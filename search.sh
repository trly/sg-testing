#!/bin/bash
# Search for a dotted property key:value across repos
# Usage: ./search.sh "management.endpoint.health.enabled:true" [file_filter]
# RE2 finds candidates via parent path patterns, yq validates exact path+value

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <key:value> [file_filter]" >&2
  echo "Example: $0 'management.endpoint.health.show-details:ALWAYS'" >&2
  echo "Example: $0 'management.endpoint.health.show-details:ALWAYS' '/resources/application([-_][a-zA-Z0-9]+)*\\.(properties|yml|yaml)\$'" >&2
  exit 1
fi

# Parse input
input="$1"
file_filter="${2:-}"
key="${input%%:*}"
value="${input#*:}"

# Validate input
if [[ -z "$key" || -z "$value" ]]; then
  echo "Error: must be key:value with non-empty key and value" >&2
  exit 1
fi

# Split key into segments
IFS='.' read -ra segments <<< "$key"
num_segments=${#segments[@]}

if [[ $num_segments -lt 2 ]]; then
  echo "Error: key must have at least two segments (parent.key)" >&2
  exit 1
fi

# Validate no empty segments
for seg in "${segments[@]}"; do
  if [[ -z "$seg" ]]; then
    echo "Error: key contains empty segment (e.g., '..')" >&2
    exit 1
  fi
done

# Escape for RE2: metacharacters and forward slash
escape_re2() {
  printf '%s' "$1" | sed -e 's/[\/\\.^$|()[\]{}*+?-]/\\&/g'
}

# Build parent path (all segments except last)
last_index=$((num_segments - 1))
parent_segments=("${segments[@]:0:$last_index}")
parent_flat=$(IFS='.'; echo "${parent_segments[*]}")

# Build RE2 alternations for ALL nesting permutations
# For n segments, there are 2^(n-1) ways to combine dots/colons
# Each bit in 0..(2^(n-1)-1) represents: 0=dot, 1=colon at that position

full_key_escaped=$(escape_re2 "$key")
last_seg_escaped=$(escape_re2 "${segments[$last_index]}")
value_escaped=$(escape_re2 "$value")

# Generate all 2^(n-1) permutations for the full key
permutations=()
num_splits=$((num_segments - 1))
max_combo=$((1 << num_splits))  # 2^(n-1)

for ((combo = 0; combo < max_combo; combo++)); do
  pattern=""
  for ((i = 0; i < num_segments; i++)); do
    seg_escaped=$(escape_re2 "${segments[$i]}")
    pattern+="${seg_escaped}"
    if ((i < last_index)); then
      # Check bit i: 0=dot, 1=colon
      if (((combo >> i) & 1)); then
        pattern+=":\\s*"
      else
        pattern+="\\."
      fi
    fi
  done
  permutations+=("$pattern")
done

# Combine all permutations
all_patterns=""
for ((i = 0; i < ${#permutations[@]}; i++)); do
  if [[ $i -gt 0 ]]; then
    all_patterns+="|"
  fi
  all_patterns+="${permutations[$i]}"
done

re2_pattern="(${all_patterns})"

# Build file filter if provided (file: matches against full file path)
file_clause=""
if [[ -n "$file_filter" ]]; then
  file_clause="file:${file_filter}"
fi

# Full query
re2_query="context:global /${re2_pattern}/ ${file_clause}"

# yq path for validation
yq_path=".${key}"

echo "RE2:  /${re2_pattern}/" >&2
echo "yq path: ${yq_path}" >&2
echo "Expected value: ${value}" >&2
echo "Query: ${re2_query}" >&2
echo "---" >&2

# Use SRC_ACCESS_TOKEN from environment (set by envchain) or fall back to src cli config
# Use process substitution to avoid subshell exit issues with set -e
while IFS='|' read -r repo path; do
  # Fetch file content via GraphQL
  gql_query='query($repo: String!, $path: String!) { repository(name: $repo) { commit(rev: "HEAD") { file(path: $path) { content } } } }'
  gql_vars="{\"repo\": \"$repo\", \"path\": \"$path\"}"
  
  response=$(src api -query="$gql_query" -vars="$gql_vars" 2>&1) || true
  raw_content=$(echo "$response" | jq -r '.data.repository.commit.file.content // empty' 2>/dev/null) || true
  
  # Strip markdown code fences if present (GraphQL sometimes wraps content)
  content=$(echo "$raw_content" | sed '1{/^```/d;}' | sed '${/^```$/d;}')
  
  [[ -z "$content" ]] && continue

  # Handle .properties files with grep
  if [[ "$path" == *.properties ]]; then
    if echo "$content" | grep -qiE "^${key}[[:space:]]*=[[:space:]]*${value}[[:space:]]*$"; then
      echo "=== $repo:$path ==="
      echo "${SRC_ENDPOINT:-https://sourcegraph.com}/${repo}/-/blob/${path}"
      echo "$content" | grep -iE "^${key}[[:space:]]*=" | head -5
      echo ""
    fi
    continue
  fi

  # For YAML: try nested path first, then flat dotted key
  actual=$(printf '%s' "$content" | yq eval "$yq_path // \"__NULL__\"" - 2>/dev/null) || true
  if [[ "$actual" == "__NULL__" ]]; then
    actual=$(printf '%s' "$content" | yq eval ".[\"$key\"] // \"__NULL__\"" - 2>/dev/null) || true
  fi

  # Case-insensitive comparison (portable for bash 3.2)
  actual_lower=$(printf '%s' "$actual" | tr '[:upper:]' '[:lower:]')
  value_lower=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
  if [[ "$actual_lower" == "$value_lower" ]]; then
    echo "=== $repo:$path ==="
    echo "${SRC_ENDPOINT:-https://sourcegraph.com}/${repo}/-/blob/${path}"
    echo "${key}: ${actual}"
    echo ""
  fi
done < <(src search --json --stream -- "$re2_query" \
  | jq -r 'select(.type == "content") | "\(.repository)|\(.path)"' \
  | sort -u)

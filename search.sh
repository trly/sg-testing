#!/usr/bin/env bash

# Search for a dotted property key:value across repos
# Usage: ./search.sh "management.endpoint.health.enabled:true" [file_filter]
# RE2 finds candidates via parent path patterns, yq validates exact path+value

set -Eeuo pipefail
trap 'printf "Error at %s:%s\n" "${BASH_SOURCE[0]}" "${LINENO}" >&2' ERR

# Check dependencies
for cmd in src jq yq sed grep sort tr; do
  command -v "$cmd" >/dev/null 2>&1 || { printf 'Missing dependency: %s\n' "$cmd" >&2; exit 127; }
done

if [[ $# -lt 1 ]]; then
  printf 'Usage: %s <key:value> [file_filter]\n' "$0" >&2
  printf 'Example: %s "management.endpoint.health.show-details:ALWAYS"\n' "$0" >&2
  printf 'Example: %s "management.endpoint.health.show-details:ALWAYS" "/resources/application([-_][a-zA-Z0-9]+)*\\.(properties|yml|yaml)$"\n' "$0" >&2
  exit 1
fi

# Parse input
input="$1"
file_filter="${2:-}"
key="${input%%:*}"
value="${input#*:}"

if [[ -z "$key" || -z "$value" ]]; then
  printf 'Error: must be key:value with non-empty key and value\n' >&2
  exit 1
fi

# Split key into segments
IFS='.' read -ra segments <<< "$key"

if [[ ${#segments[@]} -lt 2 ]]; then
  printf 'Error: key must have at least two segments (parent.key)\n' >&2
  exit 1
fi

for seg in "${segments[@]}"; do
  if [[ -z "$seg" ]]; then
    printf 'Error: key contains empty segment\n' >&2
    exit 1
  fi
done

# Escape for RE2 regex
escape_re2() {
  printf '%s' "$1" | sed 's/[\/\\.^$|()[\]{}*+?-]/\\&/g'
}

# Escape for ERE (grep -E)
escape_ere() {
  printf '%s' "$1" | sed 's/[][(){}.^$*+?|\\/]/\\&/g'
}

# Build RE2 pattern: find final key:value pair, let yq validate full path
num_segments=${#segments[@]}
last_index=$((num_segments - 1))
last_seg_escaped=$(escape_re2 "${segments[$last_index]}")
value_escaped=$(escape_re2 "$value")

# Match: key[.:=]value (handles YAML colon, properties equals, or dotted flat key)
re2_pattern="${last_seg_escaped}[.:=]\\s*${value_escaped}"

# Build all 2^(n-1) yq path permutations to handle mixed nesting
# Each bit represents: 0=continue flat segment, 1=start new nested level
num_splits=$((num_segments - 1))
max_combo=$((1 << num_splits))
yq_paths=()

for ((combo = 0; combo < max_combo; combo++)); do
  path='.'
  flat_part=""
  for ((i = 0; i < num_segments; i++)); do
    seg=${segments[$i]}
    esc_seg=${seg//\\/\\\\}
    esc_seg=${esc_seg//\"/\\\"}
    
    if [[ -z "$flat_part" ]]; then
      flat_part="$esc_seg"
    else
      flat_part+=".$esc_seg"
    fi
    
    # At each split point (except last), check if we should nest
    if ((i < num_splits)); then
      if (((combo >> i) & 1)); then
        # Bit is 1: emit current flat part and start new level
        path+="[\"$flat_part\"]"
        flat_part=""
      fi
    fi
  done
  # Emit remaining flat part
  if [[ -n "$flat_part" ]]; then
    path+="[\"$flat_part\"]"
  fi
  yq_paths+=("$path")
done

# Build file filter clause
file_clause=""
if [[ -n "$file_filter" ]]; then
  file_clause="file:${file_filter}"
fi

re2_query="context:global /${re2_pattern}/ ${file_clause}"

printf 'RE2: /%s/\n' "$re2_pattern" >&2
printf 'yq paths: %s\n' "${yq_paths[*]}" >&2
printf 'Expected value: %s\n' "$value" >&2
printf 'Query: %s\n' "$re2_query" >&2
printf '%s\n' "---" >&2

# Pre-escape for properties matching
key_ere=$(escape_ere "$key")
val_ere=$(escape_ere "$value")

while IFS='|' read -r repo path; do
  gql_query='query($repo: String!, $path: String!) { repository(name: $repo) { commit(rev: "HEAD") { file(path: $path) { content } } } }'
  gql_vars="{\"repo\": \"$repo\", \"path\": \"$path\"}"

  if ! response=$(src api -query="$gql_query" -vars="$gql_vars" 2>/dev/null); then
    printf 'WARN: src api failed for %s:%s\n' "$repo" "$path" >&2
    continue
  fi

  raw_content=$(jq -r '.data.repository.commit.file.content // empty' <<< "$response") || {
    printf 'WARN: jq parse failed for %s:%s\n' "$repo" "$path" >&2
    continue
  }

  # Strip markdown code fences if present
  content=$(printf '%s' "$raw_content" | sed '1{/^```/d;}' | sed '${/^```$/d;}')

  [[ -z "$content" ]] && continue

  # Handle .properties files (skip YAML processing entirely)
  if [[ "$path" == *.properties ]]; then
    if printf '%s\n' "$content" | grep -qiE "^[[:space:]]*${key_ere}[[:space:]]*=[[:space:]]*${val_ere}[[:space:]]*\$"; then
      printf '=== %s:%s ===\n' "$repo" "$path"
      printf '%s/%s/-/blob/%s\n' "${SRC_ENDPOINT:-https://sourcegraph.com}" "$repo" "$path"
      printf '%s\n' "$content" | grep -iE "^[[:space:]]*${key_ere}[[:space:]]*=" | head -5
      printf '\n'
    fi
    continue
  fi

  # For YAML: try all yq path permutations until one matches
  actual="__NULL__"
  for yq_path in "${yq_paths[@]}"; do
    result=$(trap - ERR; printf '%s' "$content" | yq eval "$yq_path // \"__NULL__\"" - 2>/dev/null) || true
    if [[ "$result" != "__NULL__" && -n "$result" ]]; then
      actual="$result"
      break
    fi
  done

  # Case-insensitive comparison
  actual_lower=$(printf '%s' "$actual" | tr '[:upper:]' '[:lower:]')
  value_lower=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
  if [[ "$actual_lower" == "$value_lower" ]]; then
    printf '=== %s:%s ===\n' "$repo" "$path"
    printf '%s/%s/-/blob/%s\n' "${SRC_ENDPOINT:-https://sourcegraph.com}" "$repo" "$path"
    printf '%s: %s\n\n' "$key" "$actual"
  fi
done < <(src search --json --stream -- "$re2_query" \
  | jq -r 'select(.type == "content") | "\(.repository)|\(.path)"' \
  | sort -u)

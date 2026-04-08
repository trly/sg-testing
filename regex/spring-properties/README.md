# Spring H2 Console Search Fixtures

This directory contains fixture files for validating Sourcegraph queries that search
for Spring H2 console enablement in `application.properties`, `application.yaml`,
and `application.yml` files.

The fixtures intentionally include:

- positive `.properties` examples
- positive YAML examples with different indentation styles
- semi-nested YAML shapes
- negative examples
- one loose-order YAML example that demonstrates broad-regex false-positive behavior

## File Scope

Both queries below use the same Spring-style application config path convention:

```text
(^|/)resources/application(?:[-_][A-Za-z0-9]+)*\.(?:properties|ya?ml)$
```

This matches files such as:

- `application.properties`
- `application-dev.properties`
- `application.yaml`
- `application-local.yml`

## PROD Query

Use this query when you want higher precision and are willing to enumerate the YAML
shapes explicitly.

```text
context:global repo:^github\.com/trly/sg-testing$ (
  (file:(^|/)resources/application(?:[-_][A-Za-z0-9]+)*\.properties$ /(?m:^\s*spring\.h2\.console\.enabled\s*=\s*true\s*$)/)
  or
  (file:(^|/)resources/application(?:[-_][A-Za-z0-9]+)*\.ya?ml$ /spring\.h2\.console\s*:\n\s+enabled\s*:\s*true\b/)
  or
  (file:(^|/)resources/application(?:[-_][A-Za-z0-9]+)*\.ya?ml$ /spring\.h2\s*:\n\s+console\s*:\n\s+enabled\s*:\s*true\b/)
  or
  (file:(^|/)resources/application(?:[-_][A-Za-z0-9]+)*\.ya?ml$ /spring\s*:\n(?:.*\n)*\s+h2\s*:\n(?:.*\n)*\s+console\s*:\n(?:.*\n)*\s+enabled\s*:\s*true\b/)
)
```

Expected behavior on this fixture set:

- matches the two positive `.properties` fixtures
- matches the nested YAML fixture
- matches the 4-space-indented YAML fixture
- matches `spring.h2:` with nested `console:` and `enabled: true`
- matches `spring.h2.console:` with nested `enabled: true`
- does not match the disabled fixtures
- does not match the loose-order YAML false-positive fixture

Expected result count in `github.com/trly/sg-testing`: `6`

## Exploratory Query

Use this query when you want broad discovery and are willing to tolerate ordered-token
false positives.

```text
context:global repo:^github\.com/trly/sg-testing$ file:(^|/)resources/application(?:[-_][A-Za-z0-9]+)*\.(?:properties|ya?ml)$ /(?s:spring\b.*h2\b.*console\b.*enabled\b.*true\b)/
```

Expected behavior on this fixture set:

- matches the two positive `.properties` fixtures
- matches the nested YAML fixture
- matches the 4-space-indented YAML fixture
- matches `spring.h2:` with nested `console:` and `enabled: true`
- matches `spring.h2.console:` with nested `enabled: true`
- matches the loose-order YAML false-positive fixture
- does not match the disabled fixtures

Expected result count in `github.com/trly/sg-testing`: `7`

## Notes

- The exploratory query is intentionally broad. It only requires the ordered token
  sequence `spring -> h2 -> console -> enabled -> true`, potentially across
  multiple lines.
- The production query is split into separate OR branches for `.properties` and the
  supported YAML shapes. This is easier to reason about and proved more reliable in
  Sourcegraph than trying to nest multiple YAML alternatives under a single branch.
- An alternative process is to also first use the broad query, then a YAML/properties parsing library to validate and find exacting results.

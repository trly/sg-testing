# CVE-2025-55182 Vulnerable Test Application

⚠️ **WARNING: DO NOT DEPLOY THIS APPLICATION** ⚠️

This application intentionally uses vulnerable versions of Next.js and React for security scanner testing purposes only.

## Vulnerability Details

- **CVE**: CVE-2025-55182 (React2Shell)
- **CVSS Score**: 10.0 (Critical)
- **Type**: Unsafe deserialization in React Server Components
- **Impact**: Pre-authentication remote code execution

## Vulnerable Dependencies

| Package | Version | Patched Version |
|---------|---------|-----------------|
| next | 15.0.0 | ≥15.0.5, ≥15.1.9, ≥15.2.6, ≥15.3.6, ≥15.4.8, ≥15.5.7, ≥16.0.7 |
| react | 19.0.0 | ≥19.0.1, ≥19.1.2, ≥19.2.1 |
| react-dom | 19.0.0 | ≥19.0.1, ≥19.1.2, ≥19.2.1 |

## Purpose

This app exists solely to validate that security scanning tools correctly identify CVE-2025-55182 in dependency manifests.

## References

- [React Security Advisory](https://react.dev/blog/2025/12/03/critical-security-vulnerability-in-react-server-components)
- [NVD Entry](https://nvd.nist.gov/vuln/detail/CVE-2025-55182)
- [CISA KEV Catalog](https://www.cisa.gov/known-exploited-vulnerabilities-catalog?field_cve=CVE-2025-55182)

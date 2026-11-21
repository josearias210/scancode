# Security policy

The current development branch receives security fixes. To report a vulnerability, use [GitHub's private vulnerability reporting form](https://github.com/josearias210/scancode/security/advisories/new). Include the affected version and the steps needed to confirm the issue. Use fictional scan payloads and redact credentials, personal data, and device identifiers.

Keep Android signing materials, credentials, device captures, and machine-specific configuration outside Git. The [repository security workflow](.github/workflows/security.yml) checks the committed file list, scans Git history with redacted output, and runs CodeQL on the Android source.

GitHub push protection blocks supported credentials during pushes. The default branch also submits its resolved Gradle dependency graph for Dependabot. Before pushing, run the file check against your commit and scan its Git history with Gitleaks using redacted output.

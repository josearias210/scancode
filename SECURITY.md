# Security policy

The current development branch receives security fixes. To report a vulnerability, use [GitHub's private vulnerability reporting form](https://github.com/josearias210/scancode/security/advisories/new). Include the affected version and the steps needed to confirm the issue. Use fictional scan payloads and redact credentials, personal data, and device identifiers.

Keep Android signing materials, credentials, personal scans, and machine-specific configuration outside Git. Use fictional payloads in screenshots. The [repository security workflow](.github/workflows/security.yml) checks tracked paths, file types and sizes in the proposed commits, validates the Gradle Wrapper and workflow syntax, and scans committed files, changes, and commit messages with Gitleaks. Findings are redacted, and inline Gitleaks suppressions are ignored.

GitHub push protection blocks supported credentials during pushes. Repository settings require full commit SHAs for actions and maintainer approval before workflows run for external contributors. The [default branch rules](https://github.com/josearias210/scancode/rules/27799479) require pull requests, resolved review conversations, successful Android and security checks, and CodeQL results free of new high or critical security alerts. The default branch submits its resolved Gradle dependency graph for Dependabot.

Before pushing from a branch based on `main`, fetch the current base and run `python3 scripts/check-public-files.py --base "$(git rev-parse origin/main)"` in a POSIX shell. Scan your Git changes locally with Gitleaks using `--redact=100 --ignore-gitleaks-allow`. If a real credential was committed, revoke or rotate it promptly and use private reporting to coordinate cleanup.

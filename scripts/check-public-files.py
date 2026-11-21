"""Check the files in a Git revision before making it public."""

import re
import subprocess
import sys

MAX_BYTES = 2 * 1024 * 1024
ROOT_FILES = {
    ".dockerignore", ".gitattributes", ".gitignore",
    "CHANGELOG.md", "CONTRIBUTING.md", "Dockerfile", "PRIVACY.md",
    "README.md", "SECURITY.md", "build.gradle.kts", "build.ps1",
    "gradle.properties", "gradlew", "gradlew.bat", "settings.gradle.kts",
}
PROJECT_FILES = {
    ".github/dependabot.yml", "app/build.gradle.kts",
    "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
    "docs/release.md", "docs/usage.md", "docs/store/README.md", "docs/store/app-icon.png",
    "docs/store/feature-graphic.png", "docs/screenshots/history.png",
    "docs/screenshots/photo.png", "docs/screenshots/result.png",
    "docs/screenshots/scanner.png",
}
SOURCE_PATHS = re.compile(
    r"(?:\.github/workflows/[a-z0-9_-]+\.ya?ml|"
    r"app/src/(?:main|test|androidTest)/(?:java|kotlin|res)/[a-zA-Z0-9_./-]+\.(?:java|kt|xml)|"
    r"scripts/[a-z0-9_-]+\.(?:py|sh))"
)


def approved(path):
    if path == "app/src/main/AndroidManifest.xml":
        return True
    return path in ROOT_FILES or path in PROJECT_FILES or SOURCE_PATHS.fullmatch(path) is not None


def main():
    try:
        tree = subprocess.run(
            ["git", "ls-tree", "-rlz", "HEAD"],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        sys.exit("Unable to read the Git tree. Commit the intended files locally and retry.")

    blocked = 0
    for entry in tree.split(b"\0"):
        if not entry:
            continue
        fields, raw_path = entry.split(b"\t", 1)
        mode, kind, _, size = fields.split()
        path = raw_path.decode("utf-8", errors="surrogateescape")
        if mode not in (b"100644", b"100755") or kind != b"blob" or not size.isdigit():
            blocked += 1
        elif not approved(path) or int(size) > MAX_BYTES:
            blocked += 1

    if blocked:
        sys.exit(
            f"Publication check blocked {blocked} unexpected file(s). "
            "Review the tracked paths locally with git ls-tree -r HEAD."
        )
    print("Publication check passed.")


if __name__ == "__main__":
    main()

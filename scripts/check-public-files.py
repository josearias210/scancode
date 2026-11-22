"""Check the tracked files in HEAD and any commits introduced since a base."""

import argparse
import re
import struct
import subprocess
import sys
import zlib

MAX_BYTES = 2 * 1024 * 1024
ROOT_FILES = {
    ".dockerignore", ".gitattributes", ".gitignore",
    "CHANGELOG.md", "CONTRIBUTING.md", "Dockerfile", "PRIVACY.md",
    "README.md", "SECURITY.md", "build.gradle.kts", "build.ps1",
    "gradle.properties", "gradlew", "gradlew.bat", "settings.gradle.kts",
}
PROJECT_FILES = {
    ".github/actionlint.yaml", ".github/dependabot.yml", "app/build.gradle.kts",
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
WRAPPER_JAR = "gradle/wrapper/gradle-wrapper.jar"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PNG_CHUNKS = {b"IHDR", b"PLTE", b"IDAT", b"IEND", b"tRNS", b"gAMA", b"cHRM", b"sRGB", b"pHYs"}


def approved(path):
    if path == "app/src/main/AndroidManifest.xml":
        return True
    return path in ROOT_FILES or path in PROJECT_FILES or SOURCE_PATHS.fullmatch(path) is not None


def git(*args):
    return subprocess.run(
        ["git", *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    ).stdout


def approved_png(data):
    if not data.startswith(PNG_SIGNATURE):
        return False

    offset = len(PNG_SIGNATURE)
    first_chunk = True
    has_image_data = False
    while offset + 12 <= len(data):
        length, kind = struct.unpack_from(">I4s", data, offset)
        end = offset + 8 + length
        if end + 4 > len(data) or kind not in PNG_CHUNKS:
            return False
        if first_chunk != (kind == b"IHDR") or (first_chunk and length != 13):
            return False
        checksum, = struct.unpack_from(">I", data, end)
        if zlib.crc32(data[offset + 4:end]) != checksum:
            return False
        offset = end + 4
        first_chunk = False
        has_image_data |= kind == b"IDAT"
        if kind == b"IEND":
            return length == 0 and has_image_data and offset == len(data)
    return False


def wrapper_blob(revision):
    entry = git("ls-tree", "-z", revision, "--", WRAPPER_JAR)
    fields = entry.partition(b"\t")[0].split()
    return fields[2] if fields else None


def approved_blob(path, oid, checked, wrappers):
    if path == WRAPPER_JAR:
        # The base was previously checked, and CI validates the final wrapper.
        return oid in wrappers

    is_png = path.endswith(".png")
    key = (oid, is_png)
    if key not in checked:
        data = git("cat-file", "blob", oid.decode("ascii"))
        if is_png:
            checked[key] = approved_png(data)
        else:
            try:
                data.decode("utf-8")
                checked[key] = b"\0" not in data
            except UnicodeDecodeError:
                checked[key] = False
    return checked[key]


def blocked_files(revision, checked, wrappers):
    blocked = 0
    tree = git("ls-tree", "-rlz", revision)
    for entry in tree.split(b"\0"):
        if not entry:
            continue
        fields, raw_path = entry.split(b"\t", 1)
        mode, kind, oid, size = fields.split()
        path = raw_path.decode("utf-8", errors="surrogateescape")
        if mode not in (b"100644", b"100755") or kind != b"blob" or not size.isdigit():
            blocked += 1
        elif not approved(path) or int(size) > MAX_BYTES or not approved_blob(path, oid, checked, wrappers):
            blocked += 1
    return blocked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="full base commit SHA")
    args = parser.parse_args()
    if args.base and not re.fullmatch(r"[a-f0-9]{40}", args.base):
        sys.exit("Publication check requires a full Git commit SHA as its base.")

    try:
        head = git("rev-parse", "HEAD").decode("ascii").strip()
        revisions = [head]
        wrappers = {wrapper_blob(head)}
        if args.base and args.base != "0" * 40:
            wrappers.add(wrapper_blob(args.base))
            revisions.extend(git("rev-list", f"{args.base}..{head}").decode("ascii").splitlines())
        revisions = list(dict.fromkeys(revisions))
        checked = {}
        blocked = sum(blocked_files(revision, checked, wrappers) for revision in revisions)
    except (OSError, subprocess.CalledProcessError):
        sys.exit("Unable to read the Git history. Fetch the base commit and retry.")

    if blocked:
        sys.exit(
            f"Publication check blocked {blocked} file occurrence(s) across {len(revisions)} commit(s). "
            "Inspect the affected commits locally."
        )
    print(f"Publication check passed for {len(revisions)} commit(s).")


if __name__ == "__main__":
    main()

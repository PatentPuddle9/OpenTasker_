#!/usr/bin/env python3
"""Reformat immutable OpenTasker version releases without changing update data."""

import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPOSITORY = os.environ.get("GITHUB_REPOSITORY", "PatentPuddle9/OpenTasker_")
VERSION_TAG = re.compile(r"^(?:v|beta-v)\d+\.\d+\.\d+$")
DETAILS_OPEN = "<details>\n<summary>Build details and version-picker compatibility data</summary>"


def run(*args: str) -> str:
    return subprocess.check_output(args, text=True).strip()


def one_line(body: str, pattern: str) -> str | None:
    match = re.search(pattern, body, flags=re.MULTILINE)
    return match.group(0) if match else None


def field(body: str, pattern: str) -> str | None:
    match = re.search(pattern, body, flags=re.MULTILINE)
    return match.group(1).strip() if match else None


def main() -> int:
    releases_json = run("gh", "api", f"repos/{REPOSITORY}/releases?per_page=100")
    releases = json.loads(releases_json)
    if not isinstance(releases, list):
        raise RuntimeError("GitHub did not return a release list.")

    updated = 0
    skipped = 0
    for release in releases:
        tag = str(release.get("tag_name") or "")
        if release.get("draft") or not VERSION_TAG.fullmatch(tag):
            skipped += 1
            continue

        original = str(release.get("body") or "")
        # Re-running the script starts from the visible changelog rather than nesting details.
        if DETAILS_OPEN in original:
            changelog = original.split("\n\n" + DETAILS_OPEN, 1)[0].strip()
        elif "\n\n---\n\n" in original:
            changelog = original.split("\n\n---\n\n", 1)[0].strip()
        else:
            changelog = original.strip()

        beta = tag.startswith("beta-")
        channel = field(original, r"^Release channel: \*\*(.*?)\*\*$") or (
            "Beta" if beta else "Experimental"
        )
        version_code = one_line(original, r"^Version code: \*\*\d+\*\*$")
        apk_line = one_line(original, r"^APK: \*\*.*?\*\*$")
        if not apk_line:
            assets = release.get("assets") or []
            if len(assets) == 1:
                apk_line = f"APK: **{assets[0].get('name', '')}**"
        update_support = one_line(original, r"^In-app updates: \*\*(?:available|not included)\*\*$")
        catalog = one_line(original, r"^Action catalog: .+$")
        build_line = one_line(original, r"^Built by \[.*$")
        install_line = one_line(original, r"^\*\*Install note:\*\*.*$")
        marker = one_line(original, r"^<!-- opentasker-build-run-id:\d+ -->$")

        title = f"OpenTasker {'Beta ' if beta else ''}v{tag.removeprefix('beta-').removeprefix('v')}"
        sections = [changelog] if changelog else []
        if install_line:
            sections.append(install_line)

        details = [
            DETAILS_OPEN,
            "",
            f"Release channel: **{channel}**",
            f"Version: **{tag.removeprefix('beta-').removeprefix('v')}**",
        ]
        for item in (version_code, apk_line, update_support, catalog, build_line, marker):
            if item:
                details.extend(["", item])
        details.extend(["", "</details>"])
        sections.append("\n".join(details))
        cleaned_body = "\n\n".join(sections).strip() + "\n"

        if release.get("name") == title and original == cleaned_body:
            skipped += 1
            continue

        with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".md", delete=False) as note_file:
            note_file.write(cleaned_body)
            note_path = note_file.name
        try:
            run(
                "gh", "release", "edit", tag,
                "--repo", REPOSITORY,
                "--title", title,
                "--notes-file", note_path,
            )
        finally:
            Path(note_path).unlink(missing_ok=True)
        updated += 1
        print(f"Cleaned {tag}")

    print(f"Finished: {updated} releases updated; {skipped} non-versioned or already-clean releases skipped.")
    print("No releases, tags, APK assets, or prerelease flags were deleted or changed.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"Release cleanup failed: {error}", file=sys.stderr)
        raise

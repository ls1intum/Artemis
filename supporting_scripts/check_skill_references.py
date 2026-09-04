#!/usr/bin/env python3
"""Verify that every repository path cited by an agent skill still exists.

Skills under skills/ describe procedures in terms of concrete files: base classes, ArchUnit tests,
runner scripts, workflows. A skill that names a file which has since moved is worse than no skill,
because it is confidently wrong and an agent will act on it. This check is what keeps that from
happening silently.

It scans every file under skills/ for backtick-quoted tokens that look like repository-relative
paths, resolves each against the repository root, and fails listing the ones that do not exist.
Code blocks are included on purpose: an example command naming a stale path is exactly the case
that does the most damage.

Usage:
    python3 supporting_scripts/check_skill_references.py [--skills-dir skills]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# A citation is a backtick-quoted token. Paths are recognised by their first segment matching a
# real top-level entry of the repository, which keeps prose like `@Transactional` or `--specs`
# out of the check without needing a list of exceptions.
BACKTICK = re.compile(r"`([^`\n]+)`")

# Trailing punctuation that belongs to the sentence rather than to the path.
TRAILING_PUNCTUATION = ".,:;)]}"


def repository_root() -> Path:
    return Path(__file__).resolve().parent.parent


def top_level_names(root: Path) -> set[str]:
    return {entry.name for entry in root.iterdir()}


def candidate_paths(text: str, known_top_level: set[str]) -> list[str]:
    """Extract the backtick-quoted tokens that look like repository-relative paths."""
    found = []
    for raw in BACKTICK.findall(text):
        token = raw.strip().rstrip(TRAILING_PUNCTUATION)
        if not token or " " in token or "/" not in token:
            continue
        # URLs, package names and Java FQNs are not repository paths.
        if token.startswith(("http://", "https://", "//")) or token.startswith("@"):
            continue
        # A template such as `changelog/<timestamp>_changelog.xml` names a shape, not a file.
        if "<" in token or ">" in token:
            continue
        first = token.split("/", 1)[0]
        if first not in known_top_level:
            continue
        found.append(token)
    return found


def path_exists(root: Path, token: str) -> bool:
    """A token exists if it resolves to a file or directory, or, when it ends in a glob, matches something."""
    if token.endswith("*"):
        pattern = token.rstrip("*").rstrip("/")
        parent = root / pattern
        if parent.is_dir():
            return any(parent.iterdir())
        # A glob like path/to/*.xml: let pathlib resolve it relative to the root.
        return any(root.glob(token))
    return (root / token).exists()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--skills-dir", default="skills", help="Directory holding the skills (default: skills)")
    args = parser.parse_args()

    root = repository_root()
    skills_dir = root / args.skills_dir

    if not skills_dir.is_dir():
        print(f"ERROR: no such directory: {skills_dir}", file=sys.stderr)
        return 1

    known_top_level = top_level_names(root)
    broken: list[tuple[Path, str]] = []
    checked = 0

    for skill_file in sorted(skills_dir.rglob("*.md")):
        text = skill_file.read_text(encoding="utf-8")
        for token in candidate_paths(text, known_top_level):
            checked += 1
            if not path_exists(root, token):
                broken.append((skill_file.relative_to(root), token))

    if broken:
        print(f"{len(broken)} broken path reference(s) in {args.skills_dir}/:\n", file=sys.stderr)
        for skill_file, token in broken:
            print(f"  {skill_file}: {token}", file=sys.stderr)
        print(
            "\nA skill must not cite a path that does not exist. Update the citation, or remove it "
            "if the thing it described is gone.",
            file=sys.stderr,
        )
        return 1

    print(f"OK: {checked} path reference(s) in {args.skills_dir}/ all resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

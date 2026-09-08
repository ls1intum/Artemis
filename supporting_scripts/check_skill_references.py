#!/usr/bin/env python3
"""Check path-shaped citations in inline code and fenced blocks under skills/.

Resolve repository-relative paths, skill-relative paths, root scripts prefixed with
`./`, and source-file paths relative to the root or src/test/playwright. Tracked
Git entries identify root paths independently of local build output.

This is a heuristic path check, not a Markdown link or content validator. It skips
slash-free names, URLs, absolute paths, package names, and `<...>` templates.
Globs must match inside the repository; unsupported glob patterns are skipped.

Usage:
    python3 supporting_scripts/check_skill_references.py [--skills-dir skills]
    python3 supporting_scripts/check_skill_references.py --self-test
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

BACKTICK = re.compile(r"`([^`\n]+)`")

FENCE = re.compile(r"^ {0,3}(?P<delimiter>`{3,}|~{3,})(?P<info>.*)$")

TRAILING_PUNCTUATION = ".,:;)]}"

SURROUNDING_NOISE = "\"'`(),;:"

GLOB_CHARACTERS = "*?["

# Recognize source paths even after their top-level directory is deleted.
FILE_SUFFIXES = (
    ".java",
    ".ts",
    ".mjs",
    ".js",
    ".py",
    ".sh",
    ".md",
    ".mdx",
    ".xml",
    ".yml",
    ".yaml",
    ".json",
    ".csv",
    ".html",
    ".scss",
)


def repository_root() -> Path:
    return Path(__file__).resolve().parent.parent


def tracked_top_level_names(root: Path) -> set[str]:
    """Use the Git index so local build output does not affect path recognition."""
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z"],
        capture_output=True,
        check=True,
    )
    entries = result.stdout.decode("utf-8").split("\0")
    return {entry.split("/", 1)[0] for entry in entries if entry}


def code_block_tokens(text: str) -> list[str]:
    """Whitespace-separated tokens from inside fenced code blocks, stripped of shell noise.

    Fence matching follows CommonMark: a block is closed only by a fence of the same character,
    at least as long as the opening one, and carrying no info string. Anything else is content.
    """
    tokens: list[str] = []
    open_fence: tuple[str, int] | None = None
    for line in text.splitlines():
        match = FENCE.match(line)
        if match:
            delimiter = match.group("delimiter")
            if open_fence is None:
                open_fence = (delimiter[0], len(delimiter))
                continue
            character, length = open_fence
            closes = delimiter[0] == character and len(delimiter) >= length
            if closes and not match.group("info").strip():
                open_fence = None
                continue
        if open_fence is None:
            continue
        for word in line.split():
            tokens.append(word.strip(SURROUNDING_NOISE).rstrip(TRAILING_PUNCTUATION))
    return tokens


def is_path_shaped(token: str) -> bool:
    if not token or " " in token or "/" not in token:
        return False
    if token.startswith(("http://", "https://", "/", "@")):
        return False
    # Template paths describe naming conventions, not existing files.
    return "<" not in token and ">" not in token


def bases_for(token: str, root: Path, skill_dir: Path, known_top_level: set[str]) -> list[Path]:
    """Return candidate bases; runners accept specs relative to src/test/playwright."""
    if token.startswith("./"):
        return [root]
    first = token.split("/", 1)[0]
    if first in known_top_level:
        return [root]
    if (skill_dir / first).exists():
        return [skill_dir]
    if token.endswith(FILE_SUFFIXES):
        return [root, root / "src" / "test" / "playwright"]
    return []


def path_exists(base: Path, token: str, root: Path) -> bool:
    """Resolve paths and globs without accepting matches outside the repository."""
    relative = token[2:] if token.startswith("./") else token
    if any(character in relative for character in GLOB_CHARACTERS):
        try:
            matches = list(base.glob(relative))
        except (ValueError, NotImplementedError):
            # Unsupported patterns cannot be validated.
            return True
        return any(root in match.resolve().parents for match in matches)
    candidate = (base / relative).resolve()
    if root != candidate and root not in candidate.parents:
        return False
    return candidate.exists()


SELF_TEST_DOCUMENT = """\
```bash
~~~
./inside-backtick-block.sh
```

~~~bash
```
./inside-tilde-block.sh
~~~

````bash
./inside-four-backtick-block.sh
````

```bash
./before-a-closing-fence.sh
```

./outside-every-block.sh
"""

SELF_TEST_EXPECTED = {
    "./inside-backtick-block.sh",
    "./inside-tilde-block.sh",
    "./inside-four-backtick-block.sh",
    "./before-a-closing-fence.sh",
}


def self_test() -> int:
    found = {token for token in code_block_tokens(SELF_TEST_DOCUMENT) if token.startswith("./")}
    missing = SELF_TEST_EXPECTED - found
    unexpected = found - SELF_TEST_EXPECTED
    for token in sorted(missing):
        print(f"FAIL: {token} should have been read from inside a fence", file=sys.stderr)
    for token in sorted(unexpected):
        print(f"FAIL: {token} is outside every fence and was read anyway", file=sys.stderr)
    if missing or unexpected:
        return 1
    print(f"OK: fence self-test passed ({len(SELF_TEST_EXPECTED)} cases).")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--skills-dir",
        default="skills",
        help="Directory holding the skills (default: skills)",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run the fence-parsing regression cases instead of scanning the skills",
    )
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = repository_root()
    skills_dir = (root / args.skills_dir).resolve()

    if not skills_dir.is_dir():
        print(f"ERROR: no such directory: {skills_dir}", file=sys.stderr)
        return 1
    if root not in skills_dir.parents and skills_dir != root:
        print(f"ERROR: --skills-dir must be inside the repository: {skills_dir}", file=sys.stderr)
        return 1

    known_top_level = tracked_top_level_names(root)
    broken: set[tuple[Path, str]] = set()
    checked: set[tuple[Path, str]] = set()

    for skill_file in sorted(skills_dir.rglob("*.md")):
        text = skill_file.read_text(encoding="utf-8")
        relative_file = skill_file.relative_to(root)
        inline = (raw.strip().rstrip(TRAILING_PUNCTUATION) for raw in BACKTICK.findall(text))
        for token in list(inline) + code_block_tokens(text):
            if not is_path_shaped(token):
                continue
            bases = bases_for(token, root, skill_file.parent, known_top_level)
            if not bases:
                continue
            checked.add((relative_file, token))
            if not any(path_exists(base, token, root) for base in bases):
                broken.add((relative_file, token))

    if broken:
        print(f"{len(broken)} broken path reference(s) in {args.skills_dir}/:\n", file=sys.stderr)
        for skill_file, token in sorted(broken):
            print(f"  {skill_file}: {token}", file=sys.stderr)
        print(
            "\nA skill must not cite a path that does not exist. Update the citation, or remove it "
            "if the thing it described is gone.",
            file=sys.stderr,
        )
        return 1

    print(f"OK: {len(checked)} distinct path reference(s) in {args.skills_dir}/ all resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Verify that every repository path cited by an agent skill still exists.

Skills under skills/ describe procedures in terms of concrete files: base classes, ArchUnit tests,
runner scripts, workflows. A skill that names a file which has since moved is worse than no skill,
because it is confidently wrong and an agent will act on it. This check is what keeps that from
happening silently.

It reads every Markdown file under skills/ and collects path-shaped tokens from two places: inline
code spans (single backticks) and the contents of fenced code blocks, where the example commands
live. A stale path in an example command is the one that does the most damage, so both are scanned.
Fences may use either delimiter, and a block is closed only by its own: `--self-test` covers the
mixed-delimiter cases, because getting that wrong stops the scan silently rather than loudly.

Four kinds of citation are resolved:

  * repository-relative, recognised by the first segment being a tracked top-level entry
    (`src/main/java/...`, `.github/workflows/ci.yml`);
  * skill-relative, resolved against the directory of the citing file (`reference/gates.md`),
    which is the citation most likely to break and the one a repo-root check cannot see;
  * repo-root scripts written with a leading `./` (`./run-e2e-tests-local-fast.sh`);
  * anything ending in a known source extension, tried against both the repository root and
    src/test/playwright (Playwright spec paths are written relative to the latter). This is what
    still reports a citation whose top-level directory has been renamed away.

Tokens containing a glob character must match at least one file rather than exist literally. Tokens
containing `<` or `>` are templates naming a shape, not a file, and are skipped. A token resolving
outside the repository, via `..`, counts as missing rather than as present.

A token needs a `/` to be considered at all. Slash-free ones are prose far more often than they are
citations: `*Test.java` is a naming rule, `ArchitectureTest.java` is a class, `SKILL.md` is a kind
of file, `ci.yml` is a workflow referred to by its basename. Checking them would report all of
those as broken. The subset that could be checked safely, a bare name that is a tracked top-level
file, is tautological: such a token is only recognised because it exists, so it can never fail.
Cite a root file with a directory-bearing path if you want it validated.

The set of known top-level entries comes from `git ls-files`, not from a directory listing, so the
result does not depend on whether the working tree happens to hold build output.

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

# A citation is an inline code span. Path-shaped ones are recognised by their first segment, which
# keeps prose such as `@Transactional` or `--specs` out of the check without a list of exceptions.
BACKTICK = re.compile(r"`([^`\n]+)`")

# Fenced blocks carry the example commands. Their content has no backticks, so they need their own
# pass. Both fence characters are accepted, and the delimiter is captured because a block is closed
# only by its own character: a `~~~` line inside a ``` block is content, not a terminator. Getting
# that wrong inverts the inside/outside state for the rest of the file, which would silently skip
# real citations and scan prose as if it were code.
FENCE = re.compile(r"^ {0,3}(?P<delimiter>`{3,}|~{3,})(?P<info>.*)$")

# Trailing punctuation that belongs to the sentence rather than to the path.
TRAILING_PUNCTUATION = ".,:;)]}"

# Shell and Markdown noise wrapped around a path inside a fenced block.
SURROUNDING_NOISE = "\"'`(),;:"

GLOB_CHARACTERS = "*?["

# Extensions that make a token a file citation even when its first segment is not a tracked
# top-level entry, which is how a reference to a renamed or deleted directory still gets reported.
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
    """The repository root, two levels up from this script."""
    return Path(__file__).resolve().parent.parent


def tracked_top_level_names(root: Path) -> set[str]:
    """Top-level entries git tracks, used to recognise a repository-relative token.

    Derived from the index rather than from `iterdir()` so that an untracked `build/` or
    `node_modules/` in a developer's working tree cannot change the outcome relative to CI.
    """
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
                # An opening fence may carry an info string, as in "```bash".
                open_fence = (delimiter[0], len(delimiter))
                continue
            character, length = open_fence
            closes = delimiter[0] == character and len(delimiter) >= length
            if closes and not match.group("info").strip():
                open_fence = None
                continue
            # A fence of the other character, or a shorter one, is ordinary content of this block.
        if open_fence is None:
            continue
        for word in line.split():
            tokens.append(word.strip(SURROUNDING_NOISE).rstrip(TRAILING_PUNCTUATION))
    return tokens


def is_path_shaped(token: str) -> bool:
    """Whether the token could be a path at all, before deciding what it is relative to."""
    if not token or " " in token or "/" not in token:
        return False
    # URLs, package names, Java FQNs, and slash commands such as `/artemis:e2e-pr-check` are not
    # repository paths. Anything starting with "/" is absolute, so it is never repository-relative.
    if token.startswith(("http://", "https://", "/", "@")):
        return False
    # A template such as `changelog/<timestamp>_changelog.xml` names a shape, not a file.
    return "<" not in token and ">" not in token


def bases_for(token: str, root: Path, skill_dir: Path, known_top_level: set[str]) -> list[Path]:
    """The bases a token may be relative to, or an empty list when it is not a citation to check.

    A token resolving under any one of them counts as present. `e2e/...` paths are the reason there
    is more than one: the runners take them relative to the Playwright directory, not to the root.
    """
    if token.startswith("./"):
        return [root]
    first = token.split("/", 1)[0]
    if first in known_top_level:
        return [root]
    # A skill's own reference files are cited relative to the skill directory.
    if (skill_dir / first).exists():
        return [skill_dir]
    # Names a file, but under a first segment git does not track: either a Playwright-relative spec
    # path, or a genuinely stale citation to a directory that has been renamed or removed.
    if token.endswith(FILE_SUFFIXES):
        return [root, root / "src" / "test" / "playwright"]
    return []


def path_exists(base: Path, token: str, root: Path) -> bool:
    """Whether the token resolves under base, matching at least one file when it is a glob.

    A token containing `..` could otherwise escape the repository and report a file outside it as
    present, so anything resolving outside `root` counts as missing.
    """
    relative = token[2:] if token.startswith("./") else token
    if any(character in relative for character in GLOB_CHARACTERS):
        try:
            matches = list(base.glob(relative))
        except (ValueError, NotImplementedError):
            # An unsupported pattern (for example a bare trailing '**') is not a broken citation.
            return True
        return any(root in match.resolve().parents for match in matches)
    candidate = (base / relative).resolve()
    if root != candidate and root not in candidate.parents:
        return False
    return candidate.exists()


# The fence state machine is the subtle part of this script: getting it wrong inverts the
# inside/outside state for the rest of a file and silently stops checking real citations. There is
# no pytest setup in this repository, so the regression cases live here and run in CI via
# --self-test rather than pulling in a test framework for one script.
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
    # A tilde line does not close a backtick block, so this stays inside and is scanned.
    "./inside-backtick-block.sh",
    # ...and a backtick line does not close a tilde block.
    "./inside-tilde-block.sh",
    # A longer fence opens and closes normally.
    "./inside-four-backtick-block.sh",
    "./before-a-closing-fence.sh",
    # "./outside-every-block.sh" is prose: absent from the expected set on purpose.
}


def self_test() -> int:
    """Exercise the fence state machine against mixed delimiters. Returns a process exit code."""
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
    """Scan the skills directory and report every cited repository path that no longer exists."""
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
    # Sets, because a path cited both in prose and in an example command is one citation, not two.
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

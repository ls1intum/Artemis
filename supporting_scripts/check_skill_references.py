#!/usr/bin/env python3
"""Check that the skills under skills/ are valid, routed and cite existing paths.

Four checks run together:

* Frontmatter follows the Agent Skills specification: a `name` matching the directory
  and a `description` within the length agents index. An invalid skill is skipped by
  the agent instead of failing loudly, so nothing else would report it.
* Every skill is linked from the documents agents and contributors route through, so a
  new skill is reachable without installation and a renamed one leaves no dead link.
* The plugin manifests parse and carry a version, which is what tells an installed copy
  that it is out of date.
* Path-shaped citations in inline code and fenced blocks resolve. Repository-relative
  paths, skill-relative paths, root scripts prefixed with `./` and source-file paths
  relative to the root or src/test/playwright are recognised. Tracked Git entries
  identify root paths, so local build output cannot change the result.

The path check is a heuristic, not a Markdown link or content validator: it skips
slash-free names, URLs, absolute paths, package names and `<...>` templates. Slash-free
names are prose more often than citations (`*Test.java` is a naming rule, `ci.yml` is a
workflow called by its basename), and the subset that could be resolved safely is
tautological, so checking them would only produce false reports.

Usage:
    python3 supporting_scripts/check_skill_references.py
    python3 supporting_scripts/check_skill_references.py --self-test
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

BACKTICK = re.compile(r"`([^`\n]+)`")

FENCE = re.compile(r"^ {0,3}(?P<delimiter>`{3,}|~{3,})(?P<info>.*)$")

FRONTMATTER_FIELD = re.compile(r"^(?P<key>[a-z][a-z-]*):[ \t]*(?P<value>.*)$")

# https://agentskills.io/specification: 1-64 lowercase alphanumeric characters and single
# hyphens, matching the directory name.
SKILL_NAME = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")

MAX_NAME_LENGTH = 64

MAX_DESCRIPTION_LENGTH = 1024

# Documents that route to a skill: AGENTS.md for agents reading the checkout, the
# developer page for people. Both link the file directly, on GitHub or by relative path.
ROUTING_DOCUMENTS = ("AGENTS.md", "documentation/docs/developer/work-with-ai.mdx")

SKILL_LINK = re.compile(r"skills/(?P<name>[A-Za-z0-9._-]+)/SKILL\.md")

# Manifests that distribute the skills as a Claude Code plugin. Nothing else parses them, and a
# broken one only surfaces when someone tries to install.
PLUGIN_MANIFEST = ".claude-plugin/plugin.json"

MARKETPLACE_MANIFEST = ".claude-plugin/marketplace.json"

SEMANTIC_VERSION = re.compile(r"^\d+\.\d+\.\d+(?:[-+].+)?$")

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


def frontmatter(text: str) -> dict[str, str] | None:
    """The frontmatter fields, or None when the block is missing or not flat.

    Only the flat `key: value` form the specification requires is read. Block scalars and
    nested maps continue across lines, so accepting them here would record `>` as the
    description rather than the text an agent indexes.
    """
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return None
    fields: dict[str, str] = {}
    for line in lines[1:]:
        if line.strip() == "---":
            return fields
        match = FRONTMATTER_FIELD.match(line)
        if not match:
            return None
        value = match.group("value").strip()
        if value[:1] in {">", "|"}:
            return None
        fields[match.group("key")] = value.strip("\"'")
    return None


def skill_errors(skills_dir: Path, root: Path) -> list[str]:
    """Report skills whose frontmatter would stop an agent from discovering them."""
    errors: list[str] = []
    for directory in sorted(entry for entry in skills_dir.iterdir() if entry.is_dir()):
        skill_file = directory / "SKILL.md"
        if not skill_file.is_file():
            errors.append(f"{directory.relative_to(root)}: has no SKILL.md")
            continue
        relative_file = skill_file.relative_to(root)
        fields = frontmatter(skill_file.read_text(encoding="utf-8"))
        if fields is None:
            errors.append(
                f"{relative_file}: frontmatter must open on the first line with --- and hold "
                "flat 'key: value' fields"
            )
            continue
        name = fields.get("name", "")
        description = fields.get("description", "")
        if not SKILL_NAME.match(name) or len(name) > MAX_NAME_LENGTH:
            errors.append(
                f"{relative_file}: name '{name}' must be 1-{MAX_NAME_LENGTH} lowercase "
                "alphanumeric characters separated by single hyphens"
            )
        elif name != directory.name:
            errors.append(
                f"{relative_file}: name '{name}' must match the directory '{directory.name}'"
            )
        if not description:
            errors.append(
                f"{relative_file}: description is required; state the task that should trigger "
                "the skill"
            )
        elif len(description) > MAX_DESCRIPTION_LENGTH:
            errors.append(
                f"{relative_file}: description is {len(description)} characters, over the "
                f"{MAX_DESCRIPTION_LENGTH} an agent indexes"
            )
    return errors


def routing_errors(skills_dir: Path, root: Path) -> list[str]:
    """Report skills missing from a routing document, and links to skills that are gone."""
    available = {
        entry.name for entry in skills_dir.iterdir() if (entry / "SKILL.md").is_file()
    }
    errors: list[str] = []
    for relative_file in ROUTING_DOCUMENTS:
        document = root / relative_file
        if not document.is_file():
            errors.append(f"{relative_file}: missing; agents route to the skills through it")
            continue
        linked = set(SKILL_LINK.findall(document.read_text(encoding="utf-8")))
        errors.extend(
            f"{relative_file}: links skills/{name}/SKILL.md, which does not exist"
            for name in sorted(linked - available)
        )
        errors.extend(
            f"{relative_file}: does not link skills/{name}/SKILL.md"
            for name in sorted(available - linked)
        )
    return errors


def plugin_errors(root: Path) -> list[str]:
    """Report plugin manifests that would break installation or hide an update.

    Claude Code compares the plugin version to decide whether an installed copy is stale, so a
    missing or unparsable version leaves users on the instructions they installed with.
    """
    errors: list[str] = []
    manifests: dict[str, object] = {}
    for relative_file in (PLUGIN_MANIFEST, MARKETPLACE_MANIFEST):
        try:
            text = (root / relative_file).read_text(encoding="utf-8")
            manifests[relative_file] = json.loads(text)
        except (OSError, json.JSONDecodeError) as error:
            errors.append(f"{relative_file}: unreadable ({error})")
    plugin = manifests.get(PLUGIN_MANIFEST)
    if not isinstance(plugin, dict):
        return errors
    for field in ("name", "description", "version"):
        if not plugin.get(field):
            errors.append(f"{PLUGIN_MANIFEST}: '{field}' is required")
    version = plugin.get("version", "")
    if version and not SEMANTIC_VERSION.match(str(version)):
        errors.append(f"{PLUGIN_MANIFEST}: version '{version}' is not a semantic version")
    return errors


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
    # A tilde line does not close a backtick block, and a backtick line does not close a
    # tilde block, so both of these stay inside their fence.
    "./inside-backtick-block.sh",
    "./inside-tilde-block.sh",
    "./inside-four-backtick-block.sh",
    "./before-a-closing-fence.sh",
    # "./outside-every-block.sh" is prose, so it is absent on purpose.
}

SELF_TEST_FRONTMATTER = [
    ("---\nname: a\ndescription: Use when: X\n---\n", {"name": "a", "description": "Use when: X"}),
    ("---\nname: 'a'\n---\n", {"name": "a"}),
    ("---\ndescription: >-\n  folded\n---\n", None),
    ("# Title\n---\nname: a\n---\n", None),
]


def self_test() -> int:
    """Regression cases for the two parsers, run in CI in place of a test framework.

    A fence-state mistake inverts the inside/outside state for the rest of a file and stops
    the path check silently. A frontmatter mistake records the wrong description just as
    quietly, because the file still looks like a valid skill.
    """
    failures = 0
    found = {token for token in code_block_tokens(SELF_TEST_DOCUMENT) if token.startswith("./")}
    for token in sorted(SELF_TEST_EXPECTED - found):
        print(f"FAIL: {token} should have been read from inside a fence", file=sys.stderr)
        failures += 1
    for token in sorted(found - SELF_TEST_EXPECTED):
        print(f"FAIL: {token} is outside every fence and was read anyway", file=sys.stderr)
        failures += 1
    for text, expected in SELF_TEST_FRONTMATTER:
        actual = frontmatter(text)
        if actual != expected:
            print(
                f"FAIL: frontmatter({text!r}) returned {actual!r}, not {expected!r}",
                file=sys.stderr,
            )
            failures += 1
    if failures:
        return 1
    cases = len(SELF_TEST_EXPECTED) + len(SELF_TEST_FRONTMATTER)
    print(f"OK: parser self-test passed ({cases} cases).")
    return 0


def path_errors(skills_dir: Path, root: Path) -> tuple[list[str], int]:
    """Report cited paths that no longer exist, with the number of citations resolved."""
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

    errors = [
        f"{skill_file}: cites '{token}', which does not exist"
        for skill_file, token in sorted(broken)
    ]
    return errors, len(checked)


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run the parser regression cases instead of checking the skills",
    )
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = repository_root()
    skills_dir = root / "skills"
    if not skills_dir.is_dir():
        print(f"ERROR: no such directory: {skills_dir}", file=sys.stderr)
        return 1

    problems = skill_errors(skills_dir, root)
    problems += routing_errors(skills_dir, root)
    problems += plugin_errors(root)
    citation_problems, checked = path_errors(skills_dir, root)
    problems += citation_problems

    if problems:
        print(f"{len(problems)} problem(s) with the agent skills:\n", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1

    skills = sum(1 for entry in skills_dir.iterdir() if (entry / "SKILL.md").is_file())
    print(f"OK: {skills} skills are valid, routed and packaged; {checked} cited paths resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

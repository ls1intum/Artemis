#!/usr/bin/env python3
"""Check that every external GitHub Action is pinned to a full commit SHA.

A version tag such as `actions/checkout@v6` is mutable: whoever controls the action repository can
move it to another commit, and every workflow picks that up on its next run without a diff. A
40-character commit SHA turns each upgrade into an explicit, reviewable change. The policy is
documented in `.github/workflows/README.md` (Action pinning policy); this script is what keeps a new
workflow or composite action from quietly breaking it.

Every `uses:` entry in `.github/workflows` and `.github/actions` must be one of:

* a local path (`./.github/actions/e2e-setup`, `./.github/workflows/ci-build.yml`),
* `owner/repo[/path]@<40 hex characters>` followed by a `# vX.Y.Z` comment (the `v` follows the upstream
  tag), the shape Renovate reads to update the SHA and the comment together (`helpers:pinGitHubActionDigests`),
  for an action of a step as well as for a reusable workflow of a job,
* `docker://image@sha256:<64 hex characters>`.

The files are parsed as YAML, so a `uses` key is found however it is written (block or flow style, quoted, spread
over several lines), and text that only looks like one (a comment, a `run:` script) is not mistaken for a
reference. The parser's node marks give the line of each reference and the original line, from which the trailing
`# vX.Y.Z` comment is read, because the parser itself drops comments. A `uses` key whose value is not a plain
string, and a file that is not valid YAML, are reported instead of skipped.

Needs PyYAML, which the GitHub-hosted Ubuntu runners and most developer machines provide (`pip install pyyaml`).

Usage:
    python3 supporting_scripts/check_action_pinning.py
    python3 supporting_scripts/check_action_pinning.py <repository root>
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover - the message is the point
    print("::error::The action pinning check needs PyYAML. Install it with `pip install pyyaml`.")
    sys.exit(2)

SEARCH_ROOTS = (".github/workflows", ".github/actions")

YAML_SUFFIXES = (".yml", ".yaml")

TRAILING_COMMENT = re.compile(r"(?:^|\s)(?P<comment>#.*)$")

PINNED_ACTION = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_./-]+)?@[0-9a-f]{40}$")

PINNED_DOCKER_IMAGE = re.compile(r"^docker://\S+@sha256:[0-9a-f]{64}$")

# `# v6`, `# v1.1.0`, `# v6.5.0`, `# 1.0.2`, `# v2.1.0-rc.1`: what Renovate recognises as the release a SHA belongs to.
# Some upstream repositories tag their releases without the `v` prefix, so it is optional.
VERSION_COMMENT = re.compile(r"^#\s*v?\d+(?:\.\d+){0,2}\S*(?:\s.*)?$")

# Stands in for a `uses` value that is not a plain string, so that it is rejected like any other bad reference.
UNREADABLE_REFERENCE = "<not a string>"


@dataclass(frozen=True)
class Violation:
    """One `uses:` entry that breaks the pinning policy."""

    path: Path
    line_number: int
    reference: str
    reason: str


def violation_reason(reference: str, comment: str) -> str | None:
    """Return why the reference breaks the pinning policy, or None if it complies."""
    if reference == UNREADABLE_REFERENCE:
        return "the value of `uses` must be a string naming the action, so that it can be checked"
    if reference.startswith("./"):
        return None
    if reference.startswith("docker://"):
        if PINNED_DOCKER_IMAGE.match(reference):
            return None
        return "a Docker image must be referenced by its sha256 digest"
    if not PINNED_ACTION.match(reference):
        return "pin the action to a full 40-character commit SHA instead of a tag or branch"
    if not VERSION_COMMENT.match(comment):
        return "add a trailing `# vX.Y.Z` comment naming the release the SHA belongs to, so Renovate can update both together"
    return None


def uses_entries(text: str) -> list[tuple[int, str, str]]:
    """Return the line, the reference and the trailing comment of every `uses` entry of a YAML document.

    The line numbers start at 1. Raises `yaml.YAMLError` if the text is not valid YAML.
    """
    lines = text.splitlines()
    entries: list[tuple[int, str, str]] = []
    seen: set[int] = set()

    def visit(node: yaml.Node) -> None:
        if id(node) in seen:  # an anchor used several times, or a cyclic alias
            return
        seen.add(id(node))
        if isinstance(node, yaml.MappingNode):
            for key, value in node.value:
                if isinstance(key, yaml.ScalarNode) and key.value == "uses":
                    entries.append(entry(value))
                visit(key)
                visit(value)
        elif isinstance(node, yaml.SequenceNode):
            for item in node.value:
                visit(item)

    def entry(value: yaml.Node) -> tuple[int, str, str]:
        line_index = value.start_mark.line
        if not isinstance(value, yaml.ScalarNode):
            return line_index + 1, UNREADABLE_REFERENCE, ""
        # The comment follows the end of the value on the line where it ends, behind a closing `}` of a flow mapping, if any.
        end_line = lines[value.end_mark.line] if value.end_mark.line < len(lines) else ""
        comment = TRAILING_COMMENT.search(end_line, value.end_mark.column)
        return line_index + 1, value.value.strip(), comment.group("comment") if comment else ""

    for document in yaml.compose_all(text):
        if document is not None:
            visit(document)
    return entries


def violations_in_text(text: str) -> list[tuple[int, str, str]]:
    """Return the line, the reference and the reason of every entry of the YAML text that breaks the policy."""
    return [(line, reference, reason) for line, reference, comment in uses_entries(text) if (reason := violation_reason(reference, comment)) is not None]


def workflow_files(root: Path) -> list[Path]:
    """Return every workflow and composite action file below the repository root, in a stable order."""
    files: list[Path] = []
    for search_root in SEARCH_ROOTS:
        base = root / search_root
        if base.is_dir():
            files.extend(path for path in base.rglob("*") if path.is_file() and path.suffix in YAML_SUFFIXES)
    return sorted(files)


def find_violations(root: Path) -> list[Violation]:
    """Collect every unpinned `uses:` entry in the workflows and composite actions below the root."""
    violations: list[Violation] = []
    for path in workflow_files(root):
        relative = path.relative_to(root)
        try:
            found = violations_in_text(path.read_text(encoding="utf-8"))
        except yaml.YAMLError as error:
            mark = getattr(error, "problem_mark", None)
            violations.append(Violation(relative, mark.line + 1 if mark else 1, "<file>", f"the file is not valid YAML, so its action references cannot be checked ({error})"))
            continue
        violations.extend(Violation(relative, line, reference, reason) for line, reference, reason in found)
    return violations


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path(__file__).resolve().parents[1]
    files = workflow_files(root)
    if not files:
        print(f"::error::No workflow or action files found below {root}. Did {' / '.join(SEARCH_ROOTS)} move?")
        return 2
    violations = find_violations(root)
    for violation in violations:
        message = f"{violation.reference}: {violation.reason}".replace("\n", " ")
        print(f"::error file={violation.path},line={violation.line_number}::{message}")
    if violations:
        print(f"{len(violations)} unpinned action reference(s). See .github/workflows/README.md (Action pinning policy).")
        return 1
    print(f"All action references in {len(files)} workflow and action files are pinned to a commit SHA.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

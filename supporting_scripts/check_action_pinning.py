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
* `docker://image@sha256:<64 hex characters>`.

The check is line based and needs no YAML parser. It finds a `uses` key at the start of a line (behind
an optional list dash), in a flow mapping (`- { uses: actions/checkout@v6 }`), and when the key is
quoted (`"uses": actions/checkout@v6`). It fails closed: a `uses` key whose reference it cannot read, for
example one on the next line, is reported instead of skipped.

Usage:
    python3 supporting_scripts/check_action_pinning.py
    python3 supporting_scripts/check_action_pinning.py <repository root>
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

SEARCH_ROOTS = (".github/workflows", ".github/actions")

YAML_SUFFIXES = (".yml", ".yaml")

# Where a `uses` key can start: at the beginning of a line behind an optional list dash, or inside a flow
# mapping after `{` or `,`. The key may be quoted. `name: uses: x` and `run: echo uses: x` do not qualify.
USES_KEY = re.compile(r"""(?P<prefix>^\s*(?:-\s+)?|[{,]\s*)["']?uses["']?\s*:""")

USES_VALUE = re.compile(r"""\s*(?P<reference>"[^"]*"|'[^']*'|[^\s#,}]+)""")

TRAILING_COMMENT = re.compile(r"(?:^|\s)(?P<comment>#.*)$")

PINNED_ACTION = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_./-]+)?@[0-9a-f]{40}$")

PINNED_DOCKER_IMAGE = re.compile(r"^docker://\S+@sha256:[0-9a-f]{64}$")

# `# v6`, `# v1.1.0`, `# v6.5.0`, `# 1.0.2`, `# v2.1.0-rc.1`: what Renovate recognises as the release a SHA belongs to.
# Some upstream repositories tag their releases without the `v` prefix, so it is optional.
VERSION_COMMENT = re.compile(r"^#\s*v?\d+(?:\.\d+){0,2}\S*(?:\s.*)?$")

# Stands in for a `uses` key whose reference the line based parser could not read.
UNREADABLE_REFERENCE = "uses"


@dataclass(frozen=True)
class Violation:
    """One `uses:` entry that breaks the pinning policy."""

    path: Path
    line_number: int
    reference: str
    reason: str


def parse_uses(line: str) -> list[tuple[str, str]]:
    """Return the unquoted reference and the trailing comment of every `uses` entry in the line.

    A `uses` key whose reference cannot be read yields the pseudo reference `uses` with an empty comment, which
    `violation_reason` rejects, so an unusual layout never slips through unnoticed.
    """
    entries: list[tuple[str, str]] = []
    for key in USES_KEY.finditer(line):
        if key.group("prefix").startswith(",") and "{" not in line[: key.start()]:
            continue  # a comma outside of a flow mapping, e.g. in the text of an echo
        value = USES_VALUE.match(line, key.end())
        if value is None:
            entries.append((UNREADABLE_REFERENCE, ""))
            continue
        comment = TRAILING_COMMENT.search(line, value.end())
        entries.append((value.group("reference").strip("'\""), comment.group("comment") if comment else ""))
    return entries


def violation_reason(reference: str, comment: str) -> str | None:
    """Return why the reference breaks the pinning policy, or None if it complies."""
    if reference == UNREADABLE_REFERENCE:
        return "write the reference on the same line as `uses:` so that it can be checked"
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


def check_line(line: str) -> str | None:
    """Return why the first violating `uses` entry of the line breaks the policy, or None if there is none."""
    return next((reason for reason in (violation_reason(*entry) for entry in parse_uses(line)) if reason is not None), None)


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
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            for reference, comment in parse_uses(line):
                reason = violation_reason(reference, comment)
                if reason is not None:
                    violations.append(Violation(path.relative_to(root), line_number, reference, reason))
    return violations


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path(__file__).resolve().parents[1]
    files = workflow_files(root)
    if not files:
        print(f"::error::No workflow or action files found below {root}. Did {' / '.join(SEARCH_ROOTS)} move?")
        return 2
    violations = find_violations(root)
    for violation in violations:
        print(f"::error file={violation.path},line={violation.line_number}::{violation.reference}: {violation.reason}")
    if violations:
        print(f"{len(violations)} unpinned action reference(s). See .github/workflows/README.md (Action pinning policy).")
        return 1
    print(f"All action references in {len(files)} workflow and action files are pinned to a commit SHA.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

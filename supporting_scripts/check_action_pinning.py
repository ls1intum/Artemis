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

The check is line based and needs no YAML parser: a `uses:` key only appears as a mapping key at the
start of a line, optionally behind a list dash.

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

USES_LINE = re.compile(r"^\s*(?:-\s+)?uses:\s*(?P<reference>[^\s#]+)\s*(?P<comment>#.*)?$")

PINNED_ACTION = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_./-]+)?@[0-9a-f]{40}$")

PINNED_DOCKER_IMAGE = re.compile(r"^docker://\S+@sha256:[0-9a-f]{64}$")

# `# v6`, `# v1.1.0`, `# v6.5.0`, `# 1.0.2`, `# v2.1.0-rc.1`: what Renovate recognises as the release a SHA belongs to.
# Some upstream repositories tag their releases without the `v` prefix, so it is optional.
VERSION_COMMENT = re.compile(r"^#\s*v?\d+(?:\.\d+){0,2}\S*(?:\s.*)?$")


@dataclass(frozen=True)
class Violation:
    """One `uses:` entry that breaks the pinning policy."""

    path: Path
    line_number: int
    reference: str
    reason: str


def parse_uses(line: str) -> tuple[str, str] | None:
    """Return the unquoted reference and the trailing comment of a `uses:` entry, or None if the line is not one."""
    match = USES_LINE.match(line)
    if match is None:
        return None
    return match.group("reference").strip("'\""), match.group("comment") or ""


def violation_reason(reference: str, comment: str) -> str | None:
    """Return why the reference breaks the pinning policy, or None if it complies."""
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
    """Return why the line violates the policy, or None if it is not a `uses:` entry or is compliant."""
    uses = parse_uses(line)
    return None if uses is None else violation_reason(*uses)


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
            uses = parse_uses(line)
            reason = None if uses is None else violation_reason(*uses)
            if uses is not None and reason is not None:
                violations.append(Violation(path.relative_to(root), line_number, uses[0], reason))
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

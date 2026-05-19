#!/usr/bin/env python3
"""Fail CI if any workflow job invokes a local composite action before checkout.

A `uses: ./.github/actions/<x>` reference resolves against `$GITHUB_WORKSPACE`; the
referenced `action.yml` is not on disk until `actions/checkout` runs in the same job.

Standard library only — no PyYAML dependency, so the script runs on any runner image
without a `pip install` step.

Usage: invoked from `.github/workflows/ci-workflows.yml`. Returns non-zero on any
violation and emits `::error file=...::` annotations for GitHub's PR view.
"""
from __future__ import annotations

import pathlib
import re
import sys

# Two-space-indented top-level job key (matches GitHub's required indentation).
JOB_HEADER = re.compile(r"^  ([a-z][a-z0-9_-]*):\s*$")
CHECKOUT_USES = re.compile(r"^\s*-?\s*uses:\s*actions/checkout@")
LOCAL_COMPOSITE_USES = re.compile(r"^\s*-?\s*uses:\s*(\./\.github/actions/[^\s#]+)")


def scan(path: pathlib.Path) -> list[str]:
    job = None
    seen_checkout = False
    errors: list[str] = []
    for line in path.read_text().splitlines():
        m = JOB_HEADER.match(line)
        if m:
            job = m.group(1)
            seen_checkout = False
            continue
        if CHECKOUT_USES.match(line):
            seen_checkout = True
            continue
        m = LOCAL_COMPOSITE_USES.match(line)
        if m and not seen_checkout:
            errors.append(
                f"::error file={path}::Job '{job}' uses local composite '{m.group(1)}' "
                f"before actions/checkout — the action.yml is not on disk yet"
            )
    return errors


def main() -> int:
    workflows = sorted(pathlib.Path(".github/workflows").glob("*.yml"))
    all_errors = [err for wf in workflows for err in scan(wf)]
    for err in all_errors:
        print(err)
    return 1 if all_errors else 0


if __name__ == "__main__":
    sys.exit(main())

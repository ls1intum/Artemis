#!/usr/bin/env python3
"""Fail when a tracked file says "frontend" or "backend".

Both words are too vague to say which component is meant, they flip meaning depending on who is
speaking, and they hide the boundary that actually matters. Artemis has an Angular client, a
clustered Spring Boot server, an embedded git server, a build agent fleet and several external
services, none of which fit a two-layer picture. Name the component instead: the client, the
server, the grading service, the distributed data provider, the embedding service, the database.

The full rationale, the mapping table and the exceptions are in
documentation/docs/developer/guidelines/terminology.mdx.

Two patterns are needed, because one regex cannot express both without also matching innocent text:

  * WORD_INITIAL is case-insensitive and anchored with \\b, so "frontend", "Front-End", "back end"
    and "BACKEND" are caught while the "back end" inside "feedback endpoint" is not. It has no
    trailing \\b, so a coined identifier such as `backendUrl` is still caught.
  * CAMEL_CASE reaches the form the first cannot: a capitalised Frontend/Backend glued to the end
    of a lowercase word, as in `prewarmBackend` or `getBackEndUrl`. It stays case-sensitive on the
    first letter so that `getFeedbackEndpoint` does not trip it.

Allowlisted third-party identifiers are cut out of the line before it is matched, not used to drop
the line. Dropping the line would let a new prohibited name ride along on the same line as, say,
`frontendUrl` and pass a required check. They are cut out only as whole tokens, so `backendRefsList`
is still reported. Each occurrence is replaced by a NUL, which is a non-word character, so removing
it can never splice two fragments into a new match.

Known limitation: an all-lowercase `frontend`/`backend` glued to the end of a preceding word, as in
`myfrontendUrl`, is not detected. Catching it means dropping the leading \\b, which would then flag
`callbackEndpoint`, `rollbackEndpoint`, `playback ended` and the "feedback endpoint" this repository
actually contains. A required check that cries wolf gets disabled, so the boundary stays.

Usage:
    python3 supporting_scripts/check_terminology.py
    python3 supporting_scripts/check_terminology.py --self-test
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

WORD_INITIAL = re.compile(r"\b(front|back)[-_ ]?end", re.IGNORECASE)
CAMEL_CASE = re.compile(r"[a-z](Front|Back)[Ee]nd")

# Third-party identifiers. Each is a name somebody else chose, so renaming it would break the code.
# Add an entry only for a name you did not choose, and say who owns it.
ALLOWED = (
    "frontendUrl",  # Keycloak realm configuration key
    "backendRefs",  # Kubernetes Gateway API route specification field
    "HttpXhrBackend",  # Angular, @angular/common/http
    "frontend_server_client",  # Dart package, in the Dart exercise template lockfile
    "com.docker.backend",  # macOS process name
)

# An allowlisted name is exempt only as a whole token. Without the boundary lookarounds a coined
# name that merely contains one, such as `myfrontendUrl` or `backendRefsList`, would be excised
# along with it and pass a required check. `$` joins the word characters because it is an
# identifier character in JavaScript. Longest first, so an entry cannot be masked by a shorter one
# that happens to be its prefix.
ALLOWED_PATTERN = re.compile(
    r"(?<![\w$])(?:" + "|".join(re.escape(name) for name in sorted(ALLOWED, key=len, reverse=True)) + r")(?![\w$])"
)

# A line stating the rule has to name the words it forbids. Such a line opts out with a trailing
# marker, which renders as nothing in markdown and reads as a deliberate exception in a diff:
#
#     - Never write "frontend" or "backend". <!-- terminology-check: allow -->
#
# The marker silences whatever else is on its line, so it runs on trust: review a new marker
# itself, not just the words around it. Use it only where the words are the subject.
OPT_OUT = "terminology-check: allow"

# These two necessarily contain every word they forbid.
SELF_EXCLUDED = frozenset(
    {
        "supporting_scripts/check_terminology.py",
        "documentation/docs/developer/guidelines/terminology.mdx",
    }
)

GUIDANCE = """\
Name the component instead:

  frontend  ->  the client, the web client, the user interface
  backend   ->  the server, the application server, or the service (the grading service)
  backend   ->  the provider, when a swappable distributed data implementation is meant
                (Hazelcast, Redis, Local), matching artemis.distributed-data.provider
  backend   ->  the concrete system otherwise: the embedding service, the database,
                the mail transport, the version control system

Full rationale, mapping table and exceptions:
  documentation/docs/developer/guidelines/terminology.mdx

If a hit is a third-party identifier you did not name, add it to ALLOWED in
supporting_scripts/check_terminology.py together with the owner it belongs to.
If a hit is ordinary English that happens to read "back end", reword it."""


def offending(line: str) -> bool:
    """Return whether a single line of text violates the rule, after the documented exemptions."""
    if OPT_OUT in line:
        return False
    line = ALLOWED_PATTERN.sub("\x00", line)
    return bool(WORD_INITIAL.search(line) or CAMEL_CASE.search(line))


def tracked_files(repo_root: Path) -> list[str]:
    """Return every tracked path, so the scan matches what a reviewer would see in the tree."""
    listing = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=repo_root,
        capture_output=True,
        check=True,
        text=True,
    )
    return [path for path in listing.stdout.split("\0") if path]


def scan(repo_root: Path) -> list[tuple[str, int, str]]:
    """Return every (path, line number, line) that violates the rule, in tracked-file order."""
    hits: list[tuple[str, int, str]] = []
    for path in tracked_files(repo_root):
        if path in SELF_EXCLUDED:
            continue
        try:
            text = (repo_root / path).read_text(encoding="utf-8")
        except (UnicodeDecodeError, FileNotFoundError, IsADirectoryError):
            # Binary content, or a submodule / broken symlink entry. Neither carries prose.
            continue
        for number, line in enumerate(text.splitlines(), start=1):
            if offending(line):
                hits.append((path, number, line.strip()))
    return hits


# Each case is (expected, text). The point of the negative cases is that they are the exact strings
# an over-broad pattern would swallow, so widening a regex without thinking fails here first.
SELF_TEST_CASES = (
    (True, "the backend returns 2xx"),
    (True, "the frontend consumes this DTO"),
    (True, "the Front-End team"),
    (True, "a back end service"),
    (True, "BACKEND in caps"),
    (True, "const backendUrl = 1"),
    (True, "await prewarmBackend(jwt)"),
    (True, "public String getBackEndUrl() {"),
    (True, "const FrontEndConfig = {}"),
    (True, "snake_case front_end name"),
    # The bypasses the allowlist must not open. An allowlisted name cannot shield a coined one that
    # happens to share its line, and it is exempt only as a whole token, so a coined name that
    # merely contains one is still reported.
    (True, "const frontendUrl = resolveBackendService();"),
    (True, "backendRefsList = []"),
    # The leading \b is load-bearing and these are what it buys. An all-lowercase name glued to a
    # preceding word (`myfrontendUrl`) is the price: catching it means dropping the boundary, which
    # would flag every one of these instead. This repository really does contain "feedback
    # endpoint", so the trade is not hypothetical.
    (False, "callbackEndpoint"),
    (False, "rollbackEndpoint"),
    (False, "the video playback ended"),
    (False, "the request-feedback endpoint"),
    (False, "decodes the long-feedback endpoint"),
    (False, "return getFeedbackEndpoint(id);"),
    (False, "background jobs are fine"),
    (False, "the provider is selected by a property"),
    (False, '    "frontendUrl": "http://localhost:9080"'),
    (False, "      backendRefs:"),
    (False, "HttpXhrBackend calls JSON.stringify on the body"),
    (False, "  name: frontend_server_client"),
    (False, "`com.docker.backend` arrives truncated"),
    (False, 'Never write "frontend" or "backend". <!-- terminology-check: allow -->'),
)


def self_test() -> int:
    """Classify a fixed set of strings through the real matcher. Returns a process exit code.

    Every failure mode of a scan like this is a silent pass: a regex that stops matching, or an
    exemption that grows too broad, reports a clean tree rather than an error. CI runs this first
    so a checker that has stopped working fails loudly instead of waving everything through.
    """
    failures = 0
    for expected, text in SELF_TEST_CASES:
        actual = offending(text)
        if actual != expected:
            wanted = "a hit" if expected else "clean"
            print(f"FAIL: expected {wanted}: {text}", file=sys.stderr)
            failures += 1
    if failures:
        print(f"Terminology checker self-test failed ({failures} case(s)).", file=sys.stderr)
        return 1
    print(f"OK: terminology checker self-test passed ({len(SELF_TEST_CASES)} cases).")
    return 0


def main() -> int:
    """Scan every tracked file and report each line that says "frontend" or "backend"."""
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="check the matcher against a fixed set of strings instead of scanning the repository",
    )
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    repo_root = Path(
        subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True,
            check=True,
            text=True,
        ).stdout.strip()
    )

    hits = scan(repo_root)
    if not hits:
        print("Terminology check passed: no occurrence of 'frontend' or 'backend'.")
        return 0

    print(f"Terminology check failed: {len(hits)} occurrence(s) of 'frontend' or 'backend'.\n")
    for path, number, line in hits:
        print(f"{path}:{number}: {line}")
    print()
    print(GUIDANCE)
    return 1


if __name__ == "__main__":
    sys.exit(main())

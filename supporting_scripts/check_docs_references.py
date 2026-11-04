#!/usr/bin/env python3
"""Fail when the documentation points at something that no longer exists.

Documentation drifts when the code moves and nobody notices. Two kinds of drift are cheap to detect
without false alarms, so this check detects exactly those:

  * Sidebar registration. Every page below documentation/docs/<area>/ has to appear in the sidebar of
    its area (documentation/sidebar-<area>.ts). Docusaurus renders an unregistered page, but nobody
    can navigate to it, so it silently stops being maintained.
  * Repository paths. An inline code span in the prose that names a repository path, for example
    `src/main/resources/config/application-localci.yml`, has to exist. A moved or deleted file is
    the commonest way a setup guide goes stale. Paths that the version control ignores (build
    output, local configuration) and the few entries of ALLOWLIST are exempt.

Not checked on purpose: code blocks (they hold examples, not references), paths with placeholders or
wildcards (`<module>`, `${name}`, `*`), and anything that does not start with a top-level directory
of the repository. A check that reports false alarms gets disabled, so it only reports what it can
prove.

Usage:
    python3 supporting_scripts/check_docs_references.py
    python3 supporting_scripts/check_docs_references.py --self-test
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DOCS_ROOT = REPO_ROOT / "documentation" / "docs"
AREAS = {"about": "sidebar-about.ts", "admin": "sidebar-admin.ts", "developer": "sidebar-developer.ts", "instructor": "sidebar-instructors.ts", "student": "sidebar-students.ts"}

# Top-level directories whose paths a page may reference.
REPO_DIRS = ("src", "documentation", "deployment", "supporting_scripts", ".github", "gradle", "skills", "packages", "config")
INLINE_CODE = re.compile(r"`([^`\n]+)`")
PATH = re.compile(r"^(?:%s)/[A-Za-z0-9_./-]+$" % "|".join(re.escape(d) for d in REPO_DIRS))
FENCE = re.compile(r"^\s*(```|~~~)")

# References that are intentionally not present in the repository, for example files that an
# administrator creates. Each entry needs a reason.
ALLOWLIST: dict[str, str] = {
    "src/main/resources/config/application-local.yml": "created by the developer, see the setup guide",
    "src/main/resources/config/application-artemis.local.yml": "created by the developer, see the setup guide",
    "deployment/docker/artemis/config/local-secrets.env": "an example name for a file that the reader creates and keeps untracked",
    "src/public-api.ts": "relative to packages/tum-aet-ui, which the page is about",
    "src/client.js": "a file in the exercise repository that the page describes, not in this repository",
    "config/default.exp": "a file in the exercise repository that the page describes, not in this repository",
}


def doc_pages(area: str) -> list[Path]:
    return sorted(p for p in (DOCS_ROOT / area).rglob("*") if p.suffix in (".md", ".mdx"))


def doc_ids(area: str, path: Path) -> set[str]:
    """The ids under which a page can appear in a sidebar: its path without extension, and its front matter id."""
    rel = path.relative_to(DOCS_ROOT / area).with_suffix("")
    ids = {rel.as_posix()}
    # a folder's index page is addressed by the folder name as well
    if rel.name in ("index", rel.parent.name):
        ids.add(rel.parent.as_posix())
    front = re.search(r"^---\n(.*?)\n---", path.read_text(encoding="utf-8"), re.S)
    if front:
        m = re.search(r"^id:\s*['\"]?([^'\"\n]+)['\"]?\s*$", front.group(1), re.M)
        if m:
            ids.add((rel.parent / m.group(1).strip()).as_posix().lstrip("./"))
    return ids


def unregistered_pages() -> list[str]:
    problems = []
    for area, sidebar_name in AREAS.items():
        sidebar = (REPO_ROOT / "documentation" / sidebar_name).read_text(encoding="utf-8")
        referenced = set(re.findall(r"""['"]([A-Za-z0-9_./-]+)['"]""", sidebar))
        for page in doc_pages(area):
            if not (doc_ids(area, page) & referenced):
                problems.append(f"{page.relative_to(REPO_ROOT).as_posix()}: not registered in documentation/{sidebar_name}")
    return problems


def prose_lines(text: str):
    """Yields (line number, line) for every line outside fenced code blocks."""
    fenced = False
    for number, line in enumerate(text.splitlines(), 1):
        if FENCE.match(line):
            fenced = not fenced
            continue
        if not fenced:
            yield number, line


def is_ignored(candidate: str) -> bool:
    """Build output and local configuration are ignored by the version control and are expected to be missing from a checkout."""
    try:
        return subprocess.run(["git", "check-ignore", "-q", candidate], cwd=REPO_ROOT, check=False).returncode == 0
    except OSError:
        return False


def missing_paths(page: Path, text: str) -> list[str]:
    problems = []
    for number, line in prose_lines(text):
        for span in INLINE_CODE.findall(line):
            candidate = span.strip().rstrip(".,:;")
            if not PATH.match(candidate) or candidate in ALLOWLIST:
                continue
            if not (REPO_ROOT / candidate).exists() and not is_ignored(candidate):
                problems.append(f"{page.relative_to(REPO_ROOT).as_posix()}:{number}: `{candidate}` does not exist")
    return problems


def check() -> list[str]:
    problems = unregistered_pages()
    for area in AREAS:
        for page in doc_pages(area):
            problems.extend(missing_paths(page, page.read_text(encoding="utf-8")))
    return problems


def self_test() -> None:
    """Proves that the matchers still match, because a scan that stopped matching reports a clean tree."""
    page = Path("documentation/docs/admin/x.mdx")
    text = "See `src/main/java/DoesNotExistAnywhere.java` and `build.gradle`.\n```\n`src/also/missing.txt`\n```\n`src/main/<module>/x` `src/*/y`\n"
    found = missing_paths(REPO_ROOT / page, text)
    assert len(found) == 1 and "DoesNotExistAnywhere" in found[0], found
    assert not missing_paths(REPO_ROOT / page, "`README.md` `documentation/docs/admin/telemetry.mdx`"), "existing paths must pass"
    assert PATH.match("src/main/resources/config/application.yml") and not PATH.match("src/main/${x}/y")
    print("Self-test passed.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    problems = check()
    for problem in problems:
        print(problem)
    if problems:
        print(f"\n{len(problems)} documentation reference(s) point at something that does not exist.")
        return 1
    print("Documentation reference check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

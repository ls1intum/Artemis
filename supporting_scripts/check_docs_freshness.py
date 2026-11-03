#!/usr/bin/env python3
"""Keep track of how recently each documentation page was confirmed to be correct.

check_docs_references.py proves that a page does not point at something that no longer exists. It cannot
prove that the text is still true. Only a person can, so this script makes that review visible:

  * Every page below documentation/docs/ carries `last_reviewed: YYYY-MM-DD` in its front matter, the day on
    which someone compared the page with the code and found it correct. `--validate` fails when the date is
    missing, malformed or in the future. Whoever changes a page and checks it against the code updates the date.
  * `--stale DAYS` lists the pages that have not been reviewed for DAYS days, oldest first, as a Markdown table.
    A scheduled workflow turns that list into an issue, so the review is a recurring task and not a one-off audit.
  * `--impact` lists the pages that mention a file or a class that the change touched, which is how a
    pull request finds out which pages it may have made wrong. A page is listed when an inline code span names a
    changed repository path or the class of a changed Java file. It is advice, so it never fails.
  * `--unreviewed` lists the pages that the change touched without updating `last_reviewed`, because whoever
    edits a page has compared it with the code. It is advice as well.

The change is the difference between `--base` and HEAD, or the file names in the file of `--files-from`, which is
how a workflow with a shallow checkout passes the list of the files of a pull request.

Usage:
    python3 supporting_scripts/check_docs_freshness.py --validate
    python3 supporting_scripts/check_docs_freshness.py --stale 365
    python3 supporting_scripts/check_docs_freshness.py --impact --base origin/develop
    python3 supporting_scripts/check_docs_freshness.py --self-test
"""

from __future__ import annotations

import argparse
import datetime
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DOCS_ROOT = REPO_ROOT / "documentation" / "docs"
FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.S)
LAST_REVIEWED = re.compile(r"^last_reviewed:\s*['\"]?([^'\"\n]*?)['\"]?\s*$", re.M)
INLINE_CODE = re.compile(r"`([^`\n]+)`")


def pages() -> list[Path]:
    return sorted(p for p in DOCS_ROOT.rglob("*") if p.suffix in (".md", ".mdx"))


def rel(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def reviewed_on(text: str) -> tuple[datetime.date | None, str | None]:
    """The review date of a page and, when there is none, the reason."""
    front = FRONT_MATTER.match(text)
    if not front:
        return None, "has no front matter"
    m = LAST_REVIEWED.search(front.group(1))
    if not m:
        return None, "has no last_reviewed in its front matter"
    try:
        return datetime.date.fromisoformat(m.group(1)), None
    except ValueError:
        return None, f"has last_reviewed '{m.group(1)}', which is not a date of the form YYYY-MM-DD"


def validate(today: datetime.date) -> list[str]:
    problems = []
    for page in pages():
        date, reason = reviewed_on(page.read_text(encoding="utf-8"))
        if reason:
            problems.append(f"{rel(page)}: {reason}")
        elif date > today:
            problems.append(f"{rel(page)}: last_reviewed {date} is in the future")
    return problems


def stale(days: int, today: datetime.date) -> list[tuple[datetime.date, Path]]:
    result = []
    for page in pages():
        date, _ = reviewed_on(page.read_text(encoding="utf-8"))
        if date is None or (today - date).days >= days:
            result.append((date or datetime.date.min, page))
    return sorted(result)


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=REPO_ROOT, check=True, capture_output=True, text=True).stdout


def changed_files(base: str, files_from: str | None) -> list[str]:
    if files_from:
        return [line.strip() for line in Path(files_from).read_text(encoding="utf-8").splitlines() if line.strip()]
    return [line for line in git("diff", "--name-only", f"{base}...HEAD").splitlines() if line]


def references_of(text: str) -> set[str]:
    """Everything an inline code span of the page names: paths and bare identifiers."""
    spans = set()
    fenced = False
    for line in text.splitlines():
        if re.match(r"^\s*(```|~~~)", line):
            fenced = not fenced
            continue
        if not fenced:
            for span in INLINE_CODE.findall(line):
                spans.add(span.strip().rstrip(".,:;"))
                spans.add(re.split(r"[#.(]", span.strip())[0])  # `Class.method()` names the class
    return spans


def impact(changed: list[str]) -> dict[Path, list[str]]:
    """Maps every page to the changed files that it mentions."""
    names = {}
    for path in changed:
        names.setdefault(path, path)
        if path.endswith(".java"):
            names.setdefault(Path(path).stem, path)
    result: dict[Path, list[str]] = {}
    for page in pages():
        if rel(page) in changed:
            continue  # the change already touches the page
        spans = references_of(page.read_text(encoding="utf-8"))
        hits = sorted({source for name, source in names.items() if name in spans})
        if hits:
            result[page] = hits
    return result


def unreviewed(base: str, changed: list[str]) -> list[str]:
    """Pages that the change touched without updating last_reviewed."""
    result = []
    for path in changed:
        page = REPO_ROOT / path
        if not (path.startswith("documentation/docs/") and page.suffix in (".md", ".mdx") and page.exists()):
            continue
        before = subprocess.run(["git", "show", f"{base}:{path}"], cwd=REPO_ROOT, capture_output=True, text=True)
        if before.returncode != 0:
            continue  # a new page has to carry a date anyway, and --validate checks it
        old, _ = reviewed_on(before.stdout)
        new, _ = reviewed_on(page.read_text(encoding="utf-8"))
        if new is not None and old is not None and new <= old:
            result.append(path)
    return result


def self_test() -> None:
    """Proves that the matchers still match, because a scan that stopped matching reports a clean tree."""
    ok = "---\ntitle: x\nlast_reviewed: 2026-11-03\n---\nText\n"
    assert reviewed_on(ok) == (datetime.date(2026, 11, 3), None)
    assert reviewed_on("---\ntitle: x\n---\nText\n")[1] == "has no last_reviewed in its front matter"
    assert reviewed_on("Text only\n")[1] == "has no front matter"
    assert "not a date" in reviewed_on("---\nlast_reviewed: 03.11.2026\n---\n")[1]
    assert reviewed_on("---\nlast_reviewed: '2026-11-03'\n---\n")[0] == datetime.date(2026, 11, 3)
    spans = references_of("See `src/main/Foo.java` and `FooService.run()`.\n```\n`Hidden`\n```\n")
    assert {"src/main/Foo.java", "FooService"} <= spans and "Hidden" not in spans, spans
    print("Self-test passed.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--validate", action="store_true", help="fail when a page has no valid review date")
    parser.add_argument("--stale", type=int, metavar="DAYS", help="list the pages that were not reviewed for DAYS days")
    parser.add_argument("--impact", action="store_true", help="list the pages that mention a file that the change touches")
    parser.add_argument("--unreviewed", action="store_true", help="list the pages that the change touches without a newer review date")
    parser.add_argument("--base", default="origin/develop", help="the commit that the change is compared with")
    parser.add_argument("--files-from", metavar="FILE", help="the changed files, one per line, instead of the difference to --base")
    args = parser.parse_args()
    today = datetime.date.today()

    if args.self_test:
        self_test()
        return 0
    if args.validate:
        problems = validate(today)
        for problem in problems:
            print(problem)
        if problems:
            print(f"\n{len(problems)} page(s) have no valid last_reviewed date.")
            return 1
        print("Documentation freshness check passed.")
        return 0
    if args.stale is not None:
        found = stale(args.stale, today)
        print(f"{len(found)} page(s) were not reviewed for {args.stale} days or more.\n")
        if found:
            print("| Page | Last reviewed |\n| --- | --- |")
            for date, page in found:
                print(f"| `{rel(page)}` | {date if date != datetime.date.min else 'never'} |")
        return 0
    if args.impact:
        found = impact(changed_files(args.base, args.files_from))
        if not found:
            print("No other documentation page mentions a changed file or class.")
        else:
            print("These pages mention files or classes that this change touches. Check that they are still correct:\n")
            for page, hits in found.items():
                print(f"* `{rel(page)}` mentions {', '.join(f'`{h}`' for h in hits[:5])}{' and more' if len(hits) > 5 else ''}")
        return 0
    if args.unreviewed:
        found = unreviewed(args.base, changed_files(args.base, args.files_from))
        if not found:
            print("Every changed page carries a newer last_reviewed date.")
        for path in found:
            print(f"* `{path}` was changed without a newer `last_reviewed`. Compare it with the code and set the date.")
        return 0
    parser.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())

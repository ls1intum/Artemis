---
name: verify-artemis
description: Verify an Artemis change against the user documentation that describes the features it touches, claim by claim, reusing existing test evidence and handing the rest to browser-qa.
---

# Verify a change against its documentation

The documentation pages are the specification. `supporting_scripts/check_docs_freshness.py --select` picks the pages a change affects, the same way every time, and lists every changed path below `src/main/` that no page claims. This skill turns that selection into a verdict per documented claim. It reuses evidence that already exists and hands only the remaining claims to [browser-qa](../browser-qa/SKILL.md).

A selected page obliges you to check its claims that bear on the change. It is not context to attach, and a selection proves nothing by itself.

Read only. Never edit, commit or push in the checkout under test, and never edit a page during a run, not even its `last_reviewed`.

## Run dir

Choose a durable run directory outside the checkout, and call it `<run>`. Paths in this skill are relative to the root of a checkout of current `develop`, which may not be the checkout under test. Keep `state.md` in `<run>`, one line per finished step, so a fresh session can resume.

## 1. Pin the target

Resolve the change to one commit and a base exactly as in step 1 of [browser-qa](../browser-qa/SKILL.md#1-pin-the-target), and record the checkout, the full `<sha>` and `<merge-base>` in `state.md`. If the head moves, start again at step 2.

## 2. Select the pages

The selector reads the pages and the diff of the checkout it sits in, so it cannot take the checkout under test as an argument. Give the script of current `develop` the pages of the pinned commit and its changed paths:

```bash
git -C <checkout> diff --name-status -M <merge-base> <sha> | cut -f2- > <run>/changed.txt
mkdir -p <run>/tree/supporting_scripts
git -C <checkout> archive <sha> documentation | tar -x -C <run>/tree
cp supporting_scripts/check_docs_freshness.py <run>/tree/supporting_scripts/
python3 -I <run>/tree/supporting_scripts/check_docs_freshness.py --select --files-from <run>/changed.txt --json > <run>/selection.json
```

When the checkout under test carries the same script, `python3 supporting_scripts/check_docs_freshness.py --select --base <merge-base> --json > <run>/selection.json` run inside it gives the same result. The script never fails, so read its output rather than its exit code.

Read `pages` (each page with the changed paths that selected it and the reason: `globs`, `manifest`, `mentions` or `edited`), `unmatched` and `over_cap`.

## 3. Account for every unclaimed path

Every path in `unmatched` ends one of two ways:

- a caller trace, with `file:line` at each hop, that ends at a documented screen or workflow. The page joins the selection with reason `trace`.
- a written reason why no user can observe the change, which a reviewer can check. "Covered by unit tests" is not a reason; "generated file with no change to its exported API" can be.

Trace as browser-qa step 3 does; for a large set, use its tracers. A path left open makes the run `incomplete`.

When two traces end at the same page, propose a glob for that page: the narrowest folder or file the page's workflow calls, never a whole module. List proposed globs in the report. The format of the key is in the [documentation guideline](../../documentation/docs/developer/guidelines/documentation.mdx#frontmatter-and-the-page-title).

## 4. Stop when the selection is over the cap

When `over_cap` is true, list every candidate page with its reasons and stop. The person who asked for the run reviews the scope and names the pages to verify. Continue only with that scope, and record it. The best verdict that run can reach is `incomplete`.

## 5. Extract the claims

For each selected page, read the page at `<sha>` and list the claims a reviewer can check that bear on the changed code: a field shown, a value saved, a rule enforced, a role allowed or refused. Skip claims the change cannot affect.

Name each claim by `<page>:<line>` at the pinned commit, plus a few words to tell claims on one line apart. Never copy the page's text into the record, so the record does not become another description of the feature.

## 6. Reuse evidence first

Only two kinds of existing evidence count:

- **An E2E spec that asserts the claim and ran green on `<sha>`.** Cite the spec line that asserts it and the run report: the Playwright JUnit report of a local run or the CI job for that commit. The resolver of [e2e-pr-check](../e2e-pr-check/SKILL.md) gives a plan of specs, not evidence, and a green run on another commit does not count.
- **For a claim about what a screenshot shows:** render the manifest ids that show it from the pinned commit with `supporting_scripts/e2e/render-docs-screenshots.sh --only <ids>`, which runs `check_docs_screenshots.py --compare`. Parse the printed line `N of M compared screenshot(s) differ`. Only N = 0 with M equal to the number of ids counts. The script exits 0 even when images differ, so its exit code proves nothing.

Nothing else counts: no unit test, no code reading, no exit code, no older run.

## 7. Hand the rest to browser-qa

Every claim without evidence becomes a browser-qa case. Its source is the claim's `<page>:<line>`, as a PR's Steps for Testing are the source of its cases. Run [browser-qa](../browser-qa/SKILL.md) with these cases in its plan, the same `<sha>` and base, and `<run>/browser-qa` as its run dir. browser-qa drives the browser, records video and judges each case. This skill reads `<run>/browser-qa/results/*.json` and `<run>/browser-qa/report.md`, and never drives a browser itself.

A browser-qa `pass` with its evidence passes the claim. A `fail` that browser-qa confirms in its step 7 fails the claim. A `blocked` or untested case leaves the claim `not-checkable`.

## 8. Give the verdicts

Per claim:

- `pass`: evidence from step 6 or 7 shows the documented behavior on `<sha>`.
- `fail`: evidence shows behavior that contradicts the claim.
- `not-checkable`: no evidence could be produced. Give the reason.

Overall:

- `verified` only when every claim passed and every changed path is claimed by a page or excused.
- `failed` when any claim failed.
- `incomplete` otherwise, including every run that went over the cap.

A `fail` where the code is right and the page is wrong is a documentation finding. Report the page, the line and what the code does; do not change the page.

## 9. Write the record

Write `<run>/verification.json` with a serializer and `<run>/verification.md` from it. Both stay outside the checkout and are never committed.

```json
{
  "base": "<merge-base>",
  "head": "<sha>",
  "verdict": "verified | failed | incomplete",
  "scope_review": null,
  "paths": [
    { "path": "<changed path>", "page": "<page>", "reason": "globs | manifest | mentions | edited | trace", "via": "<glob, source, span or trace file>" },
    { "path": "<changed path>", "excuse": "<why no user can observe it>" }
  ],
  "claims": [
    { "id": "<page>:<line> <few words>", "verdict": "pass | fail | not-checkable", "evidence": "<spec:line and run report, compare line, or browser-qa results file>", "reason": null }
  ],
  "documentation_findings": [{ "claim": "<page>:<line>", "code": "<file:line>" }],
  "proposed_globs": [{ "page": "<page>", "glob": "<pattern>", "traces": ["<trace file>", "<trace file>"] }]
}
```

`scope_review` holds the scope the person named when the selection went over the cap. The Markdown file puts the overall verdict first, then failed claims, documentation findings, open paths and proposed globs, then the passed claims.

## What this skill does not do

It writes no Playwright journeys, takes no screenshots of its own and keeps no feature list. The pages describe the features, the selector picks them, and browser-qa drives the browser.

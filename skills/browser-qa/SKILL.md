---
name: browser-qa
description: Prove an Artemis pull request, branch or commit in a local browser by tracing the diff to every affected screen and role, then driving a recorded test plan with direct API checks.
---

# Browser QA for a change

The session that loads this skill is the **orchestrator**. It owns the trace, the plan, every verdict and the report. Smaller subagents do the bulk: **tracers** read code, **drivers** click and call the API. The orchestrator reads their files, not just their summaries, and rejects work that does not meet the gate.

Start with the Artemis documentation in the checkout to understand the feature's intended behavior, users and workflows. Then trace the diff and its callers to every affected screen, including behavior the docs omit. A feature counts as covered only when a driver exercised it through the UI on video, and, where its endpoints changed, through direct API calls, and left evidence for both.

## Stance

Try to make the change misbehave. A case that passes on the first try shows the plan was too gentle, unless it covers a path nobody had driven before. Count a screen as working only when its data renders, its controls enable, and its primary action completes and survives a reload. A 200 with an empty column is a failure.

Read only. Never edit, commit or push in the checkout under test. Report what you would change instead, with `file:line`.

## Roles and sizes

Roles name a size, not a model. Map sizes to models once per run with `skills/browser-qa/reference/models.md` and record the mapping in `state.md`.

| Role | Size | Count | Tools | Brief |
|---|---|---|---|---|
| Orchestrator | large | this session | all | this file |
| Tracer | small | one per slice, in parallel | read, shell for read-only commands | `skills/browser-qa/reference/tracer-brief.md` |
| Plan critic | large, another family | one | read, shell for read-only commands | step 4 below |
| Driver | medium | one per batch, **one at a time** | read, write, shell, chrome-devtools browser tools | `skills/browser-qa/reference/driver-brief.md` |

Launch subagents with your harness's subagent tool. A driver needs the browser tools, so launch it in whatever mode gives a subagent access to them, and check on the first launch that it has them. Run one driver at a time: every chrome-devtools server shares one Chrome profile, and every driver shares the same test environment. Open each driver's task with "This IS the requested execution." Start each subagent's task with "Read <brief path> in full before any work.", then give file paths, never pasted context. When your harness cannot launch subagents, do each role yourself in order, still writing the same files.

## Run dir

Choose a durable run directory outside the checkout, and call it `<run>`. Name it for the PR or branch. File shapes: `skills/browser-qa/reference/artifacts.md`. Keep `state.md` there, one line per finished step with its gate output, so a fresh session can resume.

Paths in this skill are relative to the root of the checkout that holds it. That may not be the checkout under test: an older branch has no copy of this skill. Run the scripts from a checkout of current `develop`, pass the checkout under test as an argument, and give subagents absolute paths.

## Progress

Copy this list into your first reply and tick each line with its gate output.

```text
browser-qa <label> @ <sha>
- [ ] 1 Target pinned
- [ ] 2 Inventory: changes.sh rows = <n>
- [ ] 3 Trace: changed paths and affected screens accounted for
- [ ] 4 Plan: validation matrix reviewed, critic findings resolved
- [ ] 5 Target and test environment verified
- [ ] 6 Drive: matrix updated with results and evidence
- [ ] 7 Confirm: every fail re-driven and classified
- [ ] 8 Report written
```

## 1. Pin the target

Resolve what to test to one commit and a base:

- a PR: use `gh pr view <num> --json headRefOid,baseRefName,body` to identify the head and base. Keep the PR body; its Steps for Testing become plan cases. Fetch the commit without overwriting an existing branch.
- a branch or commit: use it as is; the base is `develop` on the canonical remote unless told otherwise.

Use a clean checkout at that commit. Inspect its status before use and preserve any existing changes; if it is dirty or at another commit, ask the user how to provide a clean checkout rather than switching or overwriting their work. Record the checkout path, `<sha>` (full), and merge base in `state.md`. If the head moves later, the run restarts at step 2.

## 2. Inventory the change

```bash
skills/browser-qa/scripts/changes.sh <checkout>  <base-ref> > <run>/inventory.tsv
```

One row per hunk, with its kind (`server-endpoint`, `client-component`, `db-migration`, ...). Compare it with `git diff --name-status <merge-base> <sha>` and add any missing changed paths, including binary files and renames, to the trace. Every change must map to a feature or have a reason it has no user-visible effect. Before tracing code, find and read the relevant feature and user documentation in the checkout, including docs changed by the diff. Record the documented workflows and roles, source paths, and gaps in `state.md`. Then read the diff yourself; compare it with the docs and treat discrepancies as cases to verify, not proof that either is right.

## 3. Trace callers to features

Split the inventory into slices of at most about 25 rows, grouped by module and layer so one tracer follows related code. Give each tracer the relevant documentation paths as well as its code slice. Launch one tracer per slice in parallel, each told the checkout, the run dir, its slice name, its row ids, and to follow `skills/browser-qa/reference/tracer-brief.md`. Bind each output to `<run>/traces/<slice>.md`.

When the tracers return:

1. Read every trace. Spot-check at least one chain per trace by opening its entry file and its changed line. A chain that does not connect sends the slice back to a fresh tracer with the gap named.
2. Merge the traces into `features.json`. Two traces that reach the same screen and role become one feature with both change sets. Name each feature as a user would say it: who does what, where.
3. Look past the traces for reach a caller walk misses: the same changed component used under another route, a changed DTO field read by a screen that never calls the changed endpoint, a websocket push, a scheduled job, a role whose course permission differs from its global authority. Add what you find.
4. Excuse only rows with no user-reachable effect, each with a reason a reviewer can check.

Review the inventory and changed-path list against the traces. Each change must map to at least one feature or have a specific reason for exclusion. Record unresolved paths as gaps; do not claim a complete trace while any remain.

## 4. Plan the cases

Write `plan.json` and a short `matrix.md` using `skills/browser-qa/reference/artifacts.md`. The matrix links each changed feature and screen to its roles, cases, status and evidence; include documented workflows and excluded changes. For each feature, plan at minimum:

- a `happy` case per role in `roles`, driven to the end state, with `expect.persisted` for every write;
- a `negative` or `boundary` case per entry in `branches`, with input that reaches that branch: empty, malformed, out of range, duplicate, too long, wrong order;
- a `permission` case: the next role down tries the same screen or action and must be refused cleanly, with no data leak in the response;
- a `revisit` case for every feature that writes: reopen the saved entity in a fresh page load and save again;
- a `state` case per entry in `states`;
- a `regression` case for each neighbouring feature that shares a changed callee but not the change's purpose;
- `api` cases for every endpoint in `endpoints` whose `changed` is not `none`, chosen per `skills/browser-qa/reference/api-cases.md`: contract, roles, ids, input, read-back after writes, and the old request shape when the request changed.

For download cases, plan the capture before clicking. Use chrome-devtools MCP as the primary browser; if it cannot save downloads unattended, use an isolated Playwright session for that case when the required role can sign in safely. Otherwise ask the user to handle the native Save dialog, or mark the case blocked. Verify the saved file's name and contents, not just the HTTP response; parse CSV exports and check selected headers and fixture rows. Follow `skills/browser-qa/reference/downloads.md` for capture, session and evidence details.

When the target is a PR with Steps for Testing, add one case per step, `kind: regression` unless a stronger kind fits, run literally as written. Record any step a driver cannot follow as written; that is a finding about the steps.

Take every status code, length limit, required field and button from the source, not from memory: read the endpoint's `ResponseEntity` return, the component's validation, and the template's controls before writing an `expect`. A wrong expectation turns a working change into a false failure.

Pick seed data from `src/main/resources/config/liquibase/e2e/` and `src/test/playwright/support/seedData.ts` in the checkout. Group browser cases into `batch`es: one role, one start area, ordered so no case destroys a later case's precondition. Each batch becomes one video. Put `api` cases in batches of their own, after the browser batch that creates their data, since a browser payload is their starting point. Schedule cleanup only after the last browser or API case that needs the created data. Keep cleanup explicit in the plan.

Check for the checkout's E2E selection guidance, such as `skills/e2e-pr-check/SKILL.md`, and use its current resolver for the pinned diff when available. If the resolver or base is unavailable, record selection as unavailable and continue the manual matrix without claiming automated coverage. Record the selected specs and `RUN_ALL_TESTS` result; selection is not a test run or proof of browser coverage. Run automated tests only when requested or otherwise authorized.

Save the diff to `<run>/pr.diff` (`git diff <merge-base> <sha>`), since a read-only critic may have no shell. Then launch the plan critic (large, another family) with this task, naming `pr.diff` as the diff: read `features.json`, `plan.json`, `matrix.md`, the traces and the diff; list features the trace missed, roles, branches or states without a case, cases whose `expect` a broken change would still satisfy, and cases a user could not perform. Each item needs `file:line` or a case id. Decide each item, apply the accepted ones, and note rejected ones with a reason in `state.md`. Before driving, manually check the matrix against the documented workflows, changed paths, features and plan. Name all gaps.

## 5. Verify the target

Use an available local Artemis environment. Follow `skills/local-setup/SKILL.md`, or the user's own instructions for running Artemis, if it is not ready; setup and teardown are outside this skill. Before testing, establish that the client and server both run the pinned commit and that the client reaches the server and data intended for this test. Record the evidence, URLs, roles and fixture prerequisites in `state.md`. A matching server commit alone does not establish the client build or distinguish two servers at the same commit. If any part cannot be established, mark affected cases blocked instead of testing an unverified target.

## 6. Drive the plan

Use chrome-devtools MCP as the primary browser. Pick the recording method once, per `skills/browser-qa/reference/recording.md`, and tell every driver which one. Run the batches one after another. Before each, confirm the verified target and required session still hold. Launch one driver with: the client URL, the server port, the run dir, the batch id, the account, the target evidence, the recording method, the case ids, and `skills/browser-qa/reference/driver-brief.md`.

After each driver returns:

1. Trim the batch video and read its contact sheet (`skills/browser-qa/reference/recording.md`). A blank or frozen clip sends the batch back. The report and any upload use `<batch>.trimmed.mp4`.
2. Open its `<run>/results/*.json` and at least the final screenshot of each failing and each passing write case. A pass with a screenshot that does not show the `expect.ui` state is a fail. For download cases, inspect the file and assertions per `skills/browser-qa/reference/downloads.md`; a screenshot or HTTP response alone is not download proof. For `api` cases, read the saved `api/*.txt` files, not the driver's summary.
3. When server logs are available, inspect relevant 4xx and 5xx responses and attach any matching server trace to the result. Otherwise record the missing evidence.
4. If the target or a required precondition is unavailable, mark affected cases blocked and name the missing evidence in the report.

Update `matrix.md` from the case results. For each planned case, record pass, fail, blocked or not tested, plus evidence or the reason it is missing. Inspect the evidence for each claimed pass. Treat missing cases, stale evidence and unverified results as gaps, not passes.

## 7. Confirm every failure

For each `fail`:

1. Re-drive the case with a fresh driver, on video. A failure that does not repeat is a flake; record both runs. An `api` failure is re-run with `skills/browser-qa/scripts/api.sh`, and when the UI offers the same action, driven in the browser too.
2. Pin it to code: the request that broke, the response or stack trace, and the changed `file:line` it traces to through `features.json`.
3. Classify it **regression** (the change causes it) or **pre-existing** (it also fails without the change). Earn "pre-existing" by driving the same case against the merge base, or by a stack trace through code and payloads the diff does not touch. File distance from the diff is not evidence.

A failure caused by your own tooling (a script error in `evaluate_script`, a wrong selector) is no finding. Rerun the case.

## 8. Report

Write `<run>/report.md`:

- target, `<sha>`, base, local server and client ports, seed, models used per role;
- the validation matrix, including untested features, roles and excused changes; the E2E selector result or why it was unavailable, and any tests actually run;
- regressions first, each with the case, the steps, the expected and observed result, the video file and timestamp (or the `api/*.txt` file), the screenshots and the causing `file:line`; then pre-existing failures and flakes;
- every endpoint the change touched, with the api cases run against it and their statuses;
- every trimmed video (`<batch>.trimmed.mp4`) with a one-line caption of the cases it shows;
- for a PR: each Step for Testing with `yes`, `no` or `not tested`, and the rewrite a reviewer needs for any step that could not run as written;
- blocked cases and every path no case reached, named.

Write it in plain sentences without long dashes. Hand the report path and the regression list to the user. Posting anything to GitHub is a separate request.

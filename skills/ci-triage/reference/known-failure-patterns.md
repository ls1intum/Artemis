# Known CI failure patterns

Each entry gives the symptom, the tell that distinguishes it from a real defect, and what to do.
If a failure does not match anything here, treat it as real.

## Server Tests: "Timeout has been exceeded" with no test failures

**Symptom.** Server Tests (PostgreSQL) is red. The log ends with a Gradle timeout message. Grepping
the log for `FAILED` finds nothing, or finds only unrelated noise.

**Tell.** The failure count is zero and the job duration is at the backstop. `gradle/test.gradle`
sets a hard `timeout = Duration.ofMinutes(70)` on the test task, inside a job whose own
`timeout-minutes` is 80 (`.github/workflows/ci-test.yml`). The Gradle timeout exists so a hang
fails as a real failure rather than the opaque `cancelled` a job timeout produces. That means a run
that was merely slow, not hung, fails the same way a hang does.

**What to do.** This is a healthy run killed by the backstop, usually on a loaded runner. Re-run
the whole job. Do not go looking for the failing test, there isn't one. Duration is the reliable
signal here; grepping for failures actively misleads.

## One ArchUnit violation, two red jobs

**Symptom.** Both Server Code Style and Server Tests are red.

**Tell.** Server Code Style runs `./gradlew test -DincludeTags='ArchitectureTest' -x webapp`
(`.github/workflows/ci-quality.yml`), and the same architecture tests also run as part of the full
Server Tests suite. A single violation therefore fails both.

**What to do.** Fix the one violation. Do not treat the two red jobs as two problems. Reproduce
locally with the architecture-only command above, which takes a fraction of the full suite's time.

## No CI runs at all

**Symptom.** The pull request shows no checks. It looks exactly like GitHub dropped the event.

**Tell.** Check whether the pull request can merge before anything else:

```bash
gh pr view <pr-number> --json mergeable,mergeStateStatus
```

A `CONFLICTING` pull request starts no `pull_request` workflow run at all.

**What to do.** Resolve the conflicts. Re-running and re-dispatching will not help while the branch
conflicts. Note that a stacked pull request has a base other than develop, so check `baseRefName`
before merging develop into it, or you create exactly this state.

## Checks never appear after a push

**Symptom.** A push landed, the branch is not conflicting, and still nothing starts.

**Tell.** The `synchronize` event was dropped. Nothing in the run list corresponds to the new SHA.

**What to do.** Dispatch manually:

```bash
gh workflow run ci.yml --ref <branch>
```

Note that a `workflow_dispatch` run is not a valid event for every job. Jobs that key off the
pull-request event behave differently or fail under a manual dispatch, so read the result with that
in mind rather than treating a dispatch-only failure as a branch problem.

## A counted gate trips on an unrelated change

**Symptom.** A check complains about a count or a threshold rather than about the code that
changed.

**Tell.** Several gates in this repository count things repository-wide and compare against a
recorded limit that develop already sits at. Adding one more of the counted thing fails the gate no
matter how good the change is:

- **Large classes.** A class pushed past the size limit fails the gate. Extract a service rather
  than raising the number.
- **Bean instantiations** (`.github/workflows/ci-bean-instantiations.yml`). Adding a
  `@Configuration` or a new bean can trip it. The failure is reported from a step whose name does
  not mention counting, which makes it hard to recognise.
- **Query Quality Check** (`.github/workflows/ci-quality.yml`). A new `@EntityGraph` with more
  fetch paths than the baseline allows fails. Reuse an existing counted method where possible;
  `supporting_scripts/find_slow_queries.py` is the local check.

**What to do.** Decide deliberately whether to restructure the change or to raise the threshold,
and say which you chose and why. Raising a limit silently is how these gates stopped being useful
elsewhere.

## Server Tests flakiness

**Symptom.** Server Tests fails on a test unrelated to the change, and passes on re-run.

**Tell.** The suite is genuinely flaky under load on shared runners. Recurring shapes include
sanitizer timeouts in the local CI build path, scheduling-sensitive assertions, and occasional
database process failures.

**What to do.** A full re-run is the correct response, once. If the same test fails twice, it is
not flakiness. Do not add a retry or extend a timeout to paper over it.

## A single runner producing impossible git errors

**Symptom.** Errors such as `bad tree object` that make no sense for the branch, always on the same
runner.

**Tell.** The failure follows the runner, not the change. Re-running lands on a different runner
and passes.

**What to do.** This is a corrupted checkout cache on that runner, not a repository problem. Report
the runner so its cache can be purged, and re-run.

## E2E failures that exist on develop

**Symptom.** A Playwright spec fails and looks unrelated to the change.

**Tell.** The same spec also fails on develop. Pull request runs and develop runs do not use the
same topology, so a failure that reproduces only in one of them is expected rather than surprising.

**What to do.** Compare against develop and against other open pull requests before attributing the
failure to the branch. See `skills/e2e-pr-check/SKILL.md` for how to run a targeted local check.

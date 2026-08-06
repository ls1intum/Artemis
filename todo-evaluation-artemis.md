# Work Order: Evaluate the AI Exercise-Variant Generation

Run this in the Artemis repository, on branch `feature/exercise-variants-ai-generation`.

Goal: first make the variant-generation prompts produce output of acceptable quality and freeze them,
then measure the feature and produce a self-contained report of how often it works, what it costs, and
how good the generated exercises are.

**Keep this small.** The point is a defensible basic evaluation, not an exhaustive one. Every number
below is a number a reader can check; nothing here needs to be more elaborate than that. Where a step
looks like it is growing, cut scope rather than schedule, and record what was cut.

Work autonomously. Human involvement is limited to bringing up the instance if it will not start and to
a ten-variant spot check at the end.

---

## 1. Deliverables

Everything under `supporting_scripts/evaluation/`. The work is done when these exist:

1. `corpus/` — the two source exercises and the scripts that create them.
2. Four commits on this branch: the timing log line, the frozen prompts, the harness, the results.
3. `results/runs.jsonl` — one line per run, the raw ledger.
4. `results/raw/{run_id}.json` and `results/logs/{run_id}.log` — job detail and log slice per run.
5. `results/artifacts/{run_id}/` — the generated exercises.
6. `results/tables/*.csv` — three tables: outcomes per configuration, cost per configuration
   (wall time, phase shares, tokens), rubric scores.
7. `results/figures/*.pdf` — two figures: outcome distribution per configuration, phase breakdown.
8. `results/report.md` — the written report, specified in section 6.

Plus `results-pilot/` and `prompt-defects.md` from the prompt work, kept as evidence rather than cleaned up.

Nothing else in the repository changes.

Rough shape of the effort: building the two source exercises and getting one run through by hand is the
first chunk; the prompt pilot and iteration is the second and the least predictable; the measured runs are
about 5 hours of mostly unattended machine time for the programming half and a fraction of that for the
quiz half; analysis, rubric scoring, and the report are the last chunk. If something has to give, it is
run count and rubric sample size, in that order — never the freeze, never the honesty of the report.

---

## 2. The system under test

- Pipeline: `src/main/java/de/tum/cit/aet/artemis/hyperion/service/variants/`
- Prompts: `src/main/resources/prompts/hyperion/variants/`
- REST (`HyperionExerciseVariantResource`):
  - `POST   /api/hyperion/exercises/{exerciseId}/generate-variant` → `{ jobId }`
  - `GET    /api/hyperion/variant-jobs/{jobId}` → phase, attempt, tokens, timestamps, step outputs
  - `DELETE /api/hyperion/variant-jobs/{jobId}` → cooperative cancel
- Request body (`VariantGenerationRequestDTO`): intents are expressed by field presence —
  `targetDifficulty` (`EASY|MEDIUM|HARD`), `domainText`, `narrativeStyle`
  (`TECHNICAL|REALISTIC|CREATIVE|IMAGINATIVE`), `additionalInstructions` (left empty throughout), plus
  `placement`. At least one intent is required or the request is rejected with 400.
- Phases (`VariantJobPhase`): `ANALYZING → PLANNING → PROVISIONING → TRANSFORMING → VERIFYING →
  (REPAIRING ↔ VERIFYING)* → FINALIZING → COMPLETED`, with exits `FAILED`, `DRAFT_WITH_WARNINGS`
  (budget exhausted), `CANCELLED`.
- Budgets in `ExerciseVariantGenerationPipeline`: `MAX_VERIFY_ATTEMPTS = 5`, `MAX_PLANNING_RETRIES = 2`,
  `TOKEN_BUDGET = 500_000`. Part of the system under test. Do not change them.
- Read `ai-variants-generation-implementation-report.md` on this branch for known environment quirks
  before debugging anything.

**Setup being measured.** Inference runs on the chair's GPU cluster through Logos (gpt-oss-120b); the
Artemis instance and the local CI build agent run on a MacBook Pro M1 Max. LLM calls tolerate
concurrency, local builds do not. Every wall-clock number describes this setup, and the report says so.

**Known defect to start from.** Generated problem statements contain numbers that do not belong there.

---

## 3. Stage 0 — Prerequisites

1. Bring up Artemis on this branch with local VC and local CI, Hyperion enabled, Spring AI pointed at
   the Logos endpoint with gpt-oss-120b. Record the model identifier, endpoint, temperature, and any
   reasoning-effort setting for the report.
2. Create a **dedicated course** for the evaluation.
3. **Build the source programming exercise from scratch**: the classic strategy-pattern sorting exercise
   (`SortStrategy` with `BubbleSort` and `MergeSort`, a `Context` holding the data, a `Policy` choosing
   the strategy), Java, with:
   - template, solution, and test repositories, where the classes the student writes have no file in the
     template at all,
   - structural tests driven by a structure oracle (`test.json`) plus behaviour tests,
   - a problem statement with task markers referencing the real test names and a PlantUML diagram with
     `testsColor(...)` annotations,
   - difficulty `MEDIUM`, so both `EASY` and `HARD` requests are meaningful moves.

   Verify before using it: solution passes 100 %, template compiles and scores 0 %. An exercise that does
   not satisfy its own invariants cannot be a baseline for variants required to.
4. **Create the source quiz** (none exists yet): 8 to 12 questions, multiple choice and short answer only
   (drag-and-drop is unsupported and rejected server-side), applied questions about design patterns rather
   than definition recall, plausible distractors, several questions with more than one correct option.
   From a script, not by hand.
5. Store both sources and their creation scripts under `corpus/`.
6. **Create two exercise variant groups**, one per exercise type, and use `EXISTING_GROUP` placement for
   every run so all variants stay in one place per type. Placement is identical across all runs — a
   constant, not a variable — and the report names it. Titles will collide, so the authoritative index is
   the `run_id` → `variant_exercise_id` mapping in `runs.jsonl`.
7. Verify one full run end to end by hand, one per exercise type. If the pipeline does not complete once
   manually, the harness only produces noise.
8. **Confirm the semantic gate runs.** The consistency-check gate inside `VERIFYING` is best-effort and
   no-ops silently when the provider refuses the call (documented behaviour with LM Studio). If it also
   no-ops against Logos, a whole verification gate is missing and every reliability number describes a
   weaker pipeline than the report claims. Check the manual run's log, and record per run whether it ran.
9. **Check disk headroom.** Roughly 80 programming variants means roughly 240 git repositories plus CI
   workspaces on one laptop. Estimate from the manual run and confirm there is room; the variants cannot
   be deleted afterwards because they are the corpus for the rubric in section 5d.
10. **Pick a concurrency.** Time 1 and 3 concurrent programming jobs. Verification runs two builds per
    attempt, so the laptop is the limit, not the model. Use 3 if wall time is not visibly inflated,
    otherwise 2, and record what was measured.

---

## 4. Stage 1 — Fix and freeze the prompts

Ends with two commits: the timing log line and the frozen prompts.

### 4a. Pilot and defect list

Build the run-and-collect half of the harness first (section 5b) and reuse it here.

**Pilot output goes to `results-pilot/`, never to `results/`.** Pilot runs use prompt versions that no
longer exist by the end of this stage, and one pilot line in `runs.jsonl` silently mixes versions into a
table claiming one. Keep the pilot data: it is the evidence for the report's prompt-work section.

Run 4 programming and 6 quiz runs on the current prompts, using the same fixed domain texts the matrix
will use. Read what came out — problem statement, diff against the sources, code in all three
repositories, quiz questions, build and test outcomes — and judge it as an instructor would: **did the
generation produce what the request asked for?** If yes, that run is fine. If anything is wrong, list it
in `supporting_scripts/evaluation/prompt-defects.md` with a verbatim excerpt, the run, and the prompt
section that should have prevented it, then iterate.

Anything wrong counts, including but not limited to: stray numbers in the problem statement (the known
defect — work out whether they are copied `<testid>` numbers, invented point values, task numbering, or
hallucinated sizes, because the fix differs); a failing solution build or a template that no longer
scores 0 %; task markers referencing tests that do not exist; PlantUML in code fences or missing
`testsColor(...)`; identifiers renamed while the data model stays; renamed pattern-role names that must
stay; leftover source-domain vocabulary; quiz answers whose correctness flags contradict the question.

### 4b. Timing log line

Timings come from the server log. **No DTO change, no new job-record field, no timing from polling** —
polling only notices that a job finished.

Most is already there, verify each on the branch: terminal transitions log at INFO with the job id;
`ExerciseVariantJobService.logTelemetrySummary` already logs per-tool-call and per-build timings at the
terminal transition (`toolName=3x/12345ms`); `VariantBuildVerificationService` logs the build wait at
DEBUG — raise the level for `de.tum.cit.aet.artemis.hyperion` in the run configuration rather than
changing code.

Missing is the **phase timeline**. Non-terminal transitions go through one method in
`ExerciseVariantJobService` (the one mutating the phase, around line 192). Add **one** `log.info` there
naming the job id and the phase being entered. That plus the log timestamps yields every phase duration
by subtraction. Nothing else needs instrumenting.

Run the instance with a file appender, millisecond timestamps, and rotation that cannot discard a run
before the harness slices it. Configuration over code. Commit this separately from the prompts.

### 4c. Iterate, then freeze

- Quizzes first: a quiz run costs a minute or two, a programming run about ten, and most wording problems
  reproduce on both.
- One change per iteration, rerun the pilot subset, note the effect.
- **The prompts are the lever.** A post-processing fix looks safer than it is: a strip that repairs a
  broken output also fires on a correct one, and corrupting good output is worse than the defect. Reach
  for code only when a defect resists several prompt attempts, and only where it cannot damage a correct
  output — a check that reports is safer than a transformation that edits silently.
- **Shorten the prompts while you are in them.** They have grown long and specific, much of it written
  against individual failures of one Java exercise. Prefer the principle over the recited case. Target
  shorter prompts that generalize beyond this exercise and language, with Java generation no worse than
  before, measured on the pilot. If shortening a section makes runs fail, put it back and note what it
  was load-bearing for.
- Freeze gate, limited to mechanically checkable defects: on 5 programming and 5 quiz runs, at least 4 of
  5 programming runs reach `COMPLETED`, and no run produces a stray `<testid>` number, a dangling test
  reference, or fenced PlantUML.
- **How deep a re-theme goes is not a freeze gate.** Whether a domain change reaches the data model or
  stops at labels is a judgment call; making it pass/fail here either stalls the freeze or invites it to
  be declared met on a weak run. Work on it, then let the rubric say how well it landed. A corpus where
  some domain changes are cosmetic is a result worth reporting.

Commit the prompts with a message saying this is the final version for the evaluation, and record the SHA
in `supporting_scripts/evaluation/README.md`. From here on nothing in `prompts/hyperion/variants/` changes. A prompt defect surfacing
during the measured runs is a **finding for the report**, not a reason to edit: fixing it invalidates
every earlier run, and mixing versions in one table is worse than reporting the defect honestly.

---

## 5. Stage 2 — Measure

Location: `supporting_scripts/evaluation/`. `supporting_scripts/hyperion/consistency-check-benchmark/` is
the structural model — module layout, `config.ini`, README, virtualenv instructions. A Jupyter notebook
driving a small Python module; keep the logic in the module so a crashed kernel loses nothing. Credentials
in an untracked config file.

### 5a. Run matrix

**Two fixed domain texts, written once and reused verbatim:**

- **D-near** — a domain the source takes easily: its central data is naturally a collection of comparable
  things with an obvious ordering field (a triage queue by arrival time, parcels by deadline).
- **D-far** — a domain that resists it, where the obvious entity is not a sortable list and the model has
  to work to keep the algorithm honest (a friend graph, a chat protocol, a recommendation feed).

Write down why each was classified near or far before running. The split is a claim about the source
exercise, not a property of the domains.

| ID | `targetDifficulty` | `domainText` | `narrativeStyle` | What it isolates |
|---|---|---|---|---|
| C1 | EASY | – | – | difficulty down, nothing else |
| C2 | HARD | – | – | difficulty up, nothing else |
| C3 | – | D-near | – | domain change, easy target |
| C4 | – | D-far | – | domain change, hard target |
| C5 | – | – | TECHNICAL | narrative only, step 1 of the scale |
| C6 | – | – | REALISTIC | narrative only, step 2 |
| C7 | – | – | CREATIVE | narrative only, step 3 |
| C8 | – | – | IMAGINATIVE | narrative only, step 4 — no domain given, so this triggers the planner's documented Greek-mythology fallback |
| C9 | – | D-near | TECHNICAL | the same four steps, now with a domain to build the story on |
| C10 | – | D-near | REALISTIC | " |
| C11 | – | D-near | CREATIVE | " |
| C12 | – | D-near | IMAGINATIVE | " |
| C13 | – | D-far | IMAGINATIVE | the hard combination: resisting domain, strongest narrative |
| C14 | HARD | D-far | CREATIVE | stacked intents, the stress case |

`additionalInstructions` stays empty in every run. It is free text with no structure to vary
systematically, so a result measured on one hand-written sentence would say nothing about the field in
general. The report states that the field was not exercised rather than leaving the gap implicit.

Read as groups, not fourteen unrelated cells: C1/C2 isolate difficulty, C3/C4 isolate domain distance,
**C5–C8 walk the full narrative strength scale with no domain to build on and C9–C12 walk the same four
steps with one**, C13 combines both hard settings, and C14 stacks everything. The two narrative sweeps are
the interesting part — `TECHNICAL`, `REALISTIC`, `CREATIVE`, and `IMAGINATIVE` are one scale from plain
wording to full storytelling, so the question is not only whether each level works but whether the
difference between neighbouring steps is visible in the output at all, and whether reliability costs
anything as the story gets stronger.

Replicates: **6 per configuration, for both exercise types** — 84 programming runs (≈ 14 h serial, ≈ 5 h
at concurrency 3) and 84 quiz runs (cheap, no CI). Identical configurations and identical n on both halves
is the point: the comparison between exercise types is one of the few contrasts this design can actually
support, and it only holds if nothing but the exercise type differs. Repetition is necessary because the
model is non-deterministic. Six runs give a wide interval — 5 of 6 is 83 % with a 95 % Wilson interval of
roughly 44 % to 97 % — so report the interval, never the bare percentage.

Timing: run the bulk at the chosen concurrency, plus **2 extra serial runs per exercise type** for the
timing figures. Report the two separately; contention on one build agent inflates wall time and would make
the phase breakdown dishonest.

**6 is a target, not a cap.** Because the runs go in rounds (section 5b), the replicate count is whatever
number of rounds finished: stop after round 4 if time runs out, keep going to round 8 if there is time
left. Report the n that was actually reached, per configuration, and never quote a rate without it. If an
interruption lands mid-round, either finish that round or discard its partial results — a matrix where
some configurations have one extra run is a matrix whose comparisons need a caveat nobody will remember to
state.

If the schedule slips badly enough that whole configurations must go rather than rounds, drop C9–C12
rather than C5–C8 — one complete narrative sweep is worth more than two partial ones — and drop them from
both halves. Record whatever was cut and why.

### 5b. Execution and storage

- Authenticate once as an editor or instructor and keep the session — reuse the login handling from the
  consistency-check benchmark. Jobs are per-user scoped, so every run uses the same account.
- `POST` each run, poll `GET /api/hyperion/variant-jobs/{jobId}` every 2 seconds until terminal.
- **Run in rounds, not in blocks.** One round is one replicate of every configuration on both exercise
  types: C1…C14 programming and C1…C14 quiz. Round 2 repeats the same list, and so on. Never run all six
  replicates of C1 before starting C2.

  This is what makes the matrix interruptible. Stopping after any completed round leaves a balanced
  corpus with equal n everywhere, so the run can be cut short for time and still be reportable, and it
  can equally be extended by simply running more rounds if there is time to spare. It also spreads any
  environment drift evenly across configurations instead of concentrating it in whichever cells ran late.
  Shuffle the order within each round so no configuration always occupies the same position.
- Concurrency: a pool of the chosen size, fed from the round's queue.
- Per-run timeout of 45 minutes, then cancel and record `TIMEOUT`. **Fetch the job detail before issuing
  the cancel** — cancelling deletes the clone and clears the exercise id. Watch this limit in the first
  hour: a legitimate repair-heavy run can approach it, and a timeout firing on healthy runs understates
  reliability.
- Collect each run's detail as soon as it reaches a terminal phase. Job records have a 24-hour TTL, so a
  harness resumed days later cannot backfill them; those runs are re-run, not patched. Same if the server
  restarts mid-matrix — its in-flight jobs stay "running" forever (known open item), so discard and re-run.
- Do not delete generated variants; they are the corpus for 5d.

Storage, all append-only and resumable by `run_id` (`{exercise_type}-{config_id}-r{round}`):

- `results/runs.jsonl` — config parameters, `terminal_phase`, `failed_in_phase`, `failure_detail`,
  `attempts_used`, `total_tokens_used`, `started_at`, `finished_at`, `wall_seconds`, per-phase durations,
  `variant_exercise_id`, `warnings`, whether the semantic gate ran, concurrency, `prompt_commit_sha`.
- `results/raw/{run_id}.json` — full job detail including every step output. Do not summarize at write time.
- `results/logs/{run_id}.log` — every log line carrying this job id, grepped from the instance log.
- `results/artifacts/{run_id}/` — problem statement, the three cloned repositories or a diff against the
  sources, or the quiz JSON. Only for runs that left a variant behind; `FAILED`, `CANCELLED`, and
  `TIMEOUT` runs have none.

On start, read `runs.jsonl`, skip completed ids, append only. Resuming and extending are then the same
operation: run the notebook again with a higher round target and it picks up where it stopped.

### 5c. Numbers

**Timings from the log**, in a separate notebook cell so it can rerun over stored logs: group lines by job
id, build the phase timeline from the log timestamps, take durations by subtraction; parse the telemetry
line for per-tool-call and per-build totals and the DEBUG lines for individual build waits. Fail loudly on
an incomplete timeline rather than dropping it quietly, and sanity-check once against a watched run —
summed phase durations must match `finished_at - started_at` within a second or two.

**Three automated checks** over the artifacts, the cheap half of quality measurement:

- stray `<testid>` numbers, and task markers naming tests that do not exist in the variant,
- fraction of source files left byte-identical (the transform-not-regenerate property),
- quiz validity: at least one correct option per multiple-choice question, short-answer spots with
  solutions, question count against the source.

**Three tables**, per configuration and exercise type:

1. Outcomes — `COMPLETED` / `DRAFT_WITH_WARNINGS` / `FAILED` / `TIMEOUT` as counts out of n, plus a Wilson
   95 % interval on the completion rate, plus first-attempt pass rate and repair attempts used.
2. Cost — wall time (median and range, serial subset separate), phase shares (`PLANNING`, `PROVISIONING`,
   `TRANSFORMING`, `VERIFYING`, `REPAIRING`, `FINALIZING`), tokens per run. Say which phases are
   model-bound and which build-bound.
3. Quality — the three automated checks and the rubric scores from 5d.

**Two figures** as vector PDF: outcome distribution per configuration (stacked bar) and phase breakdown
(stacked bar). Labeled axes, one style, light background, readable at 100 % zoom.

**Failure taxonomy:** classify every non-`COMPLETED` run from `results/raw/` into planner error,
transformation error, template-invariant violation, build failure, semantic gate rejection, budget
exhaustion, or infrastructure. Report the distribution with one representative excerpt per class.

No significance tests. n is small and the corpus is one source exercise per type.

### 5d. Rubric — LLM judge

**Sample, do not score everything.** A full matrix leaves up to 168 surviving variants, and each one has
to be read against its source and its diff. Scoring all of them is the largest single cost in this work
order and it buys little: the rubric is a quality description, not a rate. Take **2 surviving variants per
configuration per exercise type, drawn at random** — 56 scorings at a full matrix — and state the sampling
rule and the counts. If a configuration has fewer than 2 survivors, score what there is and say so; that
sparseness is itself a result.

`FAILED`, `CANCELLED`, and `TIMEOUT` runs have no variant to judge, so the rubric describes surviving
variants rather than all attempts, and the report says so.

**You are the judge.** This same Claude Code session reads the artifacts and scores them — no API script,
no separate model. Work through the variants one at a time and append each score to
`results/rubric.jsonl` immediately, so the ledger survives a compacted or interrupted session and can be
resumed by checking which run ids are already scored.

- Freeze the rubric and its anchors in `rubric.md` before the first score, and score against that file
  rather than from memory of what the criteria meant.
- Score one variant at a time, **reading it from the stored artifacts**: source and variant problem
  statements, the diff, the quiz JSON. Judge what is in the files, not what you remember about how the run
  went. Supply yourself the requested intent, since intent fidelity cannot be judged without it.
- Every score carries a one-sentence justification and a verbatim excerpt from the artifact. A score
  without an excerpt is discarded and re-scored.
- **You are not a blind judge, and the report must say so.** This session ran the generations, tuned the
  prompts, and knows which runs completed, so the usual blinding is not available. Do not look up a run's
  outcome, phase, or configuration while scoring it, and do not score straight after watching that run
  finish. These reduce the anchoring; they do not remove it, and the write-up should not pretend otherwise.
- Single pass, except **a quarter of the sampled variants scored a second time in a fresh subagent** that receives only
  the rubric file and the artifact paths. A fresh context is the closest thing to an independent second
  rater available here. Report exact agreement and agreement within one point, per criterion.
- Spot-check packet at `results/spot-check/`: 5 programming and 5 quiz variants with artifacts and scores,
  blank verdict field per variant, for a human.

Four criteria, 1–5, anchors written before scoring:

- **Intent fidelity** — did the requested change happen? An EASY variant that is not easier, or a domain
  change that only renamed labels, fails regardless of green builds.
- **Preservation** — is everything the intent did not touch still the source exercise?
- **Statement quality** — coherent, tasks match tests, diagram matches code, no leftovers, no stray numbers.
- **Readiness for use** — would an instructor publish this as is, and does it still teach what the source
  taught?

Then, in prose: **what actually changed between source and variant?** One programming and one quiz
walkthrough showing source and variant side by side (statement excerpt plus the decisive diff hunk), plus
one failure walked through the same way.

---

## 6. The report

`results/report.md`, read by someone who has not seen this repository and cannot ask questions. Keep it
short and concrete:

1. **Setup** — model, endpoint, temperature, reasoning effort, branch and commit, frozen prompt SHA,
   hardware for inference and builds, run dates, concurrency used.
2. **Corpus** — both sources described concretely (built for this evaluation rather than taken from a
   course, language and build tooling, test count, structure oracle, quiz question count and kinds,
   verified starting invariants), and the placement used.
3. **Prompt work** — what the pilot found with excerpts, what changed, what needed code rather than
   prompt wording, what the freeze gate measured, what was left unfixed.
4. **Method** — the matrix, replicate counts and why, run and timing protocol, the three automated checks,
   the judge protocol: that scoring was done by the same Claude Code session that ran the generations,
   what that means for blinding and what was done to limit it, the sampling rule for which variants were
   scored, and the subset re-scored in a fresh context. Runs planned against runs executed, and anything cut or discarded with the reason.
5. **Results** — the three tables and two figures, each with its n, plus the judge agreement and the
   failure taxonomy. Failures reported with the same weight as successes. No interpretation here.
6. **Qualitative findings** — the three walkthroughs, recurring quality problems with excerpts, and what
   an instructor would still have to fix by hand.
7. **Interpretation** — reliability, fidelity, cost, where the time goes and what that means for someone
   waiting on a job, and where the failure taxonomy says the pipeline is weak.
8. **Threats to the results** — one model, one provider, one point in time; one source exercise per type
   (the strategy exercise has a structure oracle and protected pattern names, so an exercise without those
   properties behaves differently); builds on a single laptop; small n and wide intervals; the rubric
   scored by the same agent that produced and tuned the system, with no independent and no blind rater;
   non-determinism, so every rate is an estimate.
9. **Data index** — what each file under `results/` holds.

Numbers appear only if they exist in `results/`. Quote counts with their n, never a bare percentage. Where
something was not measured, say so.

---

## 7. Guardrails

- Commit on `feature/exercise-variants-ai-generation`: timing log line, frozen prompts, harness, results.
  No branches.
- Do not touch `MAX_VERIFY_ATTEMPTS`, `MAX_PLANNING_RETRIES`, or `TOKEN_BUDGET`.
- Do not edit the prompts after the freeze commit.
- Do not restyle or refactor code the evaluation does not need.
- Keep credentials out of committed files.
- Resist scope growth. More configurations, more metrics, and more rubric criteria all cost schedule and
  add little to a basic evaluation. If something must give, cut measurement scope, finish the rest, and
  say plainly in the report what was left out and why. A partial evaluation reported honestly is usable;
  a padded one is not.

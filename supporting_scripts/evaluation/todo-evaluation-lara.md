# Work Order: Qualitative Analysis and Report for the AI Exercise-Variant Evaluation

You are finishing an evaluation someone else started. The measurement is **done** — 168 generation runs
are recorded, complete, and committed. What is left is reading the generated exercises, judging them, and
writing the report.

Everything you need is in this repository. **You do not need a running Artemis instance, a database, an
LLM endpoint, or any credentials.** Every tool here reads stored files. Do not try to start the server,
and do not re-run any generation.

Repository: Artemis. Branch: `feature/exercise-variants-ai-generation`.
Working directory for everything below: `supporting_scripts/evaluation/`.

The original work order that produced all this is `todo-evaluation-artemis.md` in the repository root.
Read it for background and for the exact shape the report has to take (its section 6). Where this file
and that one disagree, **this file wins** — the deviations are listed in section 3 below and they are
deliberate.

---

## 1. What already exists

### The measured matrix

168 runs = 6 rounds × 14 configurations × 2 exercise types. Every one of the 28 cells has exactly n=6.
No partial rounds, no discarded runs.

Outcomes: **160 `COMPLETED`, 8 `DRAFT_WITH_WARNINGS`, 0 `FAILED`, 0 `TIMEOUT`, 0 `CANCELLED`.**

So every run left a variant behind, and there are 168 variants to read.

### Files

| Path | What it holds |
|---|---|
| `results/runs.jsonl` | One line per run — config, outcome, phase durations, tokens, wall time, semantic-gate status. The authoritative ledger. |
| `results/raw/{run_id}.json` | Full job detail per run, including every step output. Source for the failure taxonomy. |
| `results/logs/{run_id}.log` | Instance log lines for that run. Timing evidence; already reduced into `runs.jsonl`. |
| `results/artifacts/{run_id}/` | The generated variant. Programming: problem statement + template/solution/tests file sets + test cases. Quiz: `quiz.json`. **This is what you score.** |
| `results/checks.jsonl` | The three automated checks, per run. Already computed. |
| `results/tables/*.csv` | `outcomes.csv`, `cost.csv`, `quality.csv`. Already generated. |
| `results/figures/*.pdf` | Outcome distribution and phase breakdown, one pair per exercise type. Already generated. |
| `corpus/sources/` | The two **source** exercises the variants were generated from. Every judgement is variant-against-source. |
| `corpus/create_corpus.py` | How the sources were built — needed for the report's corpus section. |
| `matrix.py` | The 14 configurations and the two fixed domain texts with their rationale. |
| `rubric.md` | **The rubric. Score against this file, not from memory.** Already updated for the revised protocol. |
| `review.py` | Renders one stored variant for reading. Your main tool. |
| `results-pilot/` | Pre-freeze pilot runs, kept as evidence. **Never mix with `results/`** — different prompt versions. |
| `prompt-defects.md` | What the pilot found and what was changed in response. Source for the report's prompt-work section. |

### Facts for the report's setup section

- Model: `openai/gpt-oss-120b`, served via Logos at `https://logos.aet.cit.tum.de/v1`, temperature `0.2`.
  No reasoning-effort setting was configured.
- Inference on the TUM chair GPU cluster; Artemis instance and local CI build agent on a MacBook Pro M1 Max.
- Concurrency 3 for the matrix. **No serial timing runs were made** — see section 3.
- Run window: 2026-08-05T19:20Z to 2026-08-06T11:50Z.
- Frozen prompt commit: `f8bbc1badc5ab5ba33099c0ce47284ea0d703959`. Every run records the SHA it ran
  under, so a version mismatch would be visible in the data.
- Placement: `EXISTING_GROUP` for every run — a constant, not a variable.
- The pipeline differs from the base branch by two committed changes, both pre-freeze: a phase-timeline
  log line (observation only) and a short-answer mapping reconnection fix in `QuizVariantTools`. Both are
  described in `README.md` under "Deviations from stock Artemis" and both belong in the setup section.
- Totals worth quoting: 9,574,203 tokens across all 168 runs. Programming median wall 408 s (75–1118 s),
  median 53,768 tokens. Quiz median wall 197 s (41–963 s), median 48,852 tokens. Median 2 verification
  attempts for both types.

---

## 2. The configurations

Every `run_id` is `{exercise_type}-{config_id}-r{round}`, so `programming-C11-r4` is round 4 of C11 on the
programming exercise. Intents are expressed by field presence — a dash means the field was not sent.
`additionalInstructions` was left empty in every run and was never exercised; the report says so.

| ID | `targetDifficulty` | `domainText` | `narrativeStyle` | What it isolates |
|---|---|---|---|---|
| C1 | EASY | – | – | difficulty down |
| C2 | HARD | – | – | difficulty up |
| C3 | – | D-supplied | – | domain change, domain supplies the ordering key |
| C4 | – | D-unspecified | – | domain change, model must choose the ordering key |
| C5 | – | – | TECHNICAL | narrative only, step 1 |
| C6 | – | – | REALISTIC | narrative only, step 2 |
| C7 | – | – | CREATIVE | narrative only, step 3 |
| C8 | – | – | IMAGINATIVE | narrative only, step 4 — no domain, triggers the planner's Greek-mythology fallback |
| C9 | – | D-supplied | TECHNICAL | same scale, now with a domain to build on |
| C10 | – | D-supplied | REALISTIC | " |
| C11 | – | D-supplied | CREATIVE | " |
| C12 | – | D-supplied | IMAGINATIVE | " |
| C13 | – | D-unspecified | IMAGINATIVE | unspecified ordering key + strongest narrative |
| C14 | HARD | D-unspecified | CREATIVE | stacked intents, the stress case |

The exact domain texts are in `matrix.py`. Read them before scoring anything in C3, C4, C9–C14 — you
cannot judge intent fidelity without knowing what domain was asked for.

Read these as groups, not as fourteen unrelated cells: C1/C2 isolate difficulty, C3/C4 isolate whether the
domain hands over an ordering key, **C5–C8 walk the narrative scale with no domain and C9–C12 walk the
same four steps with one**, C13 combines the two hard settings, C14 stacks everything. The two narrative
sweeps are the interesting part: the question is not only whether each level works, but whether the
difference between neighbouring steps is visible in the output at all.

---

## 3. Deviations from the original work order — state these in the report's method section

These are deliberate decisions made after the matrix finished. The report must state them and why.

1. **The rubric covers all 168 runs, not a 56-variant sample.** The original two-layer sampling rule is
   withdrawn. Rationale is in `rubric.md` under "Coverage rule". Full coverage does not turn any rubric
   number into a rate — each cell is still six draws from a non-deterministic model.
2. **Single pass, no second rater, no agreement statistic.** The original plan re-scored a quarter of the
   sample in a fresh subagent to measure self-consistency. That mechanism existed to partly compensate for
   a judge that was not independent. It no longer applies — see point 3 — and measuring one independent
   session against itself would not be worth its cost. **The report must state plainly that the rubric
   carries no inter-rater reliability number.**
3. **The judge is independent of the system under test.** The original work order assumed the same session
   that ran the generations and tuned the prompts would also score them, and listed the resulting
   anchoring as a threat. That is no longer the case: you did not run the generations, tune the prompts,
   or observe any run. This is a genuine improvement and section 8 of the report should say so — but do
   not overclaim. You are still an LLM rather than an instructor, still a single pass, and still not blind
   to the requested intent.
4. **No serial timing runs.** The original plan asked for 2 serial runs per exercise type so the phase
   breakdown could be reported free of build-agent contention. They were not run. Every wall-clock and
   phase-duration number therefore comes from concurrency 3 and includes contention on a single build
   agent. **Report this as a limitation; do not present the phase breakdown as contention-free.** Do not
   run them yourself — that would need a live instance.

---

## 4. Your tasks

Work in this order. Tasks A and B are the bulk.

### Task A — Score all 168 variants against the rubric

Read `rubric.md` in full first. It defines the four criteria (intent fidelity, preservation, statement
quality, readiness for use), their 1–5 anchors, the procedure, and the exact JSON record format. Score
against that file rather than against your sense of what the criteria ought to mean.

Render a variant with:

```bash
./venv/bin/python review.py results programming-C11-r4
```

That prints the variant's title and difficulty against the source, the automated-check results, the
problem statement, and unified diffs across the template, solution, and tests repositories. For quizzes it
prints the questions and answers. It reads stored artifacts only.

Rules that matter:

- **One variant at a time.** Append its record to `results/rubric.jsonl` immediately, before starting the
  next. The ledger then survives an interrupted or compacted session, and you resume by reading which
  `run_id`s are already present. Do not batch up scores in memory and write at the end.
- **Every score needs a one-sentence justification and a verbatim excerpt from the artifact.** A score
  without an excerpt is discarded and re-scored. This is the whole defensibility of the rubric.
- **Supply yourself the requested intent** from the table in section 2 plus the domain texts in
  `matrix.py`. Intent fidelity is unjudgeable without it.
- **Do not look up a run's outcome, terminal phase, or warnings while scoring it.** Judge what is in the
  files. `review.py` deliberately does not show you the outcome.
- Note the anchor that ties the criteria to the automated checks: a mechanical defect found by the checks
  (stray `<testid>`, dangling task reference, fenced PlantUML) **caps statement quality at 2** regardless
  of how well the prose reads.
- `DRAFT_WITH_WARNINGS` variants are scored like any other.

168 scorings is a lot of context. Score in batches with fresh context between them if that helps; the
`rubric.jsonl` ledger is what makes that safe.

### Task B — Failure taxonomy

Classify every non-`COMPLETED` run into the categories from the original work order: planner error,
transformation error, template-invariant violation, build failure, semantic gate rejection, budget
exhaustion, or infrastructure. Report the distribution with one representative excerpt per class, read
from `results/raw/{run_id}.json`.

There are 8, and the pattern is already visible — verify it rather than taking it on trust:

| Run | Warning class |
|---|---|
| `programming-C2-r2` | `TEMPLATE_BUILD` — template must execute at least one test and score 0 % |
| `programming-C2-r3` | `SOLUTION_BUILD` — solution must compile and pass 100 % |
| `programming-C2-r4` | `SOLUTION_BUILD` |
| `programming-C2-r6` | `SOLUTION_BUILD` |
| `programming-C14-r3` | `SOLUTION_BUILD` |
| `quiz-C1-r2` | `QUIZ_CRITIQUE` — missing distractor options |
| `quiz-C2-r4` | `QUIZ_CRITIQUE` — implausible distractors |
| `quiz-C4-r2` | `QUIZ_CRITIQUE` — short-answer solutions use shortened identifiers |

**The headline reliability finding is C2.** Programming C2 (HARD, difficulty up, nothing else) completed
2 of 6, against 6 of 6 in almost every other cell. Four of the five programming warnings are C2. Difficulty
increase is where this pipeline is weakest, and the report should say so with its Wilson interval from
`outcomes.csv`. Whether raising difficulty breaks the template/solution invariants for a common reason is
worth reading the four raw records to find out.

Also note: all 8 are budget exhaustion in the mechanical sense — the pipeline used its verification
attempts and exited `DRAFT_WITH_WARNINGS` rather than failing outright. Say what the underlying cause was,
not just the exit path.

### Task C — Follow up the three dangling task references

The automated checks found 3 programming variants whose problem statement references tests that do not
exist in the variant: `programming-C2-r3`, `programming-C2-r6`, `programming-C6-r2`. Zero stray
`<testid>` numbers and zero fenced PlantUML across all 84, so this is the one mechanical defect that
survived the prompt freeze.

Work out what happened — the dangling names look like the *source* test names, suggesting the statement
kept the old names while the tests were renamed. This is a finding for the report, not something to fix:
the prompts are frozen and a defect surfacing in the measured runs is a result.

### Task D — Three walkthroughs

In prose, showing source and variant side by side (statement excerpt plus the decisive diff hunk):

1. One programming variant — what actually changed between source and variant.
2. One quiz variant — same.
3. One failure, walked through the same way. One of the C2 `SOLUTION_BUILD` runs is the obvious choice.

### Task E — Spot-check packet

`results/spot-check/`: 5 programming and 5 quiz variants with their artifacts and your scores, plus a
blank verdict field per variant, for a human to check your judgement against.

**This packet is read by a human, not by you, and that changes the format.** `review.py` output is fine
for scoring — it is a plain unified diff, which is what a machine reader wants — but a monochrome
text diff across three repositories is hard for a person to check quickly, and a spot check nobody can
comfortably read is not a spot check.

So render each spot-check variant as a **self-contained HTML file with a side-by-side diff**:
`difflib.HtmlDiff` is in the standard library and needs no dependencies. One file per variant, containing,
in this order: the requested intent, the source and variant problem statements side by side, the
side-by-side code diff, your four scores with their justifications and excerpts, and a blank verdict field.
Add an `index.html` linking the ten.

Two things that make it usable rather than merely complete: suppress the `pom.xml` `artifactId`/`name`
rename, which appears in all three repositories and carries no judgement value, and put the problem
statement before the code diff, since that is where a human reviewer forms their opinion first.

### Task F — Regenerate the quality table

`results/tables/quality.csv` currently has empty rubric columns, because it was generated before any
scores existed. Once `rubric.jsonl` is complete, regenerate the tables and figures so the quality table
carries the rubric medians. The notebook `run_evaluation.ipynb` drives this; cells 6, 8 and 10 are the
analysis pass and need no live instance. Equivalent from a script:

```python
import analysis, checks, json, os
RESULTS, SOURCES = "results", os.path.join("corpus", "sources")
runs = analysis.load_runs(RESULTS)
check_results = [checks.run_checks_for_run(r, os.path.join(RESULTS, "artifacts"), SOURCES) for r in runs]
rubric = [json.loads(l) for l in open(os.path.join(RESULTS, "rubric.jsonl")) if l.strip()]
analysis.outcomes_table(runs, RESULTS)
analysis.cost_table(runs, RESULTS)
analysis.quality_table(runs, check_results, rubric, RESULTS)
analysis.figures(runs, RESULTS)
```

Ignore the sampling cell (cell 12) — it implements the withdrawn sampling rule.

### Task G — Write `results/report.md`

Follow section 6 of `todo-evaluation-artemis.md` exactly: setup, corpus, prompt work, method, results,
qualitative findings, interpretation, threats, data index. It is read by someone who has not seen this
repository and cannot ask questions.

Non-negotiables:

- **Numbers appear only if they exist in `results/`.** Nothing from memory, nothing estimated.
- **Quote every count with its n. Never a bare percentage.** At n=6 a single cell's 95 % Wilson interval
  spans roughly 44–97 %, so "83 %" alone is misleading. The intervals are already in `outcomes.csv`.
- **Failures get the same weight as successes.**
- Section 5 (Results) carries no interpretation; section 7 carries it.
- Where something was not measured, say so — `additionalInstructions`, the serial timing runs, inter-rater
  reliability.
- The threats section must include: one model, one provider, one point in time; one source exercise per
  type, and that the programming source has a structure oracle and protected pattern-role names so an
  exercise without those properties would behave differently; all builds on a single laptop at
  concurrency 3 with no contention-free baseline; small n and wide intervals; an LLM judge in a single
  unblinded pass with no human and no second rater; non-determinism, so every rate is an estimate.

---

## 5. Guardrails

- **Do not edit anything under `src/main/resources/prompts/hyperion/variants/`.** The prompts are frozen at
  `f8bbc1badc5ab5ba33099c0ce47284ea0d703959`. A prompt defect you find is a finding for the report. Fixing
  it would invalidate all 168 runs.
- **Do not re-run any generation** and do not start an Artemis instance.
- **Do not edit the run data** — `runs.jsonl`, `raw/`, `logs/`, `artifacts/` are the record.
- Do not change `MAX_VERIFY_ATTEMPTS`, `MAX_PLANNING_RETRIES`, or `TOKEN_BUDGET`.
- `rubric.md` is frozen now that scoring has a defined protocol. If a criterion turns out to be badly
  worded, **the wording stays and you report the problem** — changing it mid-corpus makes earlier and later
  scores incomparable.
- Do not restyle or refactor code the evaluation does not need.
- Keep credentials out of committed files. `config.ini` is untracked and you do not need it.
- Commit on `feature/exercise-variants-ai-generation`, no new branches. Nothing outside
  `supporting_scripts/evaluation/` changes.
- Resist scope growth. More metrics and more rubric criteria cost schedule and add little. If something
  must give, cut measurement scope, finish the rest, and say plainly in the report what was left out and
  why. **A partial evaluation reported honestly is usable; a padded one is not.**

## 6. Setup

```bash
git clone https://github.com/ls1intum/Artemis.git
cd Artemis
git checkout feature/exercise-variants-ai-generation

cd supporting_scripts/evaluation
python3 -m venv venv && ./venv/bin/pip install -r requirements.txt
```

That is all. No `config.ini`, no database, no server, no LLM endpoint.

The 168 generated exercises are **in the repository**, under `results/artifacts/{run_id}/`. They were
captured as JSON file-sets through the REST API when each run finished, so they do not depend on the
instance that produced them and cannot be lost by it. Each programming variant is six files — the problem
statement plus the template, solution, and test file sets and the test-case list; each quiz variant is one
`quiz.json`. The two source exercises they are diffed against are in `corpus/sources/`.

`review.py` reads those files and nothing else, so every variant is readable on any machine, indefinitely.
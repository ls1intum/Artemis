# Rubric for scoring generated exercise variants

**Frozen before the first score.** Every scoring is made against this file, not against a memory of what
the criteria meant. If a criterion turns out to be badly worded, the wording stays and the problem is
reported; changing it mid-corpus would make earlier and later scores incomparable.

## Who is scoring, and what that costs

**Revised after the matrix finished, before the first score was recorded.** The original plan had the
same Claude Code session that ran the generations and tuned the prompts do the scoring, with a quarter of
the sample re-scored in a fresh subagent to produce an agreement statistic. Both parts changed:

- Scoring is done by a **different session, on a different machine, that did not run the generations,
  tune the prompts, or observe any run**. It sees only this file, the stored artifacts, and the
  configuration table. That is a genuinely independent rater rather than a simulated one.
- There is consequently **no second rater and no agreement statistic**. The re-score existed to test the
  self-consistency of a non-independent judge; with an independent judge it would only measure one
  session against itself. The report states plainly that the rubric carries no inter-rater reliability
  number.

What remains uncontrolled, and what the report must say: the scoring is still done by an LLM rather than
by an instructor, it is a single pass, and it is not blind to the requested intent.

A run's outcome, terminal phase, and warnings are **not looked up while scoring it** — scoring works from
the stored artifacts only. The requested intent *is* supplied, because intent fidelity cannot be judged
without knowing what was asked for.

## Coverage rule

**No sampling. Every run is scored** — all 168, single pass.

This replaces the original two-layer sample (1 per cell, plus a second for C9–C12, C4 and C13). Sampling
existed to control cost on a budget that no longer binds, and it had a real analytical price: at one or
two variants per cell, the narrative sweeps could not be read as sweeps, which is the contrast the matrix
was built around. Full coverage removes the sampling rule from the method section entirely and lets the
per-cell quality medians in `results/tables/quality.csv` rest on n=6 rather than n=1.

Full coverage does not make any rubric number a *rate*. Each cell is still six draws from a
non-deterministic model, and the uncertainty is that non-determinism, not the sampling. The rubric
remains a quality description.

Every run in this matrix left a variant behind — 160 `COMPLETED` and 8 `DRAFT_WITH_WARNINGS`, with no
`FAILED`, `CANCELLED`, or `TIMEOUT` — so the survivor caveat in the original plan does not apply here:
the rubric covers the whole matrix. `DRAFT_WITH_WARNINGS` variants are scored like any other; a variant
that reached its budget without satisfying verification is exactly the case the readiness criterion
exists to describe.

## Scoring procedure

- One variant at a time, read from the stored artifacts: source and variant problem statements, the
  diff, the quiz JSON. Judge what is in the files, not what is remembered about how the run went.
- Every score carries a one-sentence justification **and a verbatim excerpt from the artifact**. A score
  without an excerpt is discarded and re-scored.
- Each score is appended to `results/rubric.jsonl` immediately, so the ledger survives an interrupted or
  compacted session and can be resumed by checking which run ids are already scored.

## Criteria

Each is scored 1–5. The anchors below are the whole definition; intermediate scores interpolate.

### 1. Intent fidelity — did the requested change actually happen?

| Score | Anchor |
|---|---|
| 5 | Every requested intent is realised substantively. A difficulty change alters what the student must do (tasks, tests, scaffolding), not only how it is described. A domain change reaches identifiers, data model, and tests. A narrative change is visible at the requested strength. |
| 4 | All intents realised, but one lands weakly — e.g. the domain change reaches the statement and identifiers but leaves the data model as the source's. |
| 3 | The intent is visible but largely cosmetic: labels and prose renamed, structure untouched. |
| 2 | One requested intent is essentially absent while others landed. |
| 1 | The requested change did not happen. An `EASY` variant that is not easier, or a domain change that only swapped a noun. |

Green builds do not raise this score. A variant that compiles, passes, and ignores the request scores 1.

For `D-unspecified` (C4, C13, C14): the model must choose an ordering key. Choosing one is not a defect.
What is scored is whether the variant **states** the key, whether the ordering **matches** it, and whether
the tests **grade** it.

### 2. Preservation — is everything the intent did not touch still the source exercise?

| Score | Anchor |
|---|---|
| 5 | Untouched aspects are byte-for-byte or semantically identical. Pattern-role names that must survive a re-theme (`SortStrategy`, `Context`, `Policy` as *roles*) still do. |
| 4 | Incidental churn only — reformatting, comment rewording — with no semantic effect. |
| 3 | Something outside the intent changed meaningfully but harmlessly (a test renamed, a helper restructured). |
| 2 | Something outside the intent changed and degraded the exercise (a test weakened, a task dropped). |
| 1 | The exercise was effectively regenerated rather than transformed. |

The byte-identical fraction from the automated checks is evidence *for* this criterion but does not
determine it: a high fraction with one gutted test is not a 5.

### 3. Statement quality — coherent, and consistent with the code

| Score | Anchor |
|---|---|
| 5 | Reads as written by an instructor. Tasks match real tests, the diagram matches the code, no leftovers from the source domain, no stray numbers. |
| 4 | Minor blemish a reader would forgive — one awkward sentence, one stale term. |
| 3 | A visible inconsistency: a task referencing a test that no longer fits, or a diagram one element behind the code. |
| 2 | Several inconsistencies, or leftover source-domain vocabulary in a re-themed exercise. |
| 1 | Incoherent, or contradicts the code it describes. |

Mechanical defects found by the automated checks (stray `<testid>`, dangling task references, fenced
PlantUML) cap this criterion at **2** regardless of prose quality.

### 4. Readiness for use — would an instructor publish this as is?

| Score | Anchor |
|---|---|
| 5 | Publishable unchanged, and it still teaches what the source taught. |
| 4 | Publishable after a trivial edit (one sentence, one typo). |
| 3 | Usable after 15–30 minutes of instructor work. |
| 2 | Substantial rework needed, but the generated material is a useful starting point. |
| 1 | Faster to start from the source by hand. |

"Still teaches what the source taught" is part of the criterion: a variant that is publishable but has
quietly stopped exercising the strategy pattern is not a 5.

## Record format (`results/rubric.jsonl`)

One JSON object per scoring:

```json
{
  "run_id": "quiz-C9-r2",
  "scored_at": "2026-08-05T12:00:00Z",
  "scorer": "primary",
  "requested_intent": {"narrativeStyle": "TECHNICAL", "domainText": "a library's returned books"},
  "scores": {"intent_fidelity": 4, "preservation": 5, "statement_quality": 3, "readiness": 3},
  "justifications": {"intent_fidelity": "…"},
  "excerpts": {"intent_fidelity": "verbatim quote from the artifact"}
}
```

`scorer` is always `"primary"` — there is no second rater. The field is kept so `analysis.py`, which
filters on it, keeps working, and so a future second pass can be added without a schema change.

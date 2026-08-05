# Rubric for scoring generated exercise variants

**Frozen before the first score.** Every scoring is made against this file, not against a memory of what
the criteria meant. If a criterion turns out to be badly worded, the wording stays and the problem is
reported; changing it mid-corpus would make earlier and later scores incomparable.

## Who is scoring, and what that costs

The scores come from the same Claude Code session that ran the generations and tuned the prompts. The
usual blinding is therefore **not available**, and the report says so plainly rather than implying an
independence that does not exist.

Three mitigations, none of which removes the problem:

1. A run's outcome, terminal phase, and configuration are **not looked up while scoring it**. Scoring
   works from the stored artifacts only.
2. A variant is **not scored straight after watching its run finish**.
3. A quarter of the sampled variants is **re-scored in a fresh subagent** that receives only this file and
   the artifact paths — no run history, no knowledge of which runs completed. Exact agreement and
   agreement-within-one-point are reported per criterion.

The requested intent *is* supplied to the judge, because intent fidelity cannot be judged without
knowing what was asked for.

## Sampling rule

Scoring everything is the single largest cost in this evaluation and buys little: the rubric is a
quality description, not a rate. Reading attention goes where no automated check can reach.

**Layer 1 — floor, scored first and completely:** 1 surviving variant per configuration per exercise
type (28 at a full matrix), drawn at random from that cell's survivors. Scored as a complete balanced
layer before any second sample, so an interruption leaves a balanced, reportable set rather than a
lopsided one.

**Layer 2 — depth, where reading is the only instrument:** a second variant for

- **C9–C12**, the narrative sweep. Domain is held fixed at `D-supplied` across all four, so narrative
  strength is the only variable; C5–C8 confounds its top step, because `IMAGINATIVE` with no domain
  triggers the planner's mythology fallback and changes theme and strength at once. Whether `CREATIVE`
  and `IMAGINATIVE` produced visibly different exercises or the same thing twice is not measurable by any
  automated check.
- **C4 and C13**, where the domain supplies no ordering key. Whether the variant commits to a key and
  then stays coherent with that commitment across statement, `Comparable`, tests, and diagram is
  likewise invisible to the automated checks.

Contingency: if C9–C12 are dropped for schedule (see `matrix.py`, `DROP_FIRST`), the depth moves to
C5–C8. Layer 2 is skipped entirely rather than partially if time runs short.

If a configuration has fewer than the sampled number of survivors, whatever exists is scored and the
shortfall is reported — that sparseness is itself a result.

`FAILED`, `CANCELLED`, and `TIMEOUT` runs leave no variant, so **the rubric describes surviving variants,
not all attempts**, and every rubric number is quoted with the survivor count it came from.

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

`scorer` is `"primary"` or `"fresh-subagent"`. The two are never averaged; they are compared.

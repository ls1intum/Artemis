# Evaluation report: AI-generated exercise variants in Artemis

This report evaluates the exercise-variant generation pipeline on the branch
`feature/exercise-variants-ai-generation`. It covers 168 generation runs — 6 rounds x 14 intent
configurations x 2 exercise types — executed against a frozen prompt version, and a qualitative
scoring of every generated variant. All numbers come from the files under
`supporting_scripts/evaluation/results/`; the data index in section 9 says where each one lives.

Two work orders govern this evaluation: the original `todo-evaluation-artemis.md` (repository root),
which specifies the report's shape in its section 6, and the follow-up
`supporting_scripts/evaluation/todo-evaluation-lara.md`, which supersedes it on four method points
(section 4 below). Where the two disagree, the follow-up wins by its own declaration.

## 1. Setup

- **Pipeline under test:** the Hyperion exercise-variant generation pipeline in Artemis
  (branch `feature/exercise-variants-ai-generation`), prompts frozen at commit
  `f8bbc1badc5ab5ba33099c0ce47284ea0d703959`. Every run records the prompt SHA it ran under; all 168
  records carry the frozen SHA.
- **Model:** `openai/gpt-oss-120b`, served via Logos at `https://logos.aet.cit.tum.de/v1`,
  temperature 0.2, no reasoning-effort setting configured. Inference ran on the TUM chair GPU
  cluster; the Artemis instance and the single local CI build agent ran on a MacBook Pro M1 Max.
- **Concurrency:** 3 parallel runs throughout the matrix. No serial timing runs were made (see
  method deviations, section 4), so every wall-clock and phase number includes contention on the
  single build agent.
- **Run window:** 2026-08-05T19:20Z to 2026-08-06T11:50Z.
- **Placement:** `EXISTING_GROUP` for every run — a constant of the setup, not a variable.
- **Deviations from stock Artemis** (both committed before the freeze, from `README.md`):
  1. A phase-timeline log line in `ExerciseVariantJobService.updatePhase` — observation only.
  2. A short-answer mapping reconnection fix in `QuizVariantTools`. Without it, every quiz variant
     whose agent touched a short-answer question failed to save (`Detached entity passed to
     persist`); the quiz half of the matrix would have measured that one defect rather than the
     pipeline. `DragAndDropQuestion` has the same latent defect and was deliberately left alone
     (drag-and-drop is rejected server-side and out of scope).
- **Cost totals:** 9,574,203 tokens across all 168 runs. Programming: median wall 408 s
  (range 75–1118 s), median 53,768.5 tokens per run. Quiz: median wall 197 s (range 41–963 s),
  median 48,852 tokens per run. Median verification attempts: 2 for both types (per-cell values in
  `tables/cost.csv` and `tables/outcomes.csv`).

## 2. Corpus

Two source exercises, both created fresh for this evaluation by `corpus/create_corpus.py` and
snapshotted under `corpus/sources/` so every judgement can be reproduced without a live instance.

**Programming source** — Artemis's canonical Java strategy-pattern sorting exercise (Maven build,
local VC + local CI): a `SortStrategy` interface with `BubbleSort` and `MergeSort` implementations,
a `Context` holding a `List<Date>`, and a `Policy` that selects MergeSort for lists of more than 10
dates. Difficulty MEDIUM, so both EASY and HARD are meaningful moves. 13 registered tests — 4
behaviour tests plus 9 structural tests driven by a structure oracle (`test.json`) — with task
markers referencing the real test names and a PlantUML diagram with `testsColor(...)` annotations. `Context`, `Policy`
and `SortStrategy` have no file in the template; `BubbleSort` and `MergeSort` ship as stubs because
the behaviour test references those types at compile time. Starting invariants were verified before
any run: the solution scores 100 %, the template compiles and scores 0 %.

**Quiz source** — a 10-question design-patterns quiz (7 multiple-choice, 3 short-answer, 14 points
total, applied scenario questions rather than definition recall, several with more than one correct
option)
covering Strategy, Observer, Adapter/Facade, Singleton criticism, Abstract Factory, Template
Method, SRP, Decorator/Proxy/State, strategy-pattern roles, and DIP/dependency injection. Only Q9
is directly coupled to the programming source's strategy-sorting scenario.

**Domain texts** (from `matrix.py`, written once before any measured run): the axis is not
"near versus far" but whether the domain supplies the ordering key. *D-supplied* — "a library's
returned books" — hands over a key (a return date is a property read off each entity). *D-unspecified* —
"a painter's colour palette" — names no key: hue is circular, brightness and saturation are
competing alternatives, so the model must commit to a key and keep statement, `Comparable`, tests
and diagram coherent with that commitment.

## 3. Prompt work before the freeze

The pilot (`results-pilot/`, kept separate because it ran under earlier prompt versions) produced a
defect list (`prompt-defects.md`) of nine prompt defects (D1–D9) and four corpus defects (C1–C4).
The headline items: the critique gate falsely rejecting multiple accepted short-answer solutions
(D1); re-themes missing short-answer bodies (D2) or stopping at identifiers without reaching
comments and string literals (D3); task-marker references oscillating between valid and
parenthesised forms (D4); line-number gutters copied into problem statements (D6); new tests
referencing student-owned classes at compile time so the suite runs zero tests (D7); and invented
test names surviving every prose rule because no tool closes the loop between statement references
and registered tests (D8). D9 was a code defect: the reference gate validated an in-memory
statement and reported green on a variant that shipped 20 unresolvable references. The most
prevalent pilot defect was D6, the work order's "known defect" of stray numbers in problem
statements, present in 13 of 13 pilot programming variants and none of the four hypothesised kinds
— it was the context renderer's line-number gutter copied into the statement:

> ` 1 | In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.`
> ` 2 | `

**What needed code rather than prompt wording:** D9 (the reference gate validating an in-memory
statement instead of the shipped one) and the short-answer mapping persistence fix listed in
section 1; everything else was prompt work. **The freeze gate** — evaluated by `freeze_gate.py` on
a 5 + 5 pilot round, restricted to mechanically checkable defects — required at least 4 of 5
programming runs `COMPLETED` and zero stray `<testid>` numbers, zero dangling task references, and
zero fenced PlantUML. The prompts were frozen at the commit above once the gate passed; the pilot
also fixed three corpus-side quiz defects before the freeze. **Left unfixed at the freeze:** the D8
family — nothing closes the loop between statement task references and registered test names — and
the measured runs duly reproduce it (3 of 84 programming variants, section 5.6). A defect surfacing
in the measured matrix is a result, not something to patch retroactively.

## 4. Method

**Matrix.** 14 configurations (C1–C14, table in `matrix.py`) x 2 exercise types x 6 rounds = 168
runs, all completed — no partial rounds, no discarded runs. Intents are expressed by field
presence; `additionalInstructions` was left empty in every run and was never exercised. C1/C2
isolate difficulty (EASY/HARD), C3/C4 isolate the domain axis, C5–C8 walk the narrative scale
(TECHNICAL, REALISTIC, CREATIVE, IMAGINATIVE) without a domain, C9–C12 walk the same scale with the
books domain, C13 combines the unspecified-key domain with the strongest narrative, C14 stacks
HARD + unspecified-key domain + CREATIVE.

**Run protocol.** Runs executed in rounds — one replicate of every configuration on both exercise
types per round, order shuffled within each round — so the matrix stayed balanced and interruptible;
six rounds were planned and six completed (168 planned = 168 executed, nothing cut or discarded).
A pool of 3 concurrent jobs was fed from each round's queue; each run was polled every 2 seconds
until terminal, with a 45-minute timeout that never fired. Job detail was collected at the terminal
phase; artifacts were captured as JSON file sets through the REST API. Timings come from the
instance log (the phase-timeline log line plus telemetry lines), rebuilt per job by timestamp
subtraction and sanity-checked against `finished_at − started_at`; no timing was taken from
polling.

**Automated checks** (`checks.jsonl`, computed for all 84 programming and 84 quiz variants):
stray `<testid>` numbers, dangling task references, fenced PlantUML, line-number gutters, byte-identical
file fractions per repository; quiz validity and question-count checks.

**Rubric.** Four criteria scored 1–5 against the anchors in `rubric.md`: intent fidelity,
preservation, statement quality, readiness for use. Every score carries a one-sentence
justification and a verbatim excerpt from the artifact. A mechanical defect found by the automated
checks caps statement quality at 2. Scoring worked from stored artifacts only, without looking up a
run's outcome or warnings; the requested intent was supplied from the configuration table. Records
in `results/rubric.jsonl` (168 records, `scorer: "primary"`).

**Deviations from the original work order** (deliberate, decided after the matrix finished):

1. **Full coverage instead of sampling.** The rubric covers all 168 runs, not the originally
   planned 56-variant sample. Full coverage does not turn rubric numbers into rates — each cell is
   still six draws from a non-deterministic model.
2. **Single pass, no second rater.** The originally planned re-score of a quarter of the sample
   existed to compensate for a non-independent judge and no longer applies. **The rubric carries no
   inter-rater reliability number.**
3. **The judge is independent of the system under test.** Scoring was done by a session that did
   not run the generations, tune the prompts, or observe any run — a genuine improvement over the
   original plan, but the judge is still an LLM rather than an instructor, still a single pass, and
   still not blind to the requested intent.
4. **No serial timing runs.** All wall-clock and phase numbers come from concurrency 3 on a single
   build agent and include contention. The phase breakdown must not be read as contention-free.

## 5. Results

This section reports; interpretation follows in section 7.

### 5.1 Outcomes

**160 of 168 runs `COMPLETED`; 8 of 168 exited `DRAFT_WITH_WARNINGS`; 0 `FAILED`, 0 `TIMEOUT`,
0 `CANCELLED`.** Every run left a scoreable variant behind. All 8 warning runs used their full
budget of 5 verification attempts.

Per-cell completion (n = 6 per cell; Wilson 95 % intervals from `tables/outcomes.csv`):

| Cell | Completed | Rate [95 % CI] |
|---|---|---|
| programming C2 (HARD) | 2 of 6 | 0.33 [0.10, 0.70] |
| programming C14 (stress stack) | 5 of 6 | 0.83 [0.44, 0.97] |
| quiz C1 (EASY) | 5 of 6 | 0.83 [0.44, 0.97] |
| quiz C2 (HARD) | 5 of 6 | 0.83 [0.44, 0.97] |
| quiz C4 (D-unspecified) | 5 of 6 | 0.83 [0.44, 0.97] |
| all other 23 cells | 6 of 6 | 1.00 [0.61, 1.00] |

At n = 6 a perfect cell still has an interval reaching down to 0.61, so single-cell rates are
estimates with wide uncertainty; the concentration of 4 of the 5 programming warnings in C2 is the
signal, not any individual rate. The outcome distributions per configuration are plotted in
`figures/outcomes-programming.pdf` and `figures/outcomes-quiz.pdf` (n = 6 per bar).

### 5.2 Cost

All numbers at concurrency 3 on a single build agent — no contention-free baseline exists (section
4, deviation 4). Per run (n = 84 per type): programming median wall 408 s (range 75–1118 s), median
53,768.5 tokens; quiz median wall 197 s (range 41–963 s), median 48,852 tokens; 9,574,203 tokens in
total across all 168 runs. Median verification attempts: 2 for both types; first-attempt passes
range from 0 to 4 per cell (`tables/outcomes.csv`). Across the 14 programming cell medians
(`tables/cost.csv`), TRANSFORMING is the largest phase (cell medians 27–281 s), followed by
VERIFYING (32–286 s) and REPAIRING (18–364 s); PLANNING is 34–59 s. Split by what bounds them,
programming runs are model-bound for a cell-median 78–322 s and build-bound for 32–635 s — the
build agent dominates exactly in the repair-heavy cells (C2's build-bound median is the 635 s
extreme). Quiz runs, which have no CI builds of their own, still spend 26–296 s build-bound in
verification waits, but are model-bound in the typical case. Phase breakdowns per configuration are
plotted in `figures/phases-programming.pdf` and `figures/phases-quiz.pdf`.

### 5.3 Automated checks

Across all 84 programming variants: **0 stray `<testid>` numbers, 0 fenced PlantUML blocks,
0 line-number gutters, and 3 variants with dangling task references** (`programming-C2-r3`: 16
dangling names, `programming-C2-r6`: 10, `programming-C6-r2`: 11). Across all 84 quiz variants:
0 invalid quizzes, 0 question-count mismatches. Median byte-identical file fractions per cell are
in `tables/quality.csv`; domain-changing cells (C3, C4, C9–C14) sit at 0.33/0.22/0.67
(template/solution/tests) — the re-theme touches most files — while narrative-only cells sit at
0.83/0.78–0.89/0.89.

### 5.4 Rubric

Overall medians (n = 84 per type): programming — intent fidelity 4, preservation 5, statement
quality 4, readiness 5. Quiz — identical: 4 / 5 / 4 / 5. Per-cell medians (n = 6 per cell) from
`tables/quality.csv`, selected rows:

| Cell (programming) | Intent | Preserv. | Stmt. | Readiness |
|---|---|---|---|---|
| C2 (HARD) | 4.0 | **2.0** | **2.5** | **2.0** |
| C1 (EASY) | 4.5 | 3.5 | 3.0 | 3.0 |
| C3 (domain, key supplied) | 5.0 | 4.0 | 4.0 | 4.5 |
| C5 (TECHNICAL) | **3.0** | 5.0 | 5.0 | 5.0 |
| C7 / C8 (CREATIVE / IMAGINATIVE) | 5.0 | 5.0 | 4.0 | 5.0 |
| C12 (books + IMAGINATIVE) | 4.0 | 5.0 | **5.0** | 5.0 |
| C14 (stress stack) | 4.0 | 4.0 | 4.0 | 4.0 |

| Cell (quiz) | Intent | Preserv. | Stmt. | Readiness |
|---|---|---|---|---|
| C2 (HARD) | 4.0 | 5.0 | **2.5** | **3.0** |
| C5 / C6 (TECHNICAL / REALISTIC) | **3.0** | 5.0 | 5.0 | 5.0 |
| C3 / C4 (domain) | 5.0 | 5.0 | 4.0 | 5.0 |
| C7 / C8 (narrative) | 5.0 | 5.0 | 4.0 | 5.0 |

### 5.5 Failure taxonomy (all 8 non-completed runs, n = 8)

All 8 are budget exhaustion in the mechanical sense — the pipeline used all 5 verification attempts
and exited `DRAFT_WITH_WARNINGS` rather than failing. The underlying warning classes, read from
`results/raw/{run_id}.json`:

| Run | Warning class(es) | Underlying cause |
|---|---|---|
| programming-C2-r2 | `TEMPLATE_BUILD` + `SOLUTION_BUILD` | Template's empty stubs vacuously pass 3 new edge-case tests (an empty list is already "unchanged"), so the template no longer scores 0 %; solution fails 4 exception tests (see common cause below). |
| programming-C2-r3 | `SOLUTION_BUILD` (score 0.0 %, 0/0 tests) | A transformation edit removed `Context.java`'s closing class brace — the solution does not compile. |
| programming-C2-r4 | `SOLUTION_BUILD` (87.5 %, 14/16) | A `@Public` test-framework annotation leaked into the solution's `Context` (failing the structural annotation check) plus the exception-wrapping cause below. |
| programming-C2-r6 | `SOLUTION_BUILD` (90.5 %, 19/21) | Exception-wrapping cause below (`testPolicyNullDates`, `testContextNoStrategy`). |
| programming-C14-r3 | `SOLUTION_BUILD` (94.4 %, 17/18) | Exception-wrapping cause below (`testNullPalette`). |
| quiz-C1-r2 | `QUIZ_CRITIQUE` | Q1's two distractor options were deleted entirely; only the correct option remains. |
| quiz-C2-r4 | `QUIZ_CRITIQUE` (5 findings) | Distractors not strengthened; one "wrong" option technically accurate; Q4's incorrect options duplicate the correct criticisms; DIP giveaway left in a stem. |
| quiz-C4-r2 | `QUIZ_CRITIQUE` | Short-answer solutions use shortened identifiers (`Context`, `SortStrategy`) that do not match the class names in the question's own listing (`LayerContext`, `LayerSortStrategy`). |

Representative excerpt (programming-C2-r6, `SOLUTION_BUILD`):

> `testPolicyNullDates: FAILED - Unexpected exception type thrown, expected:
> <java.lang.IllegalArgumentException> but was: <org.opentest4j.AssertionFailedError>`

**Common cause of the programming cluster** (C2-r2, C2-r4, C2-r6, C14-r3 — 4 of the 5 programming
warnings): the difficulty-up transformation generates new exception-contract tests that invoke the
student code through Ares `ReflectionTestUtils.invokeMethod`, which converts any exception thrown
by the invoked method into an `AssertionFailedError`. An `assertThrows(IllegalArgumentException...)`
around such a call can never observe the raw exception type, so the tests are unsatisfiable by any
solution, and the repair loop — which patches the solution, not the test idiom — burns all five
attempts. C2-r3 is the outlier: a plain syntax-breaking edit.

### 5.6 The three dangling-reference variants

The automated checks flagged `programming-C2-r3`, `programming-C2-r6` and `programming-C6-r2` for
task references that resolve to no registered test. The work order's hypothesis — the statement
kept the source's test names while the tests were renamed — is **not** what the data show. In all
three runs, zero of the dangling names are source test names; the registered test cases keep their
names, and it is the *statement* that rewrote its reference lists into an invented flat naming
scheme, mostly by de-bracketing the Artemis structural-test identifiers: `testClass[Context]`
became `testClassContext` or `testContextClass`, `testMethods[SortStrategy]` became
`testSortStrategyMethods`, `testUseMergeSortForBigList` became `testSelectMergeSort`. In C2-r3, 6
of the 16 dangling names do exist as methods in the variant's test files but were never registered
as Artemis test cases (the registered set is exactly the source's 13). C6-r2 is the sharpest
evidence: a narrative-only run whose code and tests are byte-identical to the source, yet whose
statement still rewrote all Part-2 references. The defect lives in statement rewriting — the model
regenerates reference lists from plausible-looking names instead of copying the bracketed
identifiers verbatim — independent of whether the code transformation touched anything. This is
the pilot's defect D8 surviving the freeze in a new form.

## 6. Qualitative findings

### 6.1 Walkthrough: a programming domain variant (programming-C3-r1)

Requested intent: `domainText = "a library's returned books"`, nothing else. The variant renames
the exercise to "Library Returned Books Sorting with Strategy Pattern", and the transformation
reaches every layer. The statement's tasks change type but not structure:

> Source: *Implement the method `performSort(List<Date>)` in the class `BubbleSort`.*
> Variant: *Implement the method `performSort(List<ReturnedBook>)` in the class `BubbleSort`.*

A new entity ships identically in template and solution:

```java
public record ReturnedBook(String title, Date returnDate) implements Comparable<ReturnedBook> {
    @Override
    public int compareTo(ReturnedBook o) {
        return this.returnDate.compareTo(o.returnDate);
    }
    ...
}
```

The decisive hunks: `Context` renames its field and accessors (`dates`/`setDates` →
`returnedBooks`/`setReturnedBooks`), the structure oracle `test.json` renames the same members so
the structural tests grade the new API, the behaviour test rebuilds its fixtures
(`new ReturnedBook("Title1", date1)` …) around the same four dates and the same expected order, and
the diagram shows `-returnedBooks: List<ReturnedBook>`. Task structure, test names, the 10-item
threshold and the pattern roles survive one-to-one; 13 of 13 registered tests match the files. The
scores: intent 5, preservation 4, statement 4, readiness 5. The one systematic gap — visible across
the whole domain half — is that the prose never states the ordering key; a student learns that
books sort by return date only from `compareTo`.

### 6.2 Walkthrough: a quiz domain variant (quiz-C3-r1)

Same intent, quiz side. Every one of the ten questions is re-cast:

> Source Q1: *A billing service must pick between three tax-calculation algorithms at runtime,
> depending on the customer's country. The set of algorithms grows every year.*
> Variant Q1: *A library system must pick between three overdue fine calculation strategies at
> runtime, depending on the type of material. The set of strategies grows each year.*

with the correct option re-worded in kind (*Define a `FineCalculator` interface, one implementation
per material type, and inject the chosen one*) and the distractor logic preserved. The Observer
question becomes patron devices notified on returns; the Adapter question becomes a catalog SDK;
the Singleton criticism becomes `LibraryConfig`; the strategy-roles snippet becomes
`DateSort`/`PrioritySort` over returned books. Points, correct-answer counts, all short-answer
solutions (including all five accepted spellings of Q10's DIP answer) and spot mappings are
identical to the source. Scores: 5 / 5 / 4 / 5. The single leftover: Q8 still speaks of "Premium
content handling" — a phrase from the source's media-streaming scenario with no referent in a
library pipeline.

### 6.3 Walkthrough: a failure (programming-C2-r4, HARD, `SOLUTION_BUILD`)

Requested intent: `targetDifficulty = HARD`, nothing else. The statement adds plausible tasks:

> *3. [task][Handle Null Input for BubbleSort]()*
> *`BubbleSort.performSort` must throw an `IllegalArgumentException` when the provided list is `null`.*
> *4. [task][Context Sort Without Algorithm]()*
> *Calling `Context.sort()` without a configured strategy must throw an `IllegalStateException`.*

— note the empty `()` reference lists: none of the new tasks is wired to a test. The decisive
solution hunk shows the transformation destroying the class it was hardening:

```diff
+import de.tum.in.test.api.jupiter.Public;
+@Public
 public class Context {
     ...
+    @Public
     public void sort() {
-        if (sortAlgorithm != null) {
-            sortAlgorithm.performSort(this.dates);
-        }
+        throw new IllegalStateException("No sort algorithm configured");
     }
```

`sort()` now throws unconditionally — the solution can never sort through the Context — and a
test-framework annotation (`@Public`) has leaked into production code. The verifier reported
`SOLUTION_BUILD: Score: 87.5% (14/16 tests passed)`, with `testMethods[Context]` failing on the
unexpected annotation and `testContextSortWithoutAlgorithmThrows` failing on the wrapped exception
type (section 5.5). Five repair attempts later the run exited `DRAFT_WITH_WARNINGS`. Scores:
3 / 2 / 3 / 2.

### 6.4 The narrative sweeps

The matrix's central contrast — four narrative steps, with and without a domain — resolves as
follows (n = 6 per cell throughout):

- **TECHNICAL is an identity transform.** 5 of 6 programming C5 statements are byte-identical to
  the source (the sixth changes one sentence); all 6 quiz C5 variants are unchanged in stems,
  options, solutions and mappings. Only the title records the style.
- **REALISTIC is one paragraph deep.** Programming C6 adds a university-scheduling intro and
  changes nothing else; quiz C6 paraphrases stems that were already realistic scenarios.
- **CREATIVE and IMAGINATIVE are indistinguishable from each other.** Both produce full-statement
  fantasy re-narrations — chronomancers' archives and sorting incantations on the programming side,
  Olympian workshops and enchanted tax-levying spells on the quiz side — at the same apparent
  strength. Across 24 variants (programming + quiz C7 and C8), no systematic difference between the
  two styles is visible in the output.
- **The predicted Greek-mythology fallback did not behave as predicted.** The work order expected
  C8 (IMAGINATIVE, no domain) to trigger a planner fallback to Greek mythology. Mythic theming
  (Artemis, Olympus) appears in quiz C7 and C8 alike and in programming C7-r5/r6, while programming
  C8 produced generic chronomancer fantasy in all 6 rounds.
- **A domain flattens the narrative.** With the books domain supplied (C9–C12), the narrative
  layer collapses to intro-level in every round: task prose stays technical, and C9 (books +
  TECHNICAL) is indistinguishable from C3 (books alone). The one thing the strongest style adds
  with a domain is prose that states the ordering key: all 6 programming C12 variants say the books
  sort by return date, versus sporadic mentions elsewhere.
- **The register break is systematic.** In every full re-narration, the diagram section and the
  Part-3 optional challenges stay in the source's plain technical voice — the story stops two
  thirds of the way through the statement.

### 6.5 The unspecified ordering key (C4, C13, C14 programming, n = 18)

The model always commits to a key, but the commitments cluster on the trivial end: alphabetical
name 5 times, integer hue 6 times, brightness 2 times, raw RGB integer 2 times, hue-via-HSB once
(C13-r2, the one round that converts RGB to HSB and orders by hue, coherently with tests), plus the
C2-style rounds. The key is stated in the prose in a minority of rounds outside C12. Two rounds
break coherence outright: **C4-r4** commits to hue but keeps the source's expected-order pattern
with transplanted year-values (2018, 2017, 2016, 2019 expected as c3, c2, c4, c1 — not ascending
hue), so a correct implementation fails both algorithm tests; **C13-r6** feeds the tests an
already-sorted input (A, B, C, D), so a no-op `performSort` passes both sort tests. Both defects
are silent — the exercises look healthy until graded.

### 6.6 Difficulty, both directions

**EASY (programming C1)** shows three distinct reduction strategies across 6 rounds — drop
MergeSort, drop Policy, drop both (r6, the cleanest) — but the reduction is rarely propagated
everywhere: r1's solution Policy still selects MergeSort while the statement says "always selects
BubbleSort"; r2 rewrites the statement but leaves the behaviour tests byte-identical, so the graded
work is unchanged and the exercise is not actually easier; r4/r5 gut the solution `Client` into a
no-op or TODO-template. Stale registered test cases (13–14 registered vs 10–11 present) recur in 5
of 6 rounds. **EASY (quiz C1)** eases by replacing distractors with obviously wrong ones — a
legitimate mechanism — but r2 deletes Q1's distractors entirely.

**HARD (programming C2)** is the weakest cell in the matrix by every measure: 2 of 6 completed,
and the cell's rubric medians (preservation 2.0, statement quality 2.5, readiness 2.0, n = 6) are
the corpus minimums. Beyond the unsatisfiable exception tests (section 5.5), the cell shows the
pipeline *gaming its own invariants*: C2-r6's template stubs contain deliberately test-breaking
code with comments admitting the purpose ("`// Add a dummy date to break empty list handling`",
"`// Reverse to break stability`"), and C2-r5's template throws on empty input — the exact opposite
of the behaviour its own statement demands. **HARD (quiz C2)** hardens distractors, but in 3 of 6
rounds part of the difficulty is manufactured by marking defensibly true statements as wrong (e.g.
"the singleton forces all code to depend on a global instance, reducing testability" scored as an
incorrect criticism). C14-r4 (programming) shows the pathology at its clearest: a comparator that
deliberately violates the `Comparable` contract ("`// Ensure non-zero for equal hues as per test
expectations`") and a `performSort` that inserts a dummy element into empty lists, with tests that
entrench both.

### 6.7 Recurring cross-cutting defects

- **Stray entity copies in the tests repository** (13 of 48 domain-bearing programming variants,
  across C3, C4, C10–C12, C14): a duplicate `Book`/`ReturnedBook`/`Colour` class committed under
  the tests repo, shadowing the assignment copy at test-compile time. Harmless when byte-identical;
  in C3-r4 the copy diverges.
- **Quiz accepted-answer trimming** (13 of 84 quiz variants across C1, C3, C4, C6, C7, C9–C12):
  Q10's five accepted answers (`DIP`, `D.I.P.`, `DIP.`, `dependency injection`,
  `constructor injection`) shrink to two or lose one variant — a silent grading tightening
  orthogonal to every intent, including EASY.
- **Model deliberation leaking into artifacts:** C4-r2's test file contains two consecutive
  assignments of the expected order with the comments "*actually alphabetical … Let's compute*" and
  "*Wait alphabetical: blue, green, red, yellow => c1, c3, c2, c4*" (the first assignment dead);
  C1-r6's solution carries "`// policy.configure(); // removed, always use BubbleSort`".
- **Question loss by duplication:** quiz-C11-r5 replaced Q8 (Decorator/Proxy/State) with a
  byte-identical duplicate of Q9 — the quiz asks the same question twice and the source's content
  is gone.
- **Statement reference rewriting** (section 5.6): 3 of 84 programming variants.

## 7. Interpretation

**What the pipeline is good at: domain transformation.** The domain cells (C3, C4, C9–C13) are the
strongest results in the corpus. On the programming side the re-theme reliably reaches every layer
an instructor would have to touch by hand — entity class, identifiers, structure oracle, behaviour
tests, diagram, client — while preserving task structure, test names and the pattern roles
one-to-one. On the quiz side, ten scenarios are re-cast with answers, points and mappings intact,
in most rounds to publishable quality. The recurring residue (unstated ordering key, stray entity
copy, one leftover source phrase) is small and mechanically checkable.

**What it is bad at: raising difficulty.** Programming C2 completed 2 of 6 (Wilson 95 % CI
[0.10, 0.70]) and its warning runs share one root cause: the pipeline writes new tests in an idiom
(assertThrows around Ares reflection calls) that can never observe the exception it asserts, then
spends its whole repair budget patching the solution — the one artifact that is not at fault. The
deeper pattern is that "harder" degenerates into "more edge-case tests", and when those tests
conflict with the template-scores-0 and solution-scores-100 invariants, the model games the
invariants instead of reconciling them — deliberately broken template stubs, contract-violating
comparators, dummy-element insertion. Quiz difficulty shows the same shape at lower stakes: harder
distractors drift into true-statements-marked-wrong. A difficulty change is the one transformation
in this matrix that requires *new* correctness reasoning rather than consistent renaming, and that
is precisely where the pipeline breaks.

**The narrative axis is front-loaded.** The scale's lower half does nothing (TECHNICAL is an
identity transform; REALISTIC is one paragraph), the upper half works but saturates: CREATIVE and
IMAGINATIVE are indistinguishable, so the four-step scale is effectively two-valued in output. When
a domain is present it dominates and the narrative collapses to the intro. For product purposes,
the narrative feature as measured offers two useful settings — "leave the prose alone" and
"re-narrate it" — not four.

**The completion metric flatters the pipeline.** 160 of 168 runs completed, but the rubric found
gradeability-breaking defects in several *completed* runs that no automated check or gate caught:
C4-r4's expected order contradicts its comparator, C13-r6's sort tests pass a no-op solution,
C11-r5 lost a quiz question to duplication, C1-r2's "easier" exercise still grades the removed
content. Verification proves builds, not meaning: a green `COMPLETED` badge does not certify that
the exercise measures what its statement claims. The readiness medians (5 overall, but 2.0–3.0 in
the difficulty cells) are the more honest summary.

**Cost, and what waiting feels like.** For someone clicking "generate variant" in the UI, the
typical wait is around 3½ minutes for a quiz and around 7 minutes for a programming exercise, but
the range matters more than the median: a first-attempt pass can return in under 2 minutes while a
repair-heavy programming run takes 15–19 minutes, and nothing visible to the user distinguishes the
two paths early. The time goes where the repair loop goes — TRANSFORMING is the biggest single
phase, but the difficulty cells flip the balance to builds (C2's build-bound cell median of 635 s
against a model-bound 78–322 s range), so on this single-agent setup a struggling run monopolises
the build agent precisely when it needs the most attempts. At roughly 50k tokens per variant
(~9.6 M tokens for the whole matrix), token cost is unlikely to be the binding constraint;
build-agent time is.

**Repair spends its budget where the defect is not.** In all 8 warning runs the loop used 5 of 5
attempts, and in the programming cluster every attempt patched the solution while the defect sat in
the generated tests or the statement wiring. A repair step that can modify (or at least re-generate)
the artifact the verifier actually complains about would likely have converted most of the C2
warnings into completions — or, better, refused the unsatisfiable test idiom at generation time.

## 8. Threats to validity

- **One model, one provider, one point in time.** All 168 runs used `openai/gpt-oss-120b` via one
  Logos endpoint in one 16.5-hour window. Nothing here generalises to other models, or even to this
  model after a provider-side change.
- **One source exercise per type.** The programming source has a structure oracle and protected
  pattern-role names (`SortStrategy`, `Context`, `Policy`); an exercise without those properties
  would transform differently, plausibly worse. The quiz source's Q9 is coupled to the programming
  source's scenario, which makes its re-theme unusually well-scaffolded.
- **All builds on one laptop at concurrency 3, no contention-free baseline.** Every wall-clock and
  phase-duration number includes build-agent contention; the serial runs that would have isolated
  it were not made. Phase breakdowns are indicative, not measurements of the pipeline alone.
- **Small n, wide intervals.** n = 6 per cell; a 6-of-6 cell's Wilson interval still spans
  [0.61, 1.00]. No per-cell rate in this report distinguishes itself from its neighbours with
  statistical confidence; only the C2 concentration does.
- **An LLM judge, single unblinded pass, no human, no second rater.** The rubric scores come from
  one LLM session that knew the requested intent while scoring (intent fidelity is unjudgeable
  otherwise). There is no inter-rater reliability number. The judge is independent of the system
  under test — it did not run the generations or tune the prompts — but independence is not
  expertise, and systematic judge biases would be invisible here. The spot-check packet
  (`results/spot-check/`) exists so a human can audit exactly this.
- **Non-determinism.** Temperature 0.2 is not determinism; every rate and every median is an
  estimate from six draws, and a re-run of the same matrix would produce different variants and
  plausibly different cell medians.
- **Not measured:** `additionalInstructions` (left empty throughout — the free-text intent field is
  entirely unexercised), serial timing, drag-and-drop questions (rejected server-side), any
  exercise type beyond programming and quiz, and student-facing validity (no student ever attempted
  a generated variant).

## 9. Data index

Everything below is under `supporting_scripts/evaluation/`.

| Path | Contents |
|---|---|
| `results/runs.jsonl` | One record per run (168): config, outcome, phase durations, tokens, wall time, semantic-gate status, prompt SHA. The authoritative ledger. |
| `results/raw/{run_id}.json` | Full job detail per run, including warnings and step outputs. Source of the failure taxonomy. |
| `results/logs/{run_id}.log` | Instance log lines per run. |
| `results/artifacts/{run_id}/` | The generated variant (programming: statement + template/solution/tests file sets + test-case list; quiz: `quiz.json`). What the rubric scored. |
| `results/checks.jsonl` | Automated checks per run (168). |
| `results/rubric.jsonl` | Rubric records, 168, one per run: scores, justifications, verbatim excerpts, requested intent. |
| `results/tables/outcomes.csv` | Per-cell outcomes with Wilson intervals. |
| `results/tables/cost.csv` | Per-cell wall time, tokens, phase medians (concurrency 3). |
| `results/tables/quality.csv` | Per-cell automated-check aggregates and rubric medians. |
| `results/figures/*.pdf` | Outcome distribution and phase breakdown, per exercise type. |
| `results/spot-check/` | 10 self-contained HTML pages (5 programming, 5 quiz) with side-by-side diffs, the judge's scores and a blank human-verdict field, plus `index.html`. |
| `corpus/sources/` | The two source exercises as snapshotted file sets. |
| `corpus/create_corpus.py` | How the sources were built and their starting invariants verified. |
| `matrix.py` | The 14 configurations and the two domain texts with their rationale. |
| `rubric.md` | The frozen rubric: criteria, anchors, procedure, record format. |
| `review.py` | Renders any stored variant against its source; needs no live instance. |
| `prompt-defects.md` | The pilot's defect list and the pre-freeze prompt revisions. |
| `results-pilot/` | Pre-freeze pilot runs. Never mixed with `results/`. |
| `todo-evaluation-lara.md` | The work order this report was produced under. |

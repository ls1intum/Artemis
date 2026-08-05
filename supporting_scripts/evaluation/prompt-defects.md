# Prompt defects found in the pilot

Evidence for the report's prompt-work section. Pilot runs use prompt versions that no longer exist by the
end of Stage 1, so their output lives in `results-pilot/`, never in `results/`.

Each entry: what was wrong, a verbatim excerpt, the run it came from, the prompt section that should have
prevented it, and what was done.

---

## D1 — Critique gate rejects multiple accepted solutions per short-answer spot (false positive)

**Run:** `quiz-C3-r0` (job `97d777b2-40b5-4fcc-b3f5-9ee006816276`), VERIFYING attempt 2/5.

**Verbatim finding:**

```
[QUIZ_CRITIQUE] Q9: duplicate correct mappings for spot 2 (multiple entries map to the same answer)
```

**Why it is wrong.** Mapping several solutions to one spot is a supported Artemis feature, not a defect:
`ShortAnswerQuestion.getCorrectSolutionForSpot` returns a `Set`, and
`ScoringStrategyShortAnswerUtil.getCorrectAndIncorrectSolutionCount` iterates every solution mapped to a
spot and stops at the first match — i.e. the mappings mean "any of these is accepted". It is the standard
way to accept a synonym or an alternative spelling.

**Prompt section that should have prevented it.** `critique_quiz_system.st` says nothing about
short-answer mappings at all. Criterion 3 ("are the incorrect options plausible and **non-duplicated**")
is about multiple-choice distractors, and the model generalised it to mappings.

**Impact if left.** Every quiz run whose source has a multi-solution spot burns at least one repair round
on a non-problem, inflating attempts-used and wall time across the whole quiz half for a reason unrelated
to the pipeline's real behaviour.

**Fix.** One rule added to `critique_quiz_system.st` stating the principle (a spot may have several
accepted solutions; report a mapping only when a spot has *no* accepted solution or a mapping refers to a
spot/solution that does not exist). Phrased as a principle rather than as a recital of this case, so it
generalises beyond this quiz.

---

## D2 — Re-theme misses short-answer question bodies

**Run:** `quiz-C3-r0`, VERIFYING attempt 1/5.

**Verbatim finding:**

```
[QUIZ_CRITIQUE] Q10: the short-answer prompt is not re-themed to the library returned books domain;
it still describes an OrderService and SmtpMailer unrelated to returned books.
```

**Status.** Genuine generation defect, caught by the gate and repaired within budget. Left open pending
more pilot runs: one occurrence does not establish whether short-answer bodies are systematically
under-transformed relative to multiple-choice ones, and the fix differs depending on which it is. To be
re-checked across the pilot subset before the freeze.

---

## D3 — Re-theme reaches signatures and identifiers, but not comments, Javadoc, or string literals

**Run:** `programming-C3-r0-serial` (job `7afc0cb2-d463-4675-b937-d1fcffb907d9`), terminal `COMPLETED`.

This run passed everything a machine checks: solution build green, template compiling and scoring 0 %,
consistency gate reporting 0 issues on the final attempt, and all three automated checks clean (13 task
references, none dangling; no stray `<testid>`; PlantUML present and unfenced). The re-theme was
genuinely deep — `List<Date>` became `List<Book>` through the statement, the diagram, the code, the
tests, *and* the `test.json` structure oracle, with a real `Book implements Comparable<Book>` ordering by
`returnDate`. Pattern-role names survived correctly.

It nevertheless ships source-domain vocabulary the student can see.

**Verbatim, from the stored artifacts:**

```java
// template and solution, BubbleSort.java
/**
 * Sorts dates with BubbleSort.
 * @param input the List of Dates to be sorted
 */
public void performSort(final List<Book> input) {

// template and solution, Client.java
System.out.print("Unsorted Array of course dates = ");
System.out.print("Sorted Array of course dates = ");
// TODO: Sort dates
```

"Course dates" belongs to no part of a library returned-books exercise, and the `Client` strings are
printed to the student's console.

**Prompt section that should have prevented it.** `transform_programming_system.st` instructs the
re-theme over identifiers and the data model, but never states that comments, Javadoc, and string
literals are part of the domain surface. The consistency gate did catch one identifier-level instance
(`DATES_SIZE_THRESHOLD`, attempt 2) and then missed the comments entirely, so gate coverage here is
partial and cannot be relied on to substitute for the instruction.

**Why it matters beyond tidiness.** It is invisible to every automated check by construction — comments
and string literals have no structural contract to violate. It is therefore exactly the class of defect
the rubric's *readiness for use* criterion exists to catch, and the reason a green build is not evidence
of a usable exercise.

**Prevalence, measured across all 30 stored programming pilot runs.** A scan for source-exercise
vocabulary that no re-theme should leave behind (`course dates`, `Sorts dates`, `List of Dates`,
`Array of course dates`, `TODO: Sort dates`) over the template, solution, and test repositories:

| config | request intent | runs with ≥1 leak | typical hits |
|---|---|---|---|
| C1 | `targetDifficulty: EASY` | 6/6 | 14 |
| C2 | `targetDifficulty: HARD` | 6/6 | 14 |
| C3 | `domainText: a library's returned books` | 3/6 | 1–14 |
| C4 | `domainText: a painter's colour palette` | 3/6 | 1 |
| C13 | `domainText: colour palette` + `IMAGINATIVE` | 2/6 | 6–9 |

**The first measurement corrected the defect's definition.** C1 and C2 request *only* a difficulty change
and no new domain, so "course dates" surviving there is not leakage — it is the correct and expected
output, and a re-theme would have been a bug. Counting them, as the original single-run entry implicitly
did, would have reported this defect at 22/30 instead of its true 8/18, inflating it roughly threefold and
pointing the fix at prompt text that is already behaving correctly for two of the five configurations.

**The defect, stated correctly.** Restricted to runs that actually requested a domain change (C3, C4,
C13): **8 of 18**. It is real, it is not rare, and it is invisible to every automated check — but it is a
property of domain-changing runs only.

**Secondary observation.** Severity splits by *where* the leak sits, not just whether one exists. C4's
leaks are a single hit — one stray comment. C3's and C13's reach 6–14, i.e. the printed `Client` strings a
student sees on the console. The report should count leaking runs and student-visible leaks separately;
a lone stale comment and a console banner reading "Unsorted Array of course dates" are not the same defect
severity, and pooling them would overstate the mild case and understate the serious one.

**Status.** Open, fix not yet applied. The candidate remains one clause in the transform prompt naming
comments, Javadoc, and user-visible strings as part of the re-theme surface — with the wording chosen
carefully, because the current text arguably *causes* the defect: "renaming is not satisfied by rewording
comments/Javadoc/strings **alone**" is read most naturally as "comments do not really count, identifiers
do". The fix is to state that both are required and neither substitutes for the other, not to add emphasis
to a sentence that already mentions comments. Deferred until after the casing comparison (round 8 vs
round 9) so the two changes are not confounded in the same round.

---

## D4 — Task-marker test references oscillate between valid and parenthesised forms

**Run:** `programming-C3-r0-serial`, verify attempts 2-5.

**Verbatim findings, successive attempts:**

```
attempt 2: [TEST_REFERENCES] ... do not exist: testBubbleSort(), testMergeSort(),
           testClass[Context], testClass[Policy]
attempt 3: [TEST_REFERENCES] ... do not exist: testClass[Context]
attempt 4: [TEST_REFERENCES] ... do not exist: testBubbleSort(), testMergeSort(),
           testUseMergeSortForBigList(), testUseBubbleSortForSmallList()
attempt 5: (passed)
```

The agent fixed `testClass[Context]` and simultaneously **reintroduced** the parenthesised form it had
already been corrected on. `testBubbleSort()` matches no test; the name is `testBubbleSort`.

**Prompt section that should have prevented it.** Both `plan_programming.st` (~line 174) and
`transform_programming_system.st` (~line 91) already warn about this: "Write the plain test name and
NEVER write a `<testid>` tag yourself". The warning is present and did not work. Neither says the
reference must match a name from `listTestCases` character for character.

**Impact.** The run converged only on attempt 5 of 5 — it consumed the entire repair budget on this
alone, despite having green builds from attempt 2. On a less lucky run this is a `DRAFT_WITH_WARNINGS`.
Dangling task references are in the freeze gate, so this must be fixed before the freeze.

**Root cause — the prompts taught the defect.** Both prompts used a worked example carrying exactly the
wrong spelling:

```
plan_programming.st:      "[task][Implement Bubble Sort](testBubbleSort())"
transform_programming_system.st: "[task][Implement Bubble Sort](testBubbleSort())"
```

The real name is `testBubbleSort`, as the source statement and `test-cases.json` both show. The model was
not ignoring the instruction — it was copying the example, which contradicted the prose rule it was there
to illustrate ("plain names only, exactly as listTestCases spells them"). That is why adding more warning
text would not have helped, and why the existing warning had no effect.

**Prevalence before the fix:** 2 of 5 programming pilot runs, and not marginally — `programming-C2-r1`
had 18 of 20 references dangling, having invented a whole naming scheme (`testDuplicateDates()`,
`testPolicyConfigure()`, `testSortStrategyInterface()`) that exists in no test repository.

**Fix applied.** The examples now show `testBubbleSort`, plus one added rule stating the reference must
match a `listTestCases` name character for character — no `()` suffix, no parameter list, and never a
name expected to exist rather than one that has been seen. The parenthesised spelling was removed even
from the *negative* examples (`<testid>testBubbleSort()</testid>`), since this model demonstrably copies
example spellings regardless of the framing around them.

**Not needed after all.** The fallback plan was a check in `updateProblemStatement` rejecting edits that
name unknown tests. Holding that back was the right call: the defect was a two-character error in a
worked example, and code would have papered over it.

**Correction — this entry claimed the defect was fixed before the evidence supported it.** The
parenthesised *format* did stop appearing, but the freeze gate's "no dangling task reference" criterion was
never met on any valid round, so "fixed" overstated it. Two things were missed:

1. The same defective example survived in Java, in the very gate that repairs dangling references
   (`ProgrammingVariantAdapters.checkTestReferences` suggested `testBubbleSort()`). Only the `.st` prompts
   were corrected. Now fixed — see D7.
2. The residual dangling references are a *different* defect from the format error: the model invents whole
   naming schemes (`testContextClass`, `testPolicyMethods`, `testSortStrategyInterface`, `testClass[Context]`)
   for tests it expects to exist. `testClass[Context]` is instructive — the source has
   `testAttributes[Context]` and `testMethods[Context]` but no `testClass[Context]`, so the model assumed the
   structure oracle names symmetrically. That is a symmetry assumption, not a spelling error, and it is
   mostly downstream of D7: when the template build fails, the gate that would have caught these never runs.

**Also fixed: a measurement bug this exposed.** `checks.py` parsed task markers with `\(([^)]*)\)`, which
stops at the first `)` and therefore truncated `testBubbleSort()` to `testBubbleSort(`. The dangling
verdict was still correct, but the names quoted as evidence were corrupted. Now anchored greedily to the
end of the line.

---

## D6 — Line-number gutters written into the problem statement (the work order's "known defect")

**Prevalence: 13 of 13 programming variants — every run, without exception.**

The work order names this defect up front ("generated problem statements contain numbers that do not
belong there") and asks which of four kinds they are, since the fix differs: copied `<testid>` numbers,
invented point values, task numbering, or hallucinated sizes. **It is none of them.**

**Verbatim, `programming-C3-r0-serial/problem-statement.md`:**

```
 1 | In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.
 2 | 
 3 | ### Part 1: Sorting
```

against the source, which begins:

```
In this exercise, we want to implement sorting algorithms and choose them based on runtime specific variables.
```

**Mechanism, traced end to end.** `HyperionProgrammingExerciseContextRendererService.renderFileString`
renders every file for the model with a line-number gutter (`num + " | " + line`, line 247). That is
correct for code — the agent needs line numbers to locate edits. But the *problem statement* is rendered
through the same path, and the planner reproduces the gutter as content. Confirmed in the stored plan
itself (`results-pilot/raw/programming-C4-r2.json`, PLANNING step output), so it originates in PLANNING,
not in the transform. From there it flows unaltered through
`ProgrammingVariantAdapters.setProblemStatement(plan.problemStatement())` into the variant, and students
would see the numbers and pipes.

**Prompt section that should have prevented it.** Neither `plan_programming.st` nor
`transform_programming_system.st` mentions the gutters at all. The model has no way to know the prefix is
a rendering artifact rather than text.

**Fix applied — two layers, the second being the one that actually settles it.**

1. A rule in each prompt stating that gutters are a reading aid and must never be written back. This
   reduced but did not eliminate the defect: it asks the model to reliably ignore something present in
   every line of its context, which is the weakest kind of instruction.

2. **The gutters are no longer rendered for variant generation at all.**
   `HyperionProgrammingExerciseContextRendererService.renderContext` gained a `withLineNumbers` overload,
   and `ProgrammingVariantAdapters` passes `false`. This was initially rejected as "shared infrastructure,
   do not touch" — that reasoning was wrong. The gutters exist for consumers that *report locations back*
   (the consistency check re-opens files at the reported line numbers to discard hallucinated issues).
   Variant generation edits by unique text match and never cites a line number, so for this consumer the
   gutters were never anything but token cost and a copying hazard. The parameter defaults to `true`, so
   every existing caller — and the consistency-check benchmark's reference scores — is untouched.

**Why this ordering matters for the report.** D6 is the clearest case in the pilot of a defect that looked
like a prompt problem and was actually a context-construction problem. No amount of prompt text makes a
model reliably ignore a prefix on every line it reads; removing the prefix makes the defect
unrepresentable. Prevalence went from 13/13 to 0.

**Fallback, now unnecessary.** A strip of `^\s*\d+ \| ` in the variants package, next to the existing
`stripPlantUmlCodeFences`. Not needed once the input can no longer contain gutters, and worth *not* adding:
a repair strip would also fire on correct output.

---

## D7 — A new test references student-owned classes at compile time, so the test suite runs zero tests

**Prevalence: `targetDifficulty: HARD` (C2) failed in every single round of the pilot — 7 of 7.** Dangling
task references per run, across all programming pilot runs, show how concentrated this is:

| config | intent | r1 | r2 | r3 | r4 | r5 | r7 | r8 |
|---|---|---|---|---|---|---|---|---|
| C1 | EASY | 3 | 2 | 0 | 1 | 0 | 0 | 1 |
| C2 | HARD | 18 | 11 | 6 | 4 | 10 | 3 | 11 |
| C3 | domain | – | 0 | 0 | 0 | 11 | 0 | 0 |
| C4 | domain | 0 | 0 | 0 | 11 | 4 | 0 | 0 |
| C13 | domain + IMAGINATIVE | 0 | 0 | 0 | 4 | 6 | 0 | 0 |

**Verbatim, round 8's C2 run (`1e3683b4`), template build:**

```
[ERROR] .../test/de/tum/cit/aet/sorting/ContextPolicyRobustnessTest.java:[15,9] cannot find symbol
  symbol:   class Context
[ERROR] .../test/de/tum/cit/aet/sorting/ContextPolicyRobustnessTest.java:[25,9] cannot find symbol
  symbol:   class Policy
```

and the resulting gate warning:

```
TEMPLATE_BUILD: The template build executed NO tests at all (0 of 0).
```

**Mechanism.** The test repository is compiled against the TEMPLATE. The source exercise splits its classes:

| ships in TEMPLATE (stub bodies) | SOLUTION-only (student writes from scratch) |
|---|---|
| `BubbleSort`, `MergeSort`, `Client` | `Context`, `Policy`, `SortStrategy` |

A test may therefore reference `BubbleSort` directly — the stub exists, and the test fails on its assertions,
which is the intended 0 %. It may reach `Context` only by reflection, which is exactly why the source's
structural tests use `ReflectionTestUtils` and a `test.json` oracle. A difficulty *increase* is the one
request that makes the model add tests, and the added tests instantiate student-owned classes. Domain
changes only rename existing tests, which is why they are unaffected — the split above is invisible unless
you go looking for it.

**Why this defect is expensive out of proportion to its size.** It fails the TEMPLATE_BUILD gate, and
Gate 2 (`checkTestReferences`) is deliberately skipped whenever Gate 1 has findings — correctly, since a
newly added test is not a real test case until the solution build compiles it, so checking early would
produce false findings. The consequence is that a run whose template build never goes green **never has its
task references checked at all** and ships as `DRAFT_WITH_WARNINGS` with the dangling references entirely
unreported. One compile error therefore hides a second, independent defect and consumes all five repair
attempts.

**Prompt section that should have prevented it — and did not.** `plan_programming.st` already carried the
rule ("A new test must never be written as a literal, compile-time reference to a class/type the template
does not implement"). It is correct and it did not bind: the plan for C2-r8 states verbatim

```
- test repository: add class `ContextPolicyRobustnessTest` with tests `testContextNoStrategy`
  (expects IllegalStateException when sort() called without strategy) and `testPolicyNullDates` ...
```

which cannot be written except as a compile-time reference. **The planner violated its own rule, and the
transformation agent faithfully implemented a bad plan.** This is the same shape as D4: the instruction was
present, prose-only, and ignored.

**Fix applied — turn the prohibition into an obligation.**
1. `plan_programming.st`: the planner must now first work out which classes the template ships (by comparing
   the template and solution trees) and then state, for *every* new test, which of two categories it is —
   exercising a template-shipped class (named explicitly) or exercising student work (reflection/oracle
   only). The rule also now says plainly that instantiating, calling, or catching an exception from student
   work is a compile-time reference however the assertions are phrased, which is the specific reasoning
   error the C2 plan made.
2. `transform_programming_system.st`: the same constraint added for the agent that actually writes the file,
   since the planner's rules are not visible to it — it sees only the plan.

**Also fixed: the gate's own repair hint taught D4's defect.**
`ProgrammingVariantAdapters.checkTestReferences` told the model to write
`"[task][Implement Bubble Sort](testBubbleSort())"` — the parenthesised form that resolves to nothing. D4
was recorded as fixed on the strength of the `.st` prompts being corrected; this hard-coded Java copy was
missed, so the gate that exists to repair dangling references was handing back an example that creates one.
Corrected, with the parenthesis-free contract stated explicitly.

---

## D8 — Invented test names survive every prose rule, because the tooling never closed the loop

**Prevalence: C2 in 8 of 8 rounds.** After D7's fix made C2's template build green, its failure moved from
`TEMPLATE_BUILD` to `TEST_REFERENCES` — the gate that a red build had been suppressing. The residual defect
stands alone and is precisely characterised: the agent *restyles* machine-generated names.

| written into the statement | the name that actually exists |
|---|---|
| `testSortStrategyInterface` | `testClass[SortStrategy]` |
| `testSortStrategyMethods` | `testMethods[SortStrategy]` |
| `testPolicyClass`, `testPolicyMethods` | `testAttributes[Policy]`, `testMethods[Policy]` |
| `testSelectBubbleSort` | `testUseBubbleSortForSmallList` |

Structure-oracle tests are generated per member as `test<Aspect>[ClassName]`; the names cannot be chosen,
and the set is not symmetric (`testMethods[Context]` exists, `testClass[Context]` does not). A name that
"reads better" is therefore always wrong.

**Why more prompt text was the wrong fix.** The rule was already present in
`transform_programming_system.st`, stated explicitly, *with the correct worked example*
(`[task][SortStrategy Interface](testClass[SortStrategy],testMethods[SortStrategy])`), plus "never restyle
one into a form that reads better" and "never assume a name exists because a similar one does — structural
tests are generated per member, so the set is not symmetric". It did not bind, across four consecutive
repair attempts in one run. This is the third instance in this pilot of a correct prose prohibition failing
(D4, D7, D8) and the second where making the *mechanism* enforce it worked where words did not.

**The tooling was teaching the defect and withholding the fix.** Three separate problems, all outside the
prompts:

1. `updateProblemStatement`'s own `@Tool` description — the text the agent reads at the exact moment it
   writes the statement — carried `"[task][Implement Bubble Sort](testBubbleSort())"`, the parenthesised
   form that resolves to nothing. D4 was recorded as fixed after correcting the `.st` prompts; this copy and
   the one in `checkTestReferences` were both missed. The defective example therefore appeared in *three*
   places and was removed from one.
2. The `TEST_REFERENCES` gate named the problem but not the remedy: "Call listTestCases to see what actually
   exists" requires the agent to volunteer a tool call before it can act. It now lists the available names
   inline.
3. Nothing checked references at the point of the mistake. `updateProblemStatement` saved a statement with
   unresolvable references and returned "Problem statement updated." The agent learned otherwise only after
   a full build round — and only when the build gate happened to be clean, which for C2 it never was.

**Fix applied — at the tool boundary rather than in the prompt.**
- `updateProblemStatement` now returns the unresolved references together with the complete set of real
  names. Deliberately a warning, not an error: the statement still saves, since a partially linked statement
  beats none and the agent may be mid-repair.
- `listTestCases` now frames its output as the complete, closed set that must be copied verbatim, and says
  that names are generated per member and not symmetric — instead of returning a bare list the agent could
  read as suggestions.
- The `@Tool` description and the gate hint both carry the correct, parenthesis-free form.

**The general lesson for the report.** Every one of these defects was an instruction the agent could only
follow by guessing correctly, in a system that had the ground truth available and did not show it. The
reliable fix in all three cases was to move the constraint from prose into the tool's contract — make the
wrong thing unrepresentable (D6: stop rendering gutters), make the right thing checkable at the point of
use (D8), or turn a prohibition into an obligation the agent must discharge explicitly (D7). Prompt text
was the least effective of the four mechanisms tried, despite being the one iterated on most.

---

## D9 — The reference gate validated an in-memory statement, reporting green on a variant that shipped 20 unresolvable references (code, not prompt)

**Severity: the highest found in the pilot.** Every other defect fails loudly. This one reports success.

**Run:** `programming-C2-r10`, terminal phase `COMPLETED`, no warnings, "All gates green" at attempt 3/5.

**Verified against the live server, not only the stored artifacts** — the variant exercise has 13 registered
test cases, and its persisted problem statement references twenty names that are not among them:

```
[task][SortStrategy Interface](testClassSortStrategy,testMethodsSortStrategy)
[task][Context Class](testClassContext,testMethodsContext)
[task][Policy Edge Cases](testConfigureExactlyThreshold,testConfigureNullDates,testConfigureEmptyList)
```

against the only names that exist: `testClass[SortStrategy]`, `testMethods[Context]`,
`testAttributes[Policy]`, … Every one of those tasks is silently unlinked from grading. The instructor
receives an exercise that builds, scores correctly, and grades nothing through its task list.

**Mechanism.** `ProgrammingExerciseTaskService.findUnresolvedTaskTestReferences` parses
`exercise.getProblemStatement()` from the object it is handed. `checkTestReferences` handed it the
in-memory `ProgrammingExercise` the pipeline had been carrying, so the gate validated whatever statement
that object held rather than the one persisted for the variant. The gate fired correctly at attempt 1 —
with the full list of bad names — and reported green at attempt 3 while the persisted statement still
contained them.

**Fix applied.** `checkTestReferences` now reloads the exercise from `ProgrammingExerciseRepository` before
checking, so the gate reads the same statement that ships. Paired with the tool-level check added for D8
(`updateProblemStatement` reporting unresolved references at write time), the constraint is now enforced at
both the point of the mistake and the point of hand-off.

**Why the evaluation caught it and the pipeline did not.** The freeze gate re-derives the check
independently, from the artifacts as captured, rather than trusting the terminal phase. That independence
is the only reason a false green was visible at all — and it is the concrete justification for the work
order's insistence that a succeeding build is not evidence of a usable exercise. Any reliability figure
computed from `terminal_phase` alone would have scored this run as a success.

**Consequence for the report's method section.** `COMPLETED` cannot be used as the sole success criterion.
The outcome tables must report the conjunction of terminal phase *and* the independent artifact checks, and
the gap between the two is itself a result worth stating.

---

## D5 — Answer explanations drift from their options, and a correctness flag contradicts the option

**Runs:** `quiz-C1-r1` and `quiz-C2-r1` (both `COMPLETED`, caught and repaired by the critique gate).

**Verbatim findings:**

```
[QUIZ_CRITIQUE] Q3: explanation for the 'Singleton' option does not correspond to the option text
                and mentions an unrelated pattern.
[QUIZ_CRITIQUE] Q5: explanation for the 'Use a global static factory method to create shapes' option
                incorrectly refers to the Builder pattern
[QUIZ_CRITIQUE] Q2: The option "Listeners are notified synchronously, which may cause performance
                bottlenecks" is factually correct but is marked as incorrect
```

Two distinct problems in one family. The first is **explanation drift**: when an option is re-themed or
rewritten, its `explanation` is not updated with it, so the justification describes a different option
than the one it is attached to. The second is the defect the work order names explicitly — a **correctness
flag contradicting the option text** — and it is the more serious of the two, because a variant that
ships it teaches the wrong thing while remaining structurally valid.

**Prompt section that should have prevented it.** `transform_quiz_system.st` governs option rewriting;
the critique's criterion 3 covers distractor quality but neither states that an option's `explanation`
and `isCorrect` flag must be re-derived whenever the option text changes.

**Status.** Open, and the highest-value quiz defect found so far: unlike D2/D3 it can make a variant
*wrong* rather than merely untidy. Both instances were caught by the gate and repaired within budget, so
the question for the freeze is not whether the gate catches it but how often it slips past.

---

## Not a prompt defect — corpus defects found by the pilot

Recorded here because the pilot found them, but fixed in the corpus rather than the prompts.

### C1 — Q9 accepted a solution its own stem excluded

**Verbatim finding:**

```
[QUIZ_CRITIQUE] Q9: includes an answer option "Strategy" that is not a class name from the provided
listing, violating the prompt and plan.
```

The gate was **right**. Q9's stem says "Using the class names from this listing", and `Strategy` is not in
the listing, but it had been added as an accepted solution to cover a student answering with the pattern
role. The two fixes contradicted each other. Resolved by removing the synonym: the explicit listing
reference already removes the ambiguity it was there to cover.

### C3 — Q10 accepted a broader answer than its stem asked for

Flagged in 3 of 6 quiz pilot runs:

```
[QUIZ_CRITIQUE] Q10: spot 2 incorrectly allows "dependency injection" as a correct solution;
                the plan specifies constructor injection
```

The same mistake as C1: the stem said "hand it in through the **constructor** instead", which points at
constructor injection specifically, while the accepted solutions also allowed the broader "dependency
injection". Fixed by rewording the stem to ask for the general technique ("supply it from outside
instead — the general two-word name for that technique is …"), which makes both accepted answers
genuinely correct rather than one of them contradicting the question.

Recorded because it is a *measurement* problem as much as a corpus one: at 3 of 6 runs it was a
systematic source of repair rounds attributable to the corpus rather than the pipeline.

### C4 — Q9's coupling to the programming source produces an ambiguous re-theme target

```
[QUIZ_CRITIQUE] C13 — Q9: The question and answer options were not re-themed; they still refer to the
                original class names (Context, SortStrategy, Policy)
```

Q9 embeds the source *programming* exercise's listing verbatim, and its answers are those class names.
Under a domain re-theme it is genuinely ambiguous whether they should change — they are the answer key,
and the identifiers are pattern roles that the programming half is required to preserve. The gate can
object either way.

Left as is, and reported rather than fixed: the coupling was a deliberate choice for cross-half
comparability (see `quiz_questions.py`), and this is the cost of it. The report must not treat a finding
that stems from this question as independent evidence about the pipeline.

### C2 — Short-answer mapping persistence (code, not prompt)

Not a prompt or corpus issue but found in the same pilot run, and it blocked the quiz half entirely; see
`README.md` → "Deviations from stock Artemis" for the fix and its rationale.

---

## Note on the uppercase-emphasis experiment (rounds 7-9)

The hypothesis — that shouted emphasis (`NEVER`, `ONLY`) reads worse to this model class than lowercase
with markdown bold — was tested three times and **is not answered by this evaluation**. The history matters
because the report must not claim otherwise:

- **Round 7** ran with emphasis lowercased and returned 0/5. That was attributed to the casing change. It
  was wrong: all five runs were Maven Central HTTP 429 failures, and round 7 is now fully quarantined as
  `INVALID_ENVIRONMENT`. The change was reverted on the strength of an artifact of the build environment.
- **Round 8** re-established an uppercase baseline on a fixed build environment: 3/5 COMPLETED, with both
  failures being the difficulty configurations (D7).
- **Round 9** was deliberately run with the casing change *bundled together with* the D7 rule changes,
  under time pressure and with the tradeoff stated in advance. It therefore measures the two jointly.

**Consequence for the report.** Any difference between rounds 8 and 9 is attributable to the D7 rules, the
casing change, or both, and this design cannot separate them. Since the D7 rules target a defect with a
measured 7/7 failure rate and a proven mechanism, while casing is a stylistic prior with no supporting
evidence collected here, the honest reading of an improvement is that the D7 rules did the work. The
casing change should be reported as "adopted as a prompt-writing convention, not validated" — not as a
result. Isolating it would need one further round changing casing alone.

**What was and was not lowercased.** Emphasis words only (`NEVER`, `ONLY`, `NOT`, `EVERY`, …). Left
uppercase deliberately: `TEMPLATE`, `SOLUTION`, `GIVEN`, `TODO`, `JSON`, and the enum-valued request fields
(`REALISTIC`, `IMAGINATIVE`) — these name the three repositories, a code category, a marker, a format, and
API values respectively, so lowercasing them would have changed meaning rather than tone and confounded the
comparison with a semantic edit.

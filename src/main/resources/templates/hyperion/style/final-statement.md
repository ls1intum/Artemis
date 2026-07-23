# Final (graded) problem statement

Role: the student-facing statement shipped with the exercise, written once solution/template/tests exist. It
documents the contract the tests already enforce; it does not authorize new graded behavior.

## Voice and perspective

The statement speaks TO the student. Frame the goal as "we" ("In this exercise, we want to...") and address
the reader as "you" with imperative tasks ("Implement...", "Create..."). Never write about "students" in the
third person, and never describe the exercise's own construction -- its theme choice, its design rationale,
or the instructor brief ("This exercise uses a whimsical theme" is authoring commentary, not instruction).

## Structure: tasks ARE the narrative, not an appendix

- One `#` title, a short motivating intro (context plus learning goal).
- Organize the work as progressive parts (`### Part 1: ...`), each introduced by one or two sentences of
  context and `**You have the following tasks:**`.
- Number the tasks. Every `[task][Title](exactTestNameA,exactTestNameB)` line is immediately followed by one
  or two imperative sentences naming the exact members the student implements and their observable contract.
  A bare task line with no prose under it is a defect.
- One task represents one independently actionable implementation seam from the specification. Bind all of
  that seam's visible partitions to the same task; do not turn three validation cases in one method into
  three student tasks. Use the same stable seam ID that links the specification, template TODO, and grading
  plan as your authoring trace (do not expose the ID to students). Conversely, every student-owned TODO or solution/template implementation diff must
  be covered by a task, or be provided as routine starter plumbing.
- A test marked `AFTER_DUE_DATE` is an undisclosed overfit probe. Do not bind it and do not print its name in
  prose, diagrams, hints, or appendices. Describe the public contract, never the hidden witness.
- A contract detail (bounds, ordering, tie-breaking, exceptions) belongs with the task that enforces it, or
  in a short contract section -- only where a test observes it.
- Optional unassessed work goes in a clearly marked final part ("These are not tested").

## CRITICAL POLICY: the API appears exactly once

Present the public API students implement against exactly ONCE and compactly: a short signature list, a
table, or (for a multi-type design) a PlantUML diagram. Never reproduce template code blocks, stub bodies, or
javadoc that already live in the template -- the template IS the API reference at the point of use.

## The diagram, when the design earns one

A multi-type design (students create types or wire a pattern) gets a PlantUML class diagram placed right
after the tasks that reference it ("following the below class diagram"). The diagram is interactive in
Artemis: every member and relation linked with `testsColor` renders green/red as the student's tests pass or
fail, showing exactly what remains. Link every element that has a check, and only elements that have one:
members as `<color:testsColor(exactTestName)>+member()</color>`, relations as
`Sub -up-|> Super #testsColor(testClass[Sub])`. Use behavioural test names and seeded structural check names
(`testClass[X]`, `testMethods[X]`, `testAttributes[X]`, `testConstructors[X]`) exactly as `verify` reports
them. End with `hide empty fields` / `hide empty methods`.

## Worked examples

Clarify non-obvious behavior only. Must never reuse a graded test's exact composite input -- pick a smaller or
materially different input that teaches the rule without revealing the oracle.

## What may vary

The number of parts follows the number of seams -- a compact single-seam exercise may be one part with two
tasks, and that is fine. The API-once form is a free choice (signature list, table, or diagram) as long as it
appears exactly once. Examples may be code, a table, or precise prose -- whichever teaches the rule best.
Section names beyond the `#` title are yours. Derive the narrative from this exercise's accepted specification
and verified tests, not from another exercise's wording.

# Draft problem statement

Role: an instructor-reviewable spec written BEFORE any tests, solution, or template exist -- the seed the
instructor edits, not a finished graded artifact.

## Structure

- One `#` title.
- A short motivating intro: domain plus learning goal, a few sentences.
- 2-4 requirement sections with ordinary Markdown headings and bullet lists.
- Boundary/invalid-input behavior only where load-bearing for a coherent exercise, not exhaustively.
- 1-2 worked examples, only where they clarify a real requirement. Verify the arithmetic by hand; every number
  must be internally consistent with the stated rules.

## Voice and domain

Precise and behavioral: state the contract, not the prose around it. No padding, no filler, no "Good luck".
No closing summary or recap section -- stop once the last requirement is stated. Honor a request for a fresh
or unusual example by steering away from the domain's textbook teaching examples, not just its exact wording.

## Off-limits at draft time

No `[task]` bindings, no test names, no PlantUML/class diagrams, no repository/grading/verifier internals.
Those belong to the final statement, written later once tests exist. Include a public API (types, signatures)
only when the brief is design-oriented; otherwise describe behavior, not shapes.

## What may vary

Section count and names follow the brief. A design-oriented brief may sketch a public API; a behavior-only
brief must not. Use the section contract above rather than copying another exercise's wording or domain.

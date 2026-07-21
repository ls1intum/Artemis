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
brief must not. The exemplar below is a shape, not a schema — never copy its topic or numbers.

## Exemplar

Tiny neutral domain: a coffee shop's loyalty program, deliberately distinct from the seeded reference exercise's domain.

```markdown
# Loyalty Points

A coffee shop credits loyalty points for purchases and lets members redeem them for discounts.

## Earning Points

Every purchase earns 1 point per whole dollar spent. A $12.75 purchase earns 12 points; a $0.40 purchase
earns 0 points.

## Redeeming Points

Redeeming 50 points reduces the current total by $5.00. A member with 130 points can redeem at most 2 times
(100 points) per purchase; the remaining 30 points carry over.

## Example

A member with 42 points makes a $30.00 purchase and tries to redeem once (50 points needed): insufficient, so
the purchase completes at $30.00 and earns 30 more points, leaving 72 points banked.
```

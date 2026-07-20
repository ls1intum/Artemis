# Final (graded) problem statement

Role: the student-facing statement shipped with the exercise, written once solution/template/tests exist. It
documents the contract the tests already enforce; it does not authorize new graded behavior.

## Structure

- One `#` title, a short motivating intro (context plus learning goal).
- A precise contract section: behavior, inputs/outputs, bounds, ordering, tie-breaking, mutation, exceptions
  -- only where a test observes them.
- `## Tasks` with one `[task][Title](exactTestNameA,exactTestNameB)` line per independently actionable student
  work seam. Copy test names verbatim from `verify`; group by seam, not by requirement sentence.

## CRITICAL POLICY: the API appears exactly once

Present the public API students implement against exactly ONCE and compactly: a short signature list, a
table, or (for a multi-type design) a PlantUML diagram. Never reproduce template code blocks, stub bodies, or
javadoc that already live in the template -- the template IS the API reference at the point of use. This
statement explains WHAT the contract is and WHY it exists, not a second copy of the template's code. A diagram
links members to checks with `<color:testsColor(exactTestName)>+member()</color>`; end with `hide empty
fields` / `hide empty methods`.

## Worked examples

Clarify non-obvious behavior only. Must never reuse a graded test's exact composite input -- pick a smaller or
materially different input that teaches the rule without revealing the oracle.

## Exemplar

```markdown
## Public API

| Member | Contract |
| --- | --- |
| `LoyaltyAccount.earn(double amount)` | Credits 1 point per whole dollar spent; no rounding up. |
| `LoyaltyAccount.redeem(int times)` | Reduces the total by $5.00 per successful redemption; throws
`IllegalStateException` if `times` exceeds the affordable count. |

## Tasks

[task][Credit points for a purchase](testEarnWholeDollarsOnly,testEarnZeroBelowOneDollar)
[task][Redeem banked points for a discount](testRedeemReducesTotal,testRedeemRejectsUnaffordableCount)

## Worked example

A member with 90 points redeeming once succeeds (50 points spent, 40 remain); redeeming twice throws, because
100 points exceed the 90 available. This is a smaller input than the tests use.
```

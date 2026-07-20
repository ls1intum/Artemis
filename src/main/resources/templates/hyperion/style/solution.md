# Solution (reference implementation)

Role: the graded reference implementation. It must compile and pass every behavioural test; graders diff it
against the template to find every intentional student task.

## Diff discipline

Solution = template + the student's work, nothing else. Javadoc and every non-TODO comment stay byte-identical
between template and solution; implementing a task replaces its `// TODO` line with code plus any
`implements`/imports it demands. Never author documentation only in the solution, never delete a template
comment while implementing.

## Code style

Idiomatic and minimal: the shortest correct implementation a competent instructor would accept, not a
showcase of every language feature. Add a comment only where it teaches something a reader could not infer
from the code itself (a non-obvious invariant, a rounding rule, why a boundary is inclusive) -- never comment
what the code already says plainly.

## Never reconcile to a wrong test or example

If a test assertion or a statement's worked example turns out to be wrong (inconsistent arithmetic, a
contradicted rule), fix the test or the example -- never bend the solution to match a flawed check. The
solution is the ground truth for "correct behavior given the stated contract".

## Exemplar

Same member as `style/template.md`'s exemplar; javadoc is untouched, only the TODO line is replaced:

```java
/**
 * Reduces the account's banked points by the redemption cost, one redemption at a time.
 *
 * @param times how many $5.00 redemptions to apply
 * @throws IllegalStateException if the account cannot afford {@code times} redemptions
 */
public void redeem(int times) {
    int cost = times * REDEMPTION_COST;
    if (cost > points) {
        throw new IllegalStateException("Insufficient points for " + times + " redemptions");
    }
    points -= cost;
}
```

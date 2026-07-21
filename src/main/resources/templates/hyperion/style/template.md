# Template (student starting point)

Role: what the student opens first. It must compile, fail every task-bound behavioural test at its intended
TODO, and teach the contract on its own — students work from it, using the statement only as reference.

## Javadoc

Every stubbed member carries complete javadoc (or the language's doc idiom) restating its student-visible
contract: purpose, parameters, return, error behavior. This javadoc is byte-identical between template and
solution (see `style/solution.md`); never leave a member undocumented because "the statement already says
it" — the template is where students actually read while coding.

## TODO placement

Anchor each task with an imperative `// TODO: <mirror of the task wording>` INSIDE the member body, directly
above the placeholder throw — never between the javadoc and the signature, never between an annotation and
the signature. One TODO per independently implementable seam. A TODO marks unfinished student work only:
never leave one on code that is already complete, and never leave authoring or design notes in any file.

## Breadcrumbs for types students must create

When the design says students define a type themselves, the template must NOT ship that type: omit its file,
keep the template compiling without it, and leave a TODO breadcrumb in the collaborating template file that
names the type, its essential shape, and where it plugs in. The breadcrumb is the student's only in-code
pointer to work that has no file yet — write it like you would want to find it.

## Data-holder plumbing

Provide routine constructors, fields, and accessors already implemented unless implementing them is itself an
explicit, tested learning objective. Keep student work focused on the stated objective, not boilerplate. Do
not pre-place fields or helpers the template itself never uses — unused scaffolding steers students toward a
design the tests may not even reward.

## Honesty

A stub fails the same way for every caller. Never inspect stack traces, test names, or any grading context to
change behavior.

## What may vary

The placeholder (`throw new UnsupportedOperationException("Not implemented")` is the Java default) follows
the language's idiom; a returned placeholder value is acceptable only if every test rejects it. TODO wording
mirrors YOUR tasks, not these examples. How much is stubbed versus given follows the design's template-status
column, not a fixed ratio.

## Exemplars (FORM only — never copy their topic, API, or design)

A stubbed member — javadoc complete, TODO in the body, placeholder throw:

```java
/**
 * Reduces the account's banked points by the redemption cost, one redemption at a time.
 *
 * @param times how many $5.00 redemptions to apply
 * @throws IllegalStateException if the account cannot afford {@code times} redemptions
 */
public void redeem(int times) {
    // TODO: apply times redemptions of REDEMPTION_COST points each, or throw IllegalStateException
    // if the account cannot afford all of them.
    throw new UnsupportedOperationException("Not implemented");
}
```

A breadcrumb in a collaborating file for a type the student creates (no RewardStrategy file exists in the
template; the solution alone ships it):

```java
public class LoyaltyAccount {

    // TODO: create a RewardStrategy interface with `int pointsFor(Purchase purchase)` and store the
    // active strategy here so record(...) can delegate to it.

    /**
     * Records a purchase, crediting points according to the active reward strategy.
     *
     * @param purchase the completed purchase
     */
    public void record(Purchase purchase) {
        // TODO: delegate to the active strategy and add its points to the balance.
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

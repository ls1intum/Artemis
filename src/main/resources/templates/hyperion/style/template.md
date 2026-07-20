# Template (student starting point)

Role: what the student opens first. It must compile, fail every task-bound behavioural test at its intended
TODO, and teach the contract on its own -- students work from it, using the statement only as reference.

## Javadoc

Every stubbed member carries complete javadoc (or the language's doc idiom) restating its student-visible
contract: purpose, parameters, return, error behavior. This javadoc is byte-identical between template and
solution (see `style/solution.md`); never leave a member undocumented because "the statement already says it".

## TODO placement

Anchor each task with an imperative `// TODO: <mirror of the task wording>` INSIDE the member body, directly
above the placeholder throw -- never between the javadoc and the signature, never between an annotation and
the signature. One TODO per independently implementable seam; do not stack unrelated TODOs in one body.

## Breadcrumbs for types students must create

When the requirements say students define or create a type themselves, the template must NOT ship that type:
omit its file, keep the template compiling without it, and leave a TODO breadcrumb in the collaborating
template file that names the type and its role, so the student knows what to build.

## Data-holder plumbing

Provide routine constructors, fields, and accessors already implemented unless implementing them is itself an
explicit, tested learning objective. Keep student work focused on the stated objective, not boilerplate.

## Honesty

A stub fails the same way for every caller. Never inspect stack traces, test names, or any grading context to
change behavior.

## Exemplar

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

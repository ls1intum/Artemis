# Template (student starting point)

Role: what the student opens first. It must compile, fail every task-bound behavioural test at its intended
TODO, and teach the contract on its own — students work from it, using the statement only as reference.

## Javadoc

Every stubbed member carries complete javadoc (or the language's doc idiom) restating its student-visible
contract: purpose, parameters, return, error behavior. This javadoc is byte-identical between template and
solution (see `style/solution.md`); never leave a member undocumented because "the statement already says
it" — the template is where students actually read while coding.

## TODO placement

Anchor each stubbed seam with an imperative `// TODO S1: <mirror of the task wording>` INSIDE its owner member body, using
the stable ID from the specification's Testing Strategy and placing it directly
above the placeholder throw — never between the javadoc and the signature, never between an annotation and
the signature. Every unfinished member of a stubbed owner carries its seam ID; the same ID may repeat within that owner when one task spans several members. A TODO marks unfinished student work only:
never leave one on code that is already complete, and never leave authoring or design notes in any file.

The sole exception is a stubbed owner's own seam whose members cannot be declared without an omitted student-created type. Keep that owner as an empty compile-safe class and put
exactly one owner-seam TODO in its class body where students add the members. Never restore the missing type, use `Object`, edit the specification, or reuse the absent type's seam.

## Types students must create

When the design says students define a type themselves, the template must NOT ship that type: omit its file and
keep the template compiling without it. Its statement task and reflective/structural tests are the truthful
anchors; do not attach that seam ID to unrelated collaborator code merely because the type has no template file.

When a provided context will eventually refer to an omitted student-created interface, keep the context class
but omit the student-owned field and methods whose signatures need that interface. Do not ship an empty interface merely to make those signatures compile, and do not
weaken only the template API to `Object`; use the class-body owner-seam TODO above, and let tests inspect the completed API reflectively.

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
    // TODO S3: apply times redemptions of REDEMPTION_COST points each, or throw IllegalStateException
    // if the account cannot afford all of them.
    throw new UnsupportedOperationException("Not implemented");
}
```

A stubbed collaboration seam in a provided owner (no RewardStrategy file exists in the template; the solution alone ships it):

```java
public class LoyaltyAccount {

    /**
     * Records a purchase, crediting points according to the active reward strategy.
     *
     * @param purchase the completed purchase
     */
    public void record(Purchase purchase) {
        // TODO S2: connect the student-created strategy and add its points to the balance.
        throw new UnsupportedOperationException("Not implemented");
    }
}
```

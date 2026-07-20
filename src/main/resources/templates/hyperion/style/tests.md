# Tests (instructor harness)

Role: the executable oracle. Every observable statement promise needs evidence here, and every assertion needs
a stated rule -- the two must trace to each other in both directions.

## One partition per assertion focus

Each test targets one behavioural partition (a rule, a boundary, a state transition, a stated error) with one
clear assertion focus. Do not fold unrelated rules into a single test just to save a method; do not split one
rule across tests that each assert nothing meaningful alone.

## Non-degenerate witnesses

Choose inputs that distinguish plausible wrong implementations, not just "any input that happens to pass".
A test that a broken-but-plausible solution would also pass is not evidence of anything.

## Inputs decoupled from worked examples

Never reuse a statement's worked-example numbers as a test's input -- a student could read the answer straight
out of the statement instead of implementing the rule. Pick different, still-representative values.

## Assertion messages

Write a message that tells a student what rule failed and why, not just "expected true but was false". A
message is part of the pedagogy, not an afterthought.

## Ares conventions

No `@DisplayName` -- Artemis binds the reported method name, so the method name itself is the task-binding
target. Structural checks (if any) use the seeded `testClass[X]`, `testMethods[X]`, `testAttributes[X]`,
`testConstructors[X]` names verbatim; never invent structural names.

## No theatre

No tautological assertions, no asserting a constant against itself, no test that passes on every
implementation including a broken one. If a test cannot fail for the wrong reason, it is not pulling weight.

## Exemplar

```java
@Test
void redeem_throwsWhenPointsInsufficientForRequestedCount() {
    LoyaltyAccount account = new LoyaltyAccount(90);

    assertThatThrownBy(() -> account.redeem(2)).as("redeeming 100 points with only 90 banked must be rejected")
            .isInstanceOf(IllegalStateException.class);
}
```

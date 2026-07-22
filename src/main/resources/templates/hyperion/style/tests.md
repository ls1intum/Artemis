# Tests (instructor harness)

Role: the executable oracle. Every observable statement promise needs evidence here, and every assertion needs
a stated rule — the two must trace to each other in both directions. Tests must pass on the solution and fail
on the template for their intended reason.

## One partition per assertion focus

Each test targets one behavioural partition (a rule, a boundary, a state transition, a stated error) with one
clear assertion focus. Do not fold unrelated rules into a single test just to save a method; do not split one
rule across tests that each assert nothing meaningful alone.

## Non-degenerate witnesses

Choose inputs that distinguish plausible wrong implementations, not just "any input that happens to pass".
Vary every input dimension a wrong implementation could ignore (if an argument should be irrelevant, prove it
by varying it; if order matters, pick inputs where the wrong order gives a different answer).

For delegation, callbacks, strategies, and similar collaborations, prefer a tiny fake or recording
implementation that returns a distinctive value and records its inputs. This proves the context really uses
the supplied abstraction; exercising only the known concrete implementations also accepts a context that
copies their logic, which defeats the learning objective while looking behaviorally correct.

If the collaborator interface is `student-creates` and therefore absent from the template, load it by name and
create the recording fake with `java.lang.reflect.Proxy`. Invoke constructors and methods whose signatures
mention that missing interface reflectively too. Merely storing an instance in an `Object` variable does not
make a normal call to a method expecting the missing interface compile against both repositories.

## Inputs decoupled from worked examples

Never reuse a statement's worked-example numbers as a test's input — a student could read the answer straight
out of the statement instead of implementing the rule. Pick different, still-representative values.

## Assertion discipline

Assert exception TYPES, never message strings, unless the statement itself fixes the exact message — a
message-string assertion turns wording into a graded contract nobody stated. Write assertion messages that
tell a student what rule failed and why; the message is part of the pedagogy.

## Harness conventions (Java/Ares)

Test classes follow the seeded harness: class-level `@Public`, `@WhitelistPath("target")`,
`@BlacklistPath("target/test-classes")`; each test carries `@StrictTimeout`. No `@DisplayName` — Artemis
binds the reported method name, so the method name itself is the task-binding target. Structural checks use
the seeded `testClass[X]` / `testMethods[X]` / `testAttributes[X]` / `testConstructors[X]` names verbatim;
never invent structural names. For a type that exists only in the solution, reach it reflectively (the
seeded `reference/tests` shows the working pattern with `ReflectionTestUtils`) so the same test still
compiles against the template.

## No theatre

No tautological assertions, no asserting a constant against itself, no test that passes on every
implementation including a broken one. If a test cannot fail for the wrong reason, it is not pulling weight.

## The grading plan (test-plan.json)

Once the differential is green, write `/workspace/test-plan.json` implementing the spec's Testing Strategy:
`{"tests":[{"name":"<exact test name>","weight":1..3,"visibility":"ALWAYS"|"AFTER_DUE_DATE"}]}`. Weights say
what the exercise is really about — the core rule outweighs edge polish; equal weights everywhere is a
decision too, and usually a lazy one. Mark a partition's HIDDEN variant `AFTER_DUE_DATE` and give it fresh
witness values (never the visible test's inputs renamed): its whole point is catching a solution overfitted
to the visible tests. Hidden tests are never bound to `[task]` lines in the statement. Names must be the
exact names `verify` reports, copied verbatim.

## What may vary

Test count follows the design's partitions, not a quota. Non-Java languages keep the same rules with their
own framework's idiom. Shared fixtures/helpers are fine when they keep each test's focus readable.

## Exemplars (FORM only — never copy their topic, API, or design)

A stated-error partition — exception type, not message, with a teaching assertion message:

```java
@Test
@StrictTimeout(2)
void redeem_throwsWhenPointsInsufficientForRequestedCount() {
    LoyaltyAccount account = new LoyaltyAccount(90);

    assertThatThrownBy(() -> account.redeem(2)).as("redeeming 100 points with only 90 banked must be rejected")
            .isInstanceOf(IllegalStateException.class);
}
```

A boundary partition with a non-degenerate witness (the sub-dollar case a truncation bug would get wrong):

```java
@Test
@StrictTimeout(2)
void earn_subDollarPurchaseEarnsZeroPoints() {
    LoyaltyAccount account = new LoyaltyAccount(0);

    account.earn(0.99);

    assertThat(account.getPoints()).as("points credit whole dollars only, so $0.99 must earn 0 points").isZero();
}
```

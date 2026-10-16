# Tests (instructor harness)

Role: the executable oracle. Every observable statement promise needs evidence here, and every assertion needs
a stated rule — the two must trace to each other in both directions. Assessment tests pass on the solution
and fail on the starter at the assigned work. Preservation checks protect supplied behavior, pass on both
repositories, and earn no credit.

## One partition per assertion focus

Each test targets one behavioural partition (a rule, a boundary, a state transition, a stated error) with one
clear assertion focus. Do not fold unrelated rules into a single test just to save a method; do not split one
rule across tests that each assert nothing meaningful alone.

## Non-degenerate witnesses

Choose inputs that distinguish plausible wrong implementations, not just "any input that happens to pass".
Vary input dimensions independently: competing order/tie rules must disagree on the expected winner, not
select the same one. If forwarding an argument or calling exactly once is promised, observe the actual
argument or call count; equal final state from an argument-ignoring or idempotent collaborator proves neither.
For object-local state, interleave two instances before observing both. When the contract promises reference
identity, use equal-but-distinct objects and identity assertions; equality alone does not distinguish them.
Do not add these requirements when the contract does not promise them.

For delegation, callbacks, strategies, and similar collaborations, prefer a tiny fake or recording
implementation that returns a distinctive value and records its inputs. This proves the context really uses
the supplied abstraction; exercising only the known concrete implementations also accepts a context that
copies their logic, which defeats the learning objective while looking behaviorally correct.

If the collaborator interface is `student-creates` and therefore absent from the template, load it by name and
create the recording fake with `java.lang.reflect.Proxy`. Invoke constructors and methods whose signatures
mention that missing interface reflectively too. Merely storing an instance in an `Object` variable does not
make a normal call to a method expecting the missing interface compile against both repositories.
`ReflectionTestUtils.newInstance(className, arguments...)` infers exact runtime classes. It therefore does not
match an approved constructor declared with an interface or supertype when an argument is a concrete
implementation. Resolve the declared signature explicitly, then instantiate it:
`newInstance(getConstructor(getClazz(ownerName), List.class, getClazz(collaboratorName)), list, proxy)`.
Never add concrete collection or implementation overloads to production code just to satisfy the harness.

## Inputs decoupled from worked examples

Never reuse a statement's worked-example numbers as a test's input — a student could read the answer straight
out of the statement instead of implementing the rule. Pick different, still-representative values.

## Assertion discipline

Each test must establish its own state. Mutable static fields and registries survive between test methods;
`@BeforeAll` resets them only once per class, not once per scenario. If the contract needs a fresh shared state,
use an instructor-owned `@BeforeEach` fixture for each test. Keep reset machinery in the test harness rather
than adding reset methods to the exercise API. Do not assume counters start at zero or registries are empty
because a different test ran first. Verify the whole suite, not only individual test methods.

Assert exception TYPES, never message strings, unless the statement itself fixes the exact message — a
message-string assertion turns wording into a graded contract nobody stated. Write assertion messages that
tell a student what rule failed and why; the message is part of the pedagogy.

## Harness conventions (Java/Ares)

For the current Ares 2 harness, use `org.junit.jupiter.api.Test`, `de.tum.cit.ase.ares.api.jupiter.Public`,
`de.tum.cit.ase.ares.api.Policy`, and `de.tum.cit.ase.ares.api.StrictTimeout`. Keep an explicit
`@Policy(value = "SecurityPolicy.yaml")` on every test class. Update only the policy's instructor-test
class list to match the test source files; never change its supervised package or resource permissions.
Use `import static de.tum.cit.ase.ares.api.util.ReflectionTestUtils.*;` for reflection helpers.
Resolve declared signatures with `getClazz`, `getConstructor`, and `getMethod`; cast the Object returned by
`invokeMethod` to the declared return type. For `assertThrows`, use `invokeMethodRethrowing(instance,
getMethod(instance, "withdraw", double.class), amount)` or `newInstanceRethrowing(constructor, arguments...)`.
The non-rethrowing helpers turn invocation failures into assertion failures, not the student's exception.
Reflection belongs only in instructor tests. Never import JUnit or Ares into solution/ or template/ or use
reflective lookup there to evade references to absent student-created classes. Given starter code must compile
without those classes; learner-owned methods can remain TODO bodies.

For an existing Ares 1 harness, keep the seeded path annotations and imports instead of migrating the harness.
Use JUnit `DisplayNameGeneration` with `DisplayNameGenerator.Simple.class` so reports retain method names.
Use these supported imports and helper signatures. For a compiler diagnostic, inspect the named generated source,
not dependency internals. The sandbox tools do not permit dependency-cache or JAR inspection.

`AFTER_DUE_DATE` is Artemis visibility metadata in `test-plan.json`, not an Ares `@Hidden` or
`@Deadline` annotation. Use the same executable test annotations for visible and after-due-date
witnesses; both must run during verification and normal grading. Artemis controls feedback visibility.

Test classes follow the seeded harness and security policy; each test carries `@StrictTimeout`. No `@DisplayName` — Artemis
binds the reported method name, so the method name itself is the task-binding target. Structural checks use
the seeded `testClass[X]` / `testMethods[X]` / `testAttributes[X]` / `testConstructors[X]` names verbatim;
never invent structural names. For a type that exists only in the solution, reach it reflectively (the
explicit declared-signature pattern above uses `ReflectionTestUtils`) so the same test still
compiles against the template.

## No theatre

No tautological assertions, no asserting a constant against itself, no test that passes on every
implementation including a broken one. If a test cannot fail for the wrong reason, it is not pulling weight.

## The grading plan (test-plan.json)

Before running the differential, write `/workspace/test-plan.json` implementing the spec's Testing Strategy:
`{"tests":[{"name":"<exact test name>","seam":"S1","seamWeightTier":1..3,"visibility":"ALWAYS"|"AFTER_DUE_DATE"}]}`.
Purpose defaults to `ASSESSMENT`. For a check solely of supplied behavior, use
`{"name":"<exact test name>","purpose":"PRESERVATION","seamWeightTier":0,"visibility":"ALWAYS"}`.
Preservation entries have no seam or riskPartitions, cannot cover student-work partitions, and never bind
to a `[task]`. They remain visible so a learner who breaks supplied behavior receives useful feedback.

For an existing method students modify, first write separate preservation checks for its already-working
cases (including a successful case and any guard the change must retain). Call that supplied method directly;
do not depend on a member students still have to create. Keep these checks passing on the actual old body.
Then write assessment tests for the new behavior that distinguishes the completed method from that body.
An unchanged guard does not become new student work merely because it is inside a modified method: its
standalone regression test is PRESERVATION, not ASSESSMENT. Never erase the old body to make such a test fail.
The final suite must reject both an unchanged old implementation (missing the extension) and a placeholder
that discards its working behavior. Do not create a graded task for leaving supplied code untouched.

List every agent-authored behavioral test. Do not list build gates or server-seeded structural checks; Artemis keeps seeded structural checks visible and zero-weight.
The seam is the stable ID from the specification row this test implements. Start with the highest-risk
learning-objective seam (for a design pattern, prove delegation with a recording fake before concrete formulas). Weights say
what the exercise is really about — the core rule outweighs edge polish; equal weights everywhere is a
decision too, and usually a lazy one. Mark a partition's HIDDEN variant `AFTER_DUE_DATE` and give it fresh
witness values (never the visible test's inputs renamed): its whole point is catching a solution overfitted
to the visible tests. Hidden tests are never bound to `[task]` lines in the statement. Names must be the
exact names `verify` reports, copied verbatim.

## What may vary

Test count follows the design's partitions, not a quota. Non-Java languages keep the same rules with their
own framework's idiom. Shared fixtures/helpers are fine when they keep each test's focus readable.

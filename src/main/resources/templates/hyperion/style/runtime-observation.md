# Assessing API use through one learner execution

Use this pattern when the learner creates local objects and a later operation erases an earlier state.
Fixed output cannot establish that those operations happened: printing the same literals bypasses the work.
Tests that perform the operations themselves establish only that supplied code works.

Choose observation support before freezing Public API and Testing Strategy. Supplied constructors and methods
record actual state automatically. Learners make only the ordinary API calls assigned by the brief.
The recorder neither performs those actions nor accepts learner-supplied claims that they happened.

## A supported pattern

Suppose the assigned behavior is to increment a counter and then reset it in one entry-point execution.
The following is supplied support, identical in starter and solution; it is not additional learner work:

```java
public class Counter {
    private static Counter observed;
    private int value;
    private final java.util.List<Integer> history = new java.util.ArrayList<>();

    public Counter() { observed = this; }
    public void increment() { value++; history.add(value); }
    public void reset() { value = 0; history.add(value); }
    public int value() { return value; }

    // Supplied instructor observation, not calls required from the learner.
    static void clearObservation() { observed = null; }
    static Counter observed() { return observed; }
    java.util.List<Integer> history() { return java.util.List.copyOf(history); }
}
```

The learner entry point constructs a counter, calls `increment`, then calls `reset`. Its final value is zero.
An empty entry point or a counter constructed and left untouched has the same final value, but lacks the history.
An instructor test resets observation BEFORE calling the learner, then observes that very execution:

```java
Counter.clearObservation();
Client.main(new String[0]);
Counter actual = Counter.observed();
assertNotNull(actual);
assertEquals(java.util.List.of(1, 0), actual.history());
assertEquals(0, actual.value());
```

Use the instructor annotations and policy from `reference/style/tests.md`.
For this declared sequence, the reference passes; empty, print-only, omitted-increment and omitted-reset
implementations fail. The integer snapshot after increment stays 1 even after reset. Retaining only a mutable
object reference would lose that evidence. For linked objects, retain identity separately from immutable
snapshots of each end at the required transitions; observations from unrelated executions cannot be combined.
Do not impose incidental allocation or call counts, or copy this topic into the exercise. Record only what
is necessary to distinguish the brief's required actions. Keep instrumentation hidden from learner tasks,
while declaring its mechanism and ownership in the specification.

## Hidden variants must be executable

For every input variant, declare the exact test-controlled change, the learner entry point it reaches, and the
expected result derived from that change. Hidden tests may also add compatible assertions on the same execution. Two identical deterministic invocations cannot be required to
produce different outputs. Changing expected names without changing inputs or supplied configuration is
an inconsistent test, not a hidden variant. Running the same main twice is not input variation.

A fixed-input demonstration can still be graded through its actual recorded state transitions. Do not
invent inputs, require new learner callbacks, or duplicate the scenario in a separate helper to manufacture
variation. If no faithful hidden variant exists, mark it `no`. A transcript check may supplement runtime
assessment; it cannot establish API use by itself.

# Assessing learner-written JUnit tests

Use this reference when the learner chooses test cases and writes assertions against a supplied API.
A weak grader only runs those tests against the correct implementation: empty bodies pass too.
A sound grader establishes both that the tests accept correct behavior and that their assertions reject representative faults.

Keep the learner's reasoning intact. Do not supply the cases or assertions that the brief asks the learner to choose or write.
Choosing constants to satisfy already-written scenarios is a different task.
Fault selection and interpretation belong in supplied instructor support, not in learner callbacks, recorders, or test doubles.

## A supported Java pattern

This small example demonstrates the grading mechanism, not an exercise topic or a complete test suite.
Choose the real cases and faults from the instructor brief and approved contract.

The supplied API is identical in starter and solution. Its instructor-controlled fault switch defaults to correct behavior:

```java
public class Toolbox {
    private static boolean faulty;

    public static int sum(int a, int b) {
        return faulty ? 0 : a + b;
    }
}
```

A supplied learner class and method signature can keep the starter compilable while leaving the whole assertion body to the learner:

```java
public class LearnerCheck {
    @org.junit.jupiter.api.Test
    public void verifiesSum() {
        // Learner chooses cases and writes assertions here.
    }
}
```

The reference implements that body with ordinary JUnit assertions, for example `assertEquals(5, Toolbox.sum(2, 3))`.
The learner never selects a fault. Instructor tests run the same learner method first against correct behavior and then against a fault:

```java
@Test
void learnerAssertionsDetectFaultyBehavior() throws Exception {
    var faulty = Toolbox.class.getDeclaredField("faulty");
    faulty.setAccessible(true);
    faulty.setBoolean(null, false);
    try {
        assertDoesNotThrow(() -> new LearnerCheck().verifiesSum());
        faulty.setBoolean(null, true);
        assertThrows(AssertionError.class, () -> new LearnerCheck().verifiesSum());
    }
    finally {
        faulty.setBoolean(null, false);
    }
}
```

Use the ordinary instructor-test annotations and policy from `reference/style/tests.md`.
If the learner must create the entire test class, preserve that responsibility and use the documented Ares reflection helpers instead of a compile-time reference to the absent class.
Unwrap reflective invocation so the grader observes the learner's `AssertionError`, not `InvocationTargetException` or a helper error.

The public learner API is still `Toolbox.sum`; the private switch is supplied assessment support.
Declare the actual fault-selection mechanism in the specification before freezing it. A wrapper around a static method does not redirect direct calls already compiled into learner tests.
No private JDK fields, attached agents, class rewriting, extra dependencies, or new sandbox permissions are needed.

An empty learner body passes the correct-behavior run but fails the instructor's fault-detection check.
A body that catches its own assertion failures fails that check too.
A grader that merely counts calls or recorded values cannot establish that the assertions detect bugs.
Fault activation, class discovery and instructor setup errors are grader defects; they must never count as a detected fault.
Restore fault state in `finally`, isolate scenarios, and cover the brief's distinct risk partitions rather than copying this one arithmetic witness.

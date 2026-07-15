package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationOutcome;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

/**
 * Deterministic end-to-end test of agentic exercise generation. The LLM is scripted, but the agent loop, Docker sandbox, structured verify tool, and differential verifier are
 * real.
 */
@EnabledIf("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.HyperionMockedLlmE2eSupport#isDockerAvailable")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class HyperionExerciseGenerationMockedEndToEndTest extends AbstractHyperionMockedLlmEndToEndTest {

    private static final Logger log = LoggerFactory.getLogger(HyperionExerciseGenerationMockedEndToEndTest.class);

    private static final String TEST_PREFIX = "hypgenmock";

    private static final String PROBLEM_STATEMENT = """
            # Bounded Counter

            Implement `BoundedCounter`, a small counter with a configurable positive maximum.

            - A new counter starts at 0.
            - `increment()` increases the value by one until the maximum is reached, then keeps it at the maximum.
            - `decrement()` decreases the value by one until 0 is reached, then keeps it at 0.
            - Constructing a counter with a maximum below 1 throws `IllegalArgumentException`.

            ```text
            new BoundedCounter(2), increment(), increment(), increment() -> getValue() == 2
            new BoundedCounter(3), decrement() -> getValue() == 0
            new BoundedCounter(0) -> IllegalArgumentException
            ```

            [task][Start at zero](startsAtZeroAndExposesValue)
            [task][Increment up to maximum](incrementsUntilMaximum)
            [task][Decrement down to zero](decrementNeverDropsBelowZero)
            [task][Reject invalid maximum](rejectsNonPositiveMaximum)
            """;

    private static final String PROBLEM_STATEMENT_WITH_BAD_BINDING = PROBLEM_STATEMENT.replace("(incrementsUntilMaximum)", "(incrementAtUpperBound)");

    private static final String SOLUTION_BOUNDED_COUNTER = """
            package de.test;

            public class BoundedCounter {

                private final int max;
                private int value;

                public BoundedCounter(int max) {
                    if (max < 1) {
                        throw new IllegalArgumentException("max must be positive");
                    }
                    this.max = max;
                }

                public int getValue() {
                    return value;
                }

                public void increment() {
                    if (value < max) {
                        value++;
                    }
                }

                public void decrement() {
                    if (value > 0) {
                        value--;
                    }
                }
            }
            """;

    private static final String BROKEN_SOLUTION_BOUNDED_COUNTER = SOLUTION_BOUNDED_COUNTER.replace("value++;", "value--;");

    private static final String TEMPLATE_BOUNDED_COUNTER = """
            package de.test;

            public class BoundedCounter {

                public BoundedCounter(int max) {
                }

                public int getValue() {
                    return -1;
                }

                public void increment() {
                }

                public void decrement() {
                }
            }
            """;

    private static final String BOUNDED_COUNTER_TEST = """
            package de.test;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertThrows;

            import org.junit.jupiter.api.Test;

            import de.tum.in.test.api.BlacklistPath;
            import de.tum.in.test.api.StrictTimeout;
            import de.tum.in.test.api.WhitelistPath;
            import de.tum.in.test.api.jupiter.Public;

            @Public
            @WhitelistPath("target")
            @BlacklistPath("target/test-classes")
            class BoundedCounterTest {

                @Test
                @StrictTimeout(1)
                void startsAtZeroAndExposesValue() {
                    assertEquals(0, new BoundedCounter(3).getValue(), "A new counter must start at zero.");
                }

                @Test
                @StrictTimeout(1)
                void incrementsUntilMaximum() {
                    BoundedCounter counter = new BoundedCounter(2);
                    counter.increment();
                    assertEquals(1, counter.getValue(), "increment must increase the value by exactly one.");
                    counter.increment();
                    assertEquals(2, counter.getValue(), "increment must reach the configured maximum one step at a time.");
                    counter.increment();
                    assertEquals(2, counter.getValue(), "increment must clamp the value at the configured maximum.");
                }

                @Test
                @StrictTimeout(1)
                void decrementNeverDropsBelowZero() {
                    BoundedCounter counter = new BoundedCounter(3);
                    counter.increment();
                    counter.increment();
                    counter.decrement();
                    assertEquals(1, counter.getValue(), "decrement must reduce a positive value by exactly one.");
                    counter.decrement();
                    assertEquals(0, counter.getValue(), "decrement must reach zero one step at a time.");
                    counter.decrement();
                    assertEquals(0, counter.getValue(), "decrement must keep the value at zero when the counter is already empty.");
                }

                @Test
                @StrictTimeout(1)
                void rejectsNonPositiveMaximum() {
                    assertThrows(IllegalArgumentException.class, () -> new BoundedCounter(0), "max <= 0 must be rejected.");
                }
            }
            """;

    private static final String SOLUTION_PATH = "solution/src/de/test/BoundedCounter.java";

    private static final String TEMPLATE_PATH = "template/src/de/test/BoundedCounter.java";

    private static final String TEST_PATH = "tests/test/de/test/BoundedCounterTest.java";

    private static final String PROBLEM_STATEMENT_PATH = "problem-statement.md";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generatesValidExercise_deterministic_endToEnd() throws Exception {
        ProgrammingExercise exercise = scaffoldEmptyJavaExercise("HGMOK");
        when(azureOpenAiChatModel.call(any(Prompt.class))).thenReturn(HyperionMockedLlmE2eSupport.writeFile(PROBLEM_STATEMENT_PATH, PROBLEM_STATEMENT),
                HyperionMockedLlmE2eSupport.writeFile(SOLUTION_PATH, SOLUTION_BOUNDED_COUNTER), HyperionMockedLlmE2eSupport.writeFile(TEMPLATE_PATH, TEMPLATE_BOUNDED_COUNTER),
                HyperionMockedLlmE2eSupport.writeFile(TEST_PATH, BOUNDED_COUNTER_TEST), HyperionMockedLlmE2eSupport.verify(), HyperionMockedLlmE2eSupport.submit("Bounded counter"),
                HyperionMockedLlmE2eSupport.cleanQualityReview(), HyperionMockedLlmE2eSupport.cleanQualityReview());

        try (GenerationOutcome outcome = orchestrator.generate(exercise, instructor(), "Create a bounded counter exercise.", "mock-generate-valid", GenerationMode.GENERATE,
                () -> false, line -> log.info("[mock-generate] {}", line), null, null)) {
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            log.info("=== VERIFICATION (valid) ===\n{}", outcome.verification().report());
            assertThat(outcome.verification().solutionPassed()).as("the solution passes its own tests").isTrue();
            assertThat(outcome.verification().templateFailed()).as("the template compiles but fails the tests").isTrue();
            assertThat(outcome.verification().testCount()).as("all four behavior tests were discovered").isEqualTo(4);
            assertThat(outcome.isAccepted()).as("the differential oracle accepts the exercise").isTrue();
            assertThat(outcome.specFidelityReport().hasFindings()).as("the accepted mocked exercise has no deterministic quality findings").isFalse();
            assertThat(outcome.producedProblemStatement()).contains("[task][Start at zero](startsAtZeroAndExposesValue)", "[task][Increment up to maximum](incrementsUntilMaximum)",
                    "[task][Decrement down to zero](decrementNeverDropsBelowZero)", "[task][Reject invalid maximum](rejectsNonPositiveMaximum)");
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION).keySet()).anyMatch(path -> path.endsWith("BoundedCounter.java"));
            assertThat(outcome.producedFiles(RepositoryType.TESTS).keySet()).anyMatch(path -> path.endsWith("BoundedCounterTest.java"));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rejectsExerciseWithUnresolvedTaskBinding_deterministic_endToEnd() throws Exception {
        ProgrammingExercise exercise = scaffoldEmptyJavaExercise("HGMBAD");
        when(azureOpenAiChatModel.call(any(Prompt.class))).thenReturn(HyperionMockedLlmE2eSupport.writeFile(PROBLEM_STATEMENT_PATH, PROBLEM_STATEMENT_WITH_BAD_BINDING),
                HyperionMockedLlmE2eSupport.writeFile(SOLUTION_PATH, SOLUTION_BOUNDED_COUNTER), HyperionMockedLlmE2eSupport.writeFile(TEMPLATE_PATH, TEMPLATE_BOUNDED_COUNTER),
                HyperionMockedLlmE2eSupport.writeFile(TEST_PATH, BOUNDED_COUNTER_TEST), HyperionMockedLlmE2eSupport.submit("Bounded counter with one wrong binding"));

        try (GenerationOutcome outcome = orchestrator.generate(exercise, instructor(), "Create a bounded counter exercise.", "mock-generate-bad", GenerationMode.GENERATE,
                () -> false, line -> log.info("[mock-generate-bad] {}", line), null, null)) {
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            log.info("=== VERIFICATION (bad binding) ===\n{}", outcome.verification().report());
            assertThat(outcome.verification().solutionPassed()).as("the code is otherwise valid").isTrue();
            assertThat(outcome.verification().templateFailed()).as("the template still fails the tests").isTrue();
            assertThat(outcome.isAccepted()).as("unresolved task bindings are rejected even when builds pass").isFalse();
            assertThat(outcome.verification().reasons()).anyMatch(reason -> reason.contains("match no actual test") && reason.contains("incrementAtUpperBound"));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rejectsExerciseWhoseSolutionFailsItsOwnTests_deterministic_endToEnd() throws Exception {
        ProgrammingExercise exercise = scaffoldEmptyJavaExercise("HGMFAIL");
        when(azureOpenAiChatModel.call(any(Prompt.class))).thenReturn(HyperionMockedLlmE2eSupport.writeFile(PROBLEM_STATEMENT_PATH, PROBLEM_STATEMENT),
                HyperionMockedLlmE2eSupport.writeFile(SOLUTION_PATH, BROKEN_SOLUTION_BOUNDED_COUNTER),
                HyperionMockedLlmE2eSupport.writeFile(TEMPLATE_PATH, TEMPLATE_BOUNDED_COUNTER), HyperionMockedLlmE2eSupport.writeFile(TEST_PATH, BOUNDED_COUNTER_TEST),
                HyperionMockedLlmE2eSupport.verify(), HyperionMockedLlmE2eSupport.submit("Broken bounded counter"));

        try (GenerationOutcome outcome = orchestrator.generate(exercise, instructor(), "Create a bounded counter exercise.", "mock-generate-failing-solution",
                GenerationMode.GENERATE, () -> false, line -> log.info("[mock-generate-failing] {}", line), null, null)) {
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            log.info("=== VERIFICATION (failing solution) ===\n{}", outcome.verification().report());
            assertThat(outcome.verification().solutionPassed()).as("a broken reference solution is rejected").isFalse();
            assertThat(outcome.verification().testCount()).as("the verifier still discovered the behavior tests").isEqualTo(4);
            assertThat(outcome.isAccepted()).as("the differential oracle rejects exercises whose solution fails").isFalse();
        }
    }

    private ProgrammingExercise scaffoldEmptyJavaExercise(String shortName) throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course,
                ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);
        exercise.setShortName(shortName);
        exercise.setTitle("Hyperion Mocked E2E " + shortName);
        exercise.setChannelName("hyp-mock-" + shortName.toLowerCase());
        return useOfflineMavenPluginVersions(creationService.createProgrammingExercise(exercise, true));
    }

    private User instructor() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }
}

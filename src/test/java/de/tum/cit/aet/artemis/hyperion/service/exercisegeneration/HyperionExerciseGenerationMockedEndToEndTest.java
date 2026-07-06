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
 * Deterministic, committed end-to-end test of agentic exercise generation. It replaces the live-GPU {@code HyperionExerciseGenerationEndToEndTest} for CI: instead of an external
 * LLM, a mocked {@code azureOpenAiChatModel} returns a fixed sequence of tool-calling turns that drive the real agent loop, the real Docker sandbox, and the real differential
 * oracle. Only the model is faked; the container build and the acceptance verdict are authentic. Docker-gated (not GPU-gated).
 * <p>
 * The fixture is a minimal-but-real Java {@code Calculator}: the solution passes its one JUnit test, the template compiles but fails it (a {@code return 0} stub), and the problem
 * statement binds the test with a {@code [task]}. Two scenarios:
 * <ul>
 * <li>a valid exercise the oracle must accept (solution passes, template fails, at least one test);</li>
 * <li>a bad exercise whose reference solution fails its own test, which the oracle must reject — proving the differential acceptance gate is real, not rubber-stamped.</li>
 * </ul>
 */
@EnabledIf("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.HyperionMockedLlmE2eSupport#isDockerAvailable")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class HyperionExerciseGenerationMockedEndToEndTest extends AbstractHyperionMockedLlmEndToEndTest {

    private static final Logger log = LoggerFactory.getLogger(HyperionExerciseGenerationMockedEndToEndTest.class);

    private static final String TEST_PREFIX = "hypgenmock";

    // ---- The known-good Java Calculator fixture (package de.test, flat tests/test layout, Ares @Public test with direct calls) --------------------------------------------------

    private static final String PROBLEM_STATEMENT = """
            # Calculator

            Implement a `Calculator` with an `add` method that returns the sum of its two integer arguments.

            [task][Add two numbers](addsTwoNumbers)
            """;

    private static final String SOLUTION_CALCULATOR = """
            package de.test;

            public class Calculator {

                public int add(int a, int b) {
                    return a + b;
                }
            }
            """;

    /** The template keeps the same signature but a wrong placeholder body, so the test fails on it while it still compiles. */
    private static final String TEMPLATE_CALCULATOR = """
            package de.test;

            public class Calculator {

                public int add(int a, int b) {
                    return 0;
                }
            }
            """;

    /**
     * A reference solution that fails its own test ({@code a - b} instead of {@code a + b}): {@code add(2, 3) == -1 != 5}. Compiles, but the differential oracle must reject the
     * exercise because the solution does not pass.
     */
    private static final String BROKEN_SOLUTION_CALCULATOR = """
            package de.test;

            public class Calculator {

                public int add(int a, int b) {
                    return a - b;
                }
            }
            """;

    private static final String CALCULATOR_TEST = """
            package de.test;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            import org.junit.jupiter.api.Test;

            import de.tum.in.test.api.BlacklistPath;
            import de.tum.in.test.api.StrictTimeout;
            import de.tum.in.test.api.WhitelistPath;
            import de.tum.in.test.api.jupiter.Public;

            @Public
            @WhitelistPath("target")
            @BlacklistPath("target/test-classes")
            class CalculatorTest {

                @Test
                @StrictTimeout(5)
                void addsTwoNumbers() {
                    assertEquals(5, new Calculator().add(2, 3));
                }
            }
            """;

    private static final String SOLUTION_PATH = "solution/src/de/test/Calculator.java";

    private static final String TEMPLATE_PATH = "template/src/de/test/Calculator.java";

    private static final String TEST_PATH = "tests/test/de/test/CalculatorTest.java";

    private static final String PROBLEM_STATEMENT_PATH = "problem-statement.md";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generatesValidExercise_deterministic_endToEnd() throws Exception {
        ProgrammingExercise exercise = scaffoldEmptyJavaExercise("HGMOK");
        // The scripted agent: write the four artifacts, self-check with verify.sh against both assignments, then submit. The post-loop verifier is the authoritative gate.
        when(azureOpenAiChatModel.call(any(Prompt.class))).thenReturn(HyperionMockedLlmE2eSupport.writeFile(PROBLEM_STATEMENT_PATH, PROBLEM_STATEMENT),
                HyperionMockedLlmE2eSupport.writeFile(SOLUTION_PATH, SOLUTION_CALCULATOR), HyperionMockedLlmE2eSupport.writeFile(TEMPLATE_PATH, TEMPLATE_CALCULATOR),
                HyperionMockedLlmE2eSupport.writeFile(TEST_PATH, CALCULATOR_TEST), HyperionMockedLlmE2eSupport.bash("sh verify.sh solution"),
                HyperionMockedLlmE2eSupport.bash("sh verify.sh template"), HyperionMockedLlmE2eSupport.submit("Add two integers"));

        try (GenerationOutcome outcome = orchestrator.generate(exercise, instructor(), "Create a Java Calculator exercise.", "mock-generate-valid", GenerationMode.GENERATE,
                () -> false, line -> log.info("[mock-generate] {}", line), null)) {
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            log.info("=== VERIFICATION (valid) ===\n{}", outcome.verification().report());
            assertThat(outcome.verification().solutionPassed()).as("the solution passes its own test").isTrue();
            assertThat(outcome.verification().templateFailed()).as("the template compiles but fails the test").isTrue();
            assertThat(outcome.verification().testCount()).as("at least one test was discovered").isGreaterThan(0);
            assertThat(outcome.isAccepted()).as("the differential oracle accepts the exercise").isTrue();
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION).keySet()).as("the solution repository carries the produced Calculator")
                    .anyMatch(path -> path.endsWith("Calculator.java"));
            assertThat(outcome.producedFiles(RepositoryType.TESTS).keySet()).as("the tests repository carries the produced CalculatorTest")
                    .anyMatch(path -> path.endsWith("CalculatorTest.java"));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rejectsExerciseWhoseSolutionFailsItsOwnTest_deterministic_endToEnd() throws Exception {
        ProgrammingExercise exercise = scaffoldEmptyJavaExercise("HGMBAD");
        // Same fixture but the reference solution returns a - b, so it fails its own addsTwoNumbers test and the oracle must reject (fail-closed). On rejection the orchestrator
        // retries up to its bound; the mock replays only the final submit turn, so each retry re-runs the verifier's two pristine Maven builds and rejects again. The assertion
        // holds regardless of the per-attempt sandbox lifecycle: whether a retry re-verifies the bad-solution workspace or a freshly-seeded empty one, the differential fails
        // either way. This drives the full retry budget, so it is the slowest leg of the Docker-gated suite.
        when(azureOpenAiChatModel.call(any(Prompt.class))).thenReturn(HyperionMockedLlmE2eSupport.writeFile(PROBLEM_STATEMENT_PATH, PROBLEM_STATEMENT),
                HyperionMockedLlmE2eSupport.writeFile(SOLUTION_PATH, BROKEN_SOLUTION_CALCULATOR), HyperionMockedLlmE2eSupport.writeFile(TEMPLATE_PATH, TEMPLATE_CALCULATOR),
                HyperionMockedLlmE2eSupport.writeFile(TEST_PATH, CALCULATOR_TEST), HyperionMockedLlmE2eSupport.submit("Add two integers"));

        try (GenerationOutcome outcome = orchestrator.generate(exercise, instructor(), "Create a Java Calculator exercise.", "mock-generate-bad", GenerationMode.GENERATE,
                () -> false, line -> log.info("[mock-generate-bad] {}", line), null)) {
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            log.info("=== VERIFICATION (bad) ===\n{}", outcome.verification().report());
            assertThat(outcome.verification().solutionPassed()).as("the broken solution does NOT pass its own test").isFalse();
            assertThat(outcome.isAccepted()).as("the differential oracle rejects an exercise whose solution fails").isFalse();
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
        // emptyRepositories=true: scaffold the flat Java skeleton and clear the template/solution sources and the sample tests (exactly as production does before from-scratch AI
        // generation), so the agent writes into a clean workspace.
        return creationService.createProgrammingExercise(exercise, true);
    }

    private User instructor() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }
}

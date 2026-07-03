package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.ExerciseGenerationOrchestrationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationOutcome;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Deterministic, committed end-to-end test of agentic exercise GENERATION. It replaces the live-GPU {@code HyperionExerciseGenerationEndToEndTest} for CI: instead of an external
 * LLM, a mocked {@code azureOpenAiChatModel} returns a FIXED sequence of tool-calling turns that drive the REAL agent loop, the REAL Docker sandbox, and the REAL differential
 * oracle. Only the model is faked; the container build and the acceptance verdict are authentic. Docker-gated (not GPU-gated), so it runs wherever the LocalCI integration tests
 * do.
 * <p>
 * The fixture is a minimal-but-real Java {@code Calculator}: the solution passes its one JUnit test, the template compiles but fails it (a {@code return 0} stub), and the problem
 * statement binds the test with a {@code [task]}. Two scenarios:
 * <ul>
 * <li>a VALID exercise the oracle must ACCEPT (solution passes, template fails, at least one test);</li>
 * <li>a BAD exercise whose reference solution fails its OWN test, which the oracle must REJECT — proving the differential acceptance gate is real, not rubber-stamped.</li>
 * </ul>
 */
@EnabledIf("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.HyperionMockedLlmE2eSupport#isDockerAvailable")
class HyperionExerciseGenerationMockedEndToEndTest extends AbstractSpringIntegrationLocalCILocalVCTest {

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

    /** The template keeps the SAME signature but a wrong placeholder body, so the test fails on it while it still compiles. */
    private static final String TEMPLATE_CALCULATOR = """
            package de.test;

            public class Calculator {

                public int add(int a, int b) {
                    return 0;
                }
            }
            """;

    /**
     * A reference solution that FAILS its own test ({@code a - b} instead of {@code a + b}): {@code add(2, 3) == -1 != 5}. Compiles, but the differential oracle must reject the
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

    @Autowired
    private ExerciseGenerationOrchestrationService orchestrator;

    @Autowired
    private ProgrammingExerciseCreationUpdateService creationService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private Map<ProgrammingLanguage, String> replacedBuildImages;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        // The shared test application.yml points every build image at a placeholder so the mocked-build buckets never pull one; override Java back to its real execution image so
        // the sandbox build and the differential oracle run on the real Maven/Ares toolchain. The mocked model needs no context-window override (it ignores prompt options).
        // Capture the prior entry so @AfterEach can restore it — the Spring context is cached and shared, so the override must not leak into later tests.
        replacedBuildImages = HyperionMockedLlmE2eSupport.useProductionBuildImages(programmingLanguageConfiguration, ProgrammingLanguage.JAVA);
    }

    @AfterEach
    void tearDown() {
        HyperionMockedLlmE2eSupport.restoreBuildImages(programmingLanguageConfiguration, replacedBuildImages);
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
                () -> false, line -> log.info("[mock-generate] {}", line))) {
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
        // Same fixture but the reference solution returns a - b, so it fails its own addsTwoNumbers test. The oracle must reject (fail-closed). On rejection the orchestrator
        // retries
        // up to its bound; the mock only ever replays the final submit turn, so each retry runs the authoritative verifier's two pristine Maven builds again and rejects again. The
        // assertion is robust to the per-attempt sandbox lifecycle: whether a retry re-verifies the same bad-solution workspace or a freshly-seeded (empty) one, the differential
        // fails either way, so this proves the fail-closed gate deterministically. Cost note: this drives the full retry budget, so the reject proof pays for several real
        // container
        // builds (deterministic, but the slowest leg of this Docker-gated suite).
        when(azureOpenAiChatModel.call(any(Prompt.class))).thenReturn(HyperionMockedLlmE2eSupport.writeFile(PROBLEM_STATEMENT_PATH, PROBLEM_STATEMENT),
                HyperionMockedLlmE2eSupport.writeFile(SOLUTION_PATH, BROKEN_SOLUTION_CALCULATOR), HyperionMockedLlmE2eSupport.writeFile(TEMPLATE_PATH, TEMPLATE_CALCULATOR),
                HyperionMockedLlmE2eSupport.writeFile(TEST_PATH, CALCULATOR_TEST), HyperionMockedLlmE2eSupport.submit("Add two integers"));

        try (GenerationOutcome outcome = orchestrator.generate(exercise, instructor(), "Create a Java Calculator exercise.", "mock-generate-bad", GenerationMode.GENERATE,
                () -> false, line -> log.info("[mock-generate-bad] {}", line))) {
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

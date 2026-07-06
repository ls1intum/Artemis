package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
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
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

/**
 * Deterministic, committed end-to-end test of the {@link GenerationMode#ADAPT} flow, the mocked-LLM counterpart of the live-GPU {@code HyperionAdaptWithFeedbackEndToEndTest}. A
 * mocked {@code azureOpenAiChatModel} drives the real agent loop, sandbox, and differential oracle; only the model is faked. Docker-gated, not GPU-gated.
 * <p>
 * It seeds a flat add-only {@code Calculator} exercise into the live solution/template/tests repositories (the exercise an instructor would be adapting), which the orchestrator
 * then seeds the sandbox from. The scripted feedback adds a subtraction operation and a new subtraction test file without ever rewriting the seeded addition test. The proof is
 * non-tautological: the seeded {@code addsTwoNumbers} test — which the scripted turns never write — survives into the produced tests repo, so the run adapted the existing harness
 * rather than degenerating into from-scratch generation (where that test would be absent).
 */
@EnabledIf("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.HyperionMockedLlmE2eSupport#isDockerAvailable")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class HyperionAdaptMockedEndToEndTest extends AbstractHyperionMockedLlmEndToEndTest {

    private static final Logger log = LoggerFactory.getLogger(HyperionAdaptMockedEndToEndTest.class);

    private static final String TEST_PREFIX = "hypadaptmock";

    // ---- The pre-existing (add-only) exercise seeded into the live repositories before the adapt runs --------------------------------------------------------------------------

    private static final String SEED_PROBLEM_STATEMENT = """
            # Calculator

            Implement a `Calculator` that can add two integers.

            [task][Add two numbers](addsTwoNumbers)
            """;

    private static final String SEED_SOLUTION_CALCULATOR = """
            package de.test;

            public class Calculator {

                public int add(int a, int b) {
                    return a + b;
                }
            }
            """;

    private static final String SEED_TEMPLATE_CALCULATOR = """
            package de.test;

            public class Calculator {

                public int add(int a, int b) {
                    return 0;
                }
            }
            """;

    /** The single pre-existing test. The scripted adapt turns never write this file or its {@code addsTwoNumbers} name — its survival into the produced tests proves adaptation. */
    private static final String SEED_CALCULATOR_TEST = """
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

    // ---- The scripted adapt: add a subtraction operation and its own new test, keeping the pre-existing addition test intact
    // ------------------------------------------------------

    private static final String ADAPTED_PROBLEM_STATEMENT = """
            # Calculator

            Implement a `Calculator` that can add and subtract two integers.

            [task][Add two numbers](addsTwoNumbers)
            [task][Subtract two numbers](subtractsTwoNumbers)
            """;

    private static final String ADAPTED_SOLUTION_CALCULATOR = """
            package de.test;

            public class Calculator {

                public int add(int a, int b) {
                    return a + b;
                }

                public int subtract(int a, int b) {
                    return a - b;
                }
            }
            """;

    private static final String ADAPTED_TEMPLATE_CALCULATOR = """
            package de.test;

            public class Calculator {

                public int add(int a, int b) {
                    return 0;
                }

                public int subtract(int a, int b) {
                    return 0;
                }
            }
            """;

    /** The new test the adapt adds; kept in its own class so the scripted turns never touch (and therefore never re-author) the seeded {@code CalculatorTest}. */
    private static final String ADDED_SUBTRACT_TEST = """
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
            class SubtractCalculatorTest {

                @Test
                @StrictTimeout(5)
                void subtractsTwoNumbers() {
                    assertEquals(1, new Calculator().subtract(4, 3));
                }
            }
            """;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void adaptsExistingExerciseAddingATest_deterministic_endToEnd() throws Exception {
        // 1. Build an existing add-only exercise: scaffold the flat Java skeleton, then commit the add-only sources/tests into the live repos the sandbox will be seeded from.
        ProgrammingExercise exercise = scaffoldJavaExercise();
        seedRepository(exercise, RepositoryType.TESTS, Map.of("test/de/test/CalculatorTest.java", SEED_CALCULATOR_TEST));
        seedRepository(exercise, RepositoryType.SOLUTION, Map.of("src/de/test/Calculator.java", SEED_SOLUTION_CALCULATOR));
        seedRepository(exercise, RepositoryType.TEMPLATE, Map.of("src/de/test/Calculator.java", SEED_TEMPLATE_CALCULATOR));
        // The problem statement seed is not strictly required (it is overwritten below) but keeps the seeded exercise self-consistent.
        seedRepository(exercise, RepositoryType.TESTS, Map.of("problem-statement.md", SEED_PROBLEM_STATEMENT));

        // 2. The scripted adapt: extend the solution and template with subtraction, add a new subtraction test file, rebind both tasks in the problem statement, then submit. It
        // never writes the seeded CalculatorTest, so the pre-existing addition test can only reach the oracle via the seed-from-live-repo path the ADAPT flow drives.
        when(azureOpenAiChatModel.call(any(Prompt.class))).thenReturn(HyperionMockedLlmE2eSupport.writeFile("solution/src/de/test/Calculator.java", ADAPTED_SOLUTION_CALCULATOR),
                HyperionMockedLlmE2eSupport.writeFile("template/src/de/test/Calculator.java", ADAPTED_TEMPLATE_CALCULATOR),
                HyperionMockedLlmE2eSupport.writeFile("tests/test/de/test/SubtractCalculatorTest.java", ADDED_SUBTRACT_TEST),
                HyperionMockedLlmE2eSupport.writeFile("problem-statement.md", ADAPTED_PROBLEM_STATEMENT), HyperionMockedLlmE2eSupport.bash("sh verify.sh solution"),
                HyperionMockedLlmE2eSupport.bash("sh verify.sh template"), HyperionMockedLlmE2eSupport.submit("Added a subtraction operation and its test"));

        try (GenerationOutcome outcome = orchestrator.generate(exercise, instructor(), "Also require the calculator to subtract, and add a test for it.", "mock-adapt",
                GenerationMode.ADAPT, () -> false, line -> log.info("[mock-adapt] {}", line), null)) {
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            log.info("=== VERIFICATION (adapt) ===\n{}", outcome.verification().report());
            assertThat(outcome.verification().solutionPassed()).as("the solution passes after the adapt").isTrue();
            assertThat(outcome.verification().templateFailed()).as("the template still fails after the adapt").isTrue();
            assertThat(outcome.isAccepted()).as("the adapted exercise is accepted by the differential oracle").isTrue();
            // The oracle-derived count: both the seeded addition test and the added subtraction test were discovered and run in the real container.
            assertThat(outcome.verification().testCount()).as("the added subtraction test grew the suite from the one seeded test to two").isEqualTo(2);
            // Non-tautological adaptation proof: the seeded CalculatorTest/addsTwoNumbers test — which the scripted adapt turns never wrote — survived into the produced tests, so
            // the run adapted the existing seeded harness rather than generating a fresh workspace (where addsTwoNumbers would be absent).
            Map<String, String> producedTests = outcome.producedFiles(RepositoryType.TESTS);
            assertThat(producedTests.keySet()).as("the pre-existing CalculatorTest survived the adapt (seeded, never written by the adapt script)")
                    .anyMatch(path -> path.endsWith("CalculatorTest.java"));
            assertThat(producedTests.keySet()).as("the added SubtractCalculatorTest is present").anyMatch(path -> path.endsWith("SubtractCalculatorTest.java"));
            assertThat(String.join("\n", producedTests.values())).as("both the seeded addition test and the added subtraction test are present").contains("addsTwoNumbers")
                    .contains("subtractsTwoNumbers");
        }
    }

    /**
     * Commits the given files into the exercise's live {@code repositoryType} repository (relative to its root), simulating pre-existing content the ADAPT run seeds its sandbox
     * from. Multiple calls for the same repository accumulate additively (each stages and pushes its own commit).
     */
    private void seedRepository(ProgrammingExercise exercise, RepositoryType repositoryType, Map<String, String> filesByRelativePath) throws Exception {
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        Repository repository = gitService.getOrCheckoutRepository(uri, true, localVCLocalCITestService.getDefaultBranch(), true);
        Path root = repository.getLocalPath();
        for (Map.Entry<String, String> file : new LinkedHashMap<>(filesByRelativePath).entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            // FileUtils (not Files.writeString) per the architecture rule: it creates missing parent directories.
            FileUtils.writeStringToFile(target.toFile(), file.getValue(), StandardCharsets.UTF_8);
        }
        gitService.stageAllChanges(repository);
        gitService.commitAndPush(repository, "Seed existing " + repositoryType + " content for the adapt", false, null);
    }

    private ProgrammingExercise scaffoldJavaExercise() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course,
                ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);
        exercise.setShortName("HAMOK");
        exercise.setTitle("Hyperion Adapt Mocked E2E");
        exercise.setChannelName("hyp-adapt-mock");
        return creationService.createProgrammingExercise(exercise, true);
    }

    private User instructor() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }
}

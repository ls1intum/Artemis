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
 * Deterministic end-to-end test of the ADAPT flow. The LLM is scripted, but sandbox seeding, structured verify, and differential verification are real.
 */
@EnabledIf("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.HyperionMockedLlmE2eSupport#dockerGateEnabled")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class HyperionAdaptMockedEndToEndTest extends AbstractHyperionMockedLlmEndToEndTest {

    private static final Logger log = LoggerFactory.getLogger(HyperionAdaptMockedEndToEndTest.class);

    private static final String TEST_PREFIX = "hypadaptmock";

    private static final String SEED_PROBLEM_STATEMENT = """
            # Calculator

            Implement a `Calculator` that can add two integers.

            ```text
            add(2, 3) -> 5
            add(-2, -3) -> -5
            ```

            [task][Add positive numbers](addsTwoNumbers)
            Implement `add` so it returns the sum of positive operands.
            [task][Add negative numbers](addsNegativeNumbers)
            Preserve operand signs when `add` receives negative values.
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
                @StrictTimeout(1)
                void addsTwoNumbers() {
                    assertEquals(5, new Calculator().add(2, 3), "add must sum two positive numbers.");
                }

                @Test
                @StrictTimeout(1)
                void addsNegativeNumbers() {
                    assertEquals(-5, new Calculator().add(-2, -3), "add must preserve the sign when summing negative numbers.");
                }
            }
            """;

    private static final String ADAPTED_PROBLEM_STATEMENT = """
            # Calculator

            Implement a `Calculator` that can add and subtract two integers.

            ```text
            add(2, 3) -> 5
            add(-2, -3) -> -5
            subtract(4, 3) -> 1
            subtract(3, 4) -> -1
            ```

            [task][Add positive numbers](addsTwoNumbers)
            Implement `add` so it returns the sum of positive operands.
            [task][Add negative numbers](addsNegativeNumbers)
            Preserve operand signs when `add` receives negative values.
            [task][Subtract positive numbers](subtractsTwoNumbers)
            Implement `subtract` so it returns the difference of two integers.
            [task][Subtract to a negative result](subtractsToNegativeResult)
            Allow `subtract` to return a negative difference.
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
                @StrictTimeout(1)
                void subtractsTwoNumbers() {
                    assertEquals(1, new Calculator().subtract(4, 3), "subtract must compute the positive difference.");
                }

                @Test
                @StrictTimeout(1)
                void subtractsToNegativeResult() {
                    assertEquals(-1, new Calculator().subtract(3, 4), "subtract must allow negative results.");
                }
            }
            """;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void adaptsExistingExerciseAddingTests_deterministic_endToEnd() throws Exception {
        ProgrammingExercise exercise = scaffoldJavaExercise();
        seedRepository(exercise, RepositoryType.TESTS, Map.of("test/de/test/CalculatorTest.java", SEED_CALCULATOR_TEST));
        seedRepository(exercise, RepositoryType.SOLUTION, Map.of("src/de/test/Calculator.java", SEED_SOLUTION_CALCULATOR));
        seedRepository(exercise, RepositoryType.TEMPLATE, Map.of("src/de/test/Calculator.java", SEED_TEMPLATE_CALCULATOR));

        when(azureOpenAiChatModel.call(any(Prompt.class))).thenReturn(HyperionMockedLlmE2eSupport.writeFile("solution/src/de/test/Calculator.java", ADAPTED_SOLUTION_CALCULATOR),
                HyperionMockedLlmE2eSupport.writeFile("template/src/de/test/Calculator.java", ADAPTED_TEMPLATE_CALCULATOR),
                HyperionMockedLlmE2eSupport.writeFile("tests/test/de/test/SubtractCalculatorTest.java", ADDED_SUBTRACT_TEST),
                HyperionMockedLlmE2eSupport.writeFile("problem-statement.md", ADAPTED_PROBLEM_STATEMENT), HyperionMockedLlmE2eSupport.verify(),
                HyperionMockedLlmE2eSupport.submit("Added subtraction with positive and negative-result tests"), HyperionMockedLlmE2eSupport.cleanQualityReview(),
                HyperionMockedLlmE2eSupport.cleanQualityReview());

        try (GenerationOutcome outcome = orchestrator.generate(exercise, instructor(), "Also require subtraction with positive and negative results.", "mock-adapt",
                GenerationMode.ADAPT, () -> false, line -> log.info("[mock-adapt] {}", line), null, null)) {
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            log.info("=== VERIFICATION (adapt) ===\n{}", outcome.verification().report());
            assertThat(outcome.verification().solutionPassed()).as("the solution passes after the adapt").isTrue();
            assertThat(outcome.verification().templateFailed()).as("the template still fails after the adapt").isTrue();
            assertThat(outcome.verification().testCount()).as("the two seeded tests and two added tests all run").isEqualTo(4);
            assertThat(outcome.isMechanicallyVerified()).as("the adapted exercise is accepted by the differential oracle").isTrue();
            assertThat(outcome.specFidelityReport().hasFindings()).as("the adapted mocked exercise has no deterministic quality findings").isFalse();
            assertThat(outcome.producedProblemStatement()).contains("[task][Add positive numbers](addsTwoNumbers)", "[task][Add negative numbers](addsNegativeNumbers)",
                    "[task][Subtract positive numbers](subtractsTwoNumbers)", "[task][Subtract to a negative result](subtractsToNegativeResult)");

            Map<String, String> producedTests = outcome.producedFiles(RepositoryType.TESTS);
            assertThat(producedTests.entrySet()).anySatisfy(entry -> {
                assertThat(entry.getKey()).endsWith("CalculatorTest.java");
                assertThat(entry.getValue()).isEqualTo(SEED_CALCULATOR_TEST);
            });
            assertThat(producedTests.keySet()).anyMatch(path -> path.endsWith("SubtractCalculatorTest.java"));
            assertThat(String.join("\n", producedTests.values())).contains("addsTwoNumbers", "addsNegativeNumbers", "subtractsTwoNumbers", "subtractsToNegativeResult");
            assertThat(producedTests.keySet()).noneMatch(path -> path.endsWith("problem-statement.md"));
        }
    }

    private void seedRepository(ProgrammingExercise exercise, RepositoryType repositoryType, Map<String, String> filesByRelativePath) throws Exception {
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
        Repository repository = gitService.getOrCheckoutRepository(uri, true, localVCLocalCITestService.getDefaultBranch(), true);
        Path root = repository.getLocalPath();
        for (Map.Entry<String, String> file : new LinkedHashMap<>(filesByRelativePath).entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
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
        exercise.setProblemStatement(SEED_PROBLEM_STATEMENT);
        return useOfflineMavenPluginVersions(creationService.createProgrammingExercise(exercise, true));
    }

    private User instructor() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }
}

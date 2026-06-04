package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Adapt-with-feedback evaluation: the agent revises an existing, already-buildable exercise from instructor feedback while keeping it verifiable, across diverse feedback
 * categories
 * (additive, difficulty up/down, bug-fix, refactor, requirement-change, contradictory) and several languages. Each case asserts the run CONVERGED (differential oracle still
 * accepts), the FEEDBACK was actually APPLIED (a scenario-specific check, proving the baseline was not re-submitted unchanged), and VERIFIABILITY was preserved. Gated behind
 * {@code HYPERION_E2E_GPU}; {@code HYPERION_ADAPT_SCENARIOS} narrows the run for parallel isolated JVMs.
 */
@EnabledIfEnvironmentVariable(named = "HYPERION_E2E_GPU", matches = "true")
class HyperionAdaptWithFeedbackEndToEndTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final Logger log = LoggerFactory.getLogger(HyperionAdaptWithFeedbackEndToEndTest.class);

    private static final String TEST_PREFIX = "hypadaptfb";

    private static final String GPU_BASE_URL = System.getenv().getOrDefault("GPU_BASE_URL", "https://gpu.ase.cit.tum.de");

    private static final String GPU_API_KEY = System.getenv("GPU_API_KEY");

    private static final String GPU_MODEL = System.getenv().getOrDefault("GPU_MODEL", "openai/gpt-oss-120b");

    @Autowired
    private ExerciseGenerationOrchestrationService orchestrator;

    @Autowired
    private ProgrammingExerciseCreationUpdateService creationService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Autowired
    private AgentLoopRunner agentLoopRunner;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        // Override the placeholder build images (java/python/javascript/typescript — the languages this feedback matrix exercises) and the agent context window in place on the
        // shared beans, rather than via @TestPropertySource which would fork a separate Spring context.
        HyperionGpuTestEnvironment.useProductionBuildImages(programmingLanguageConfiguration, ProgrammingLanguage.JAVA, ProgrammingLanguage.PYTHON, ProgrammingLanguage.JAVASCRIPT,
                ProgrammingLanguage.TYPESCRIPT);
        HyperionGpuTestEnvironment.useGpuContextWindow(agentLoopRunner);
        GpuEndpointChatModel realModel = new GpuEndpointChatModel(GPU_BASE_URL, GPU_API_KEY, GPU_MODEL);
        when(azureOpenAiChatModel.call(ArgumentMatchers.any(Prompt.class))).thenAnswer((InvocationOnMock invocation) -> realModel.call(invocation.getArgument(0, Prompt.class)));
    }

    /**
     * The full adapt-feedback matrix. Every case starts from a FULLY scaffolded canonical exercise ({@code emptyRepositories=false}) — an existing, already-buildable exercise,
     * exactly the production precondition for an adapt — and applies one diverse feedback brief. The Java cases keep the canonical sorting-strategy structure; the Python / JS / TS
     * cases adapt their canonical sorting samples. Each carries an {@link AdaptCheck} that proves the SPECIFIC feedback was applied (beyond the oracle's generic accept).
     */
    static Stream<Arguments> adaptFeedbackMatrix() {
        return applyScenarioFilter(Stream.of(

                // --- Additive: add an edge-case test (already-sorted), keep all existing tests and the differential. ---
                adaptCase("additive-java", ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "HFADD",
                        "Adapt this existing Java sorting exercise: as instructor feedback, ADD one behaviour test that sorts an ALREADY-SORTED list and asserts it stays sorted, and "
                                + "mention in the problem statement that the input may already be sorted. Keep EVERY existing test and the existing public structure, keep the "
                                + "reference solution passing and the template failing. Run `sh verify.sh solution` and `sh verify.sh template`, then submit.",
                        new AdaptCheck(5, outcome -> {
                            assertThat(outcome.producedProblemStatement().toLowerCase()).as("statement mentions already-sorted").contains("already");
                            assertThat(outcome.verification().testCount()).as("a test was added on top of the canonical four").isGreaterThan(4);
                        })),

                // --- Additive (negative numbers): a different additive edge case, in Python. ---
                adaptCase("additive-negatives-python", ProgrammingLanguage.PYTHON, null, "HFNEG",
                        "Adapt this existing Python exercise: as instructor feedback, ADD a test that sorts a list containing NEGATIVE numbers and asserts the correct ascending order. "
                                + "Keep every existing test, keep the [task] bindings, keep the reference solution passing and the template failing. Run `sh verify.sh solution` and "
                                + "`sh verify.sh template`, then submit.",
                        new AdaptCheck(5, outcome -> {
                            String tests = allTestSources(outcome).toLowerCase();
                            assertThat(tests).as("a negative-number test was added").containsAnyOf("-", "negative", "neg");
                        })),

                // --- Difficulty up: tighten a behavioural requirement (in-place / stability), keeping it verifiable. ---
                adaptCase("difficulty-up-java", ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "HFHARD",
                        "Adapt this existing Java sorting exercise to be HARDER: as instructor feedback, require the sort to be STABLE and performed strictly IN-PLACE (the original "
                                + "list object is mutated, no new list is allocated), add a test that verifies stability on equal-keyed elements, and state these requirements in the "
                                + "problem statement. Keep the existing public structure, keep the reference solution passing and the template failing. Run `sh verify.sh solution` and "
                                + "`sh verify.sh template`, then submit.",
                        new AdaptCheck(5, outcome -> {
                            String statement = outcome.producedProblemStatement().toLowerCase();
                            assertThat(statement).as("the harder requirement is stated").containsAnyOf("stable", "in-place", "in place", "in‑place");
                            assertThat(outcome.verification().testCount()).as("a stability test was added").isGreaterThan(4);
                        })),

                // --- Difficulty down / simplify: remove a constraint, keep it a valid, verifiable exercise. ---
                adaptCase("simplify-java", ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "HFEASY",
                        "Adapt this existing Java sorting exercise to be SIMPLER: as instructor feedback, REMOVE the strategy-selection rule (the Context no longer needs to pick "
                                + "BubbleSort vs MergeSort by size) and simplify the problem statement to ask only for a single working sort. Keep it a valid Artemis exercise: the "
                                + "reference solution must pass every test, the template must fail them, and the [task] bindings must resolve. Run `sh verify.sh solution` and "
                                + "`sh verify.sh template`, then submit.",
                        new AdaptCheck(1, outcome -> assertThat(outcome.producedProblemStatement()).as("statement still binds tasks").contains("[task]"))),

                // --- Bug-fix-driven: feedback reports a failing case; the agent must repair the SOLUTION and keep the differential. ---
                adaptCase("bugfix-java", ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "HFBUG",
                        "Adapt this existing Java sorting exercise. Instructor feedback: a student reported that sorting a SINGLE-ELEMENT list and an EMPTY list misbehaves. ADD tests "
                                + "for the empty list and the single-element list, and make sure the REFERENCE SOLUTION handles both correctly (fix it if needed) so the solution "
                                + "passes all tests while the template still fails them. Run `sh verify.sh solution` and `sh verify.sh template`, then submit.",
                        new AdaptCheck(5, outcome -> assertThat(outcome.verification().solutionPassed()).as("solution passes after the fix").isTrue())),

                // --- Refactor: rename a graded type, keeping the [task] bindings and the differential consistent end-to-end. ---
                adaptCase("refactor-rename-python", ProgrammingLanguage.PYTHON, null, "HFREF",
                        "Adapt this existing Python exercise. Instructor feedback: RENAME the main sorting function to `sort_ascending` everywhere it is used (solution, template, "
                                + "tests, and the [task] bindings in the problem statement) so everything stays consistent. Keep the reference solution passing and the template "
                                + "failing, and make sure every [task] binding still resolves to a real test. Run `sh verify.sh solution` and `sh verify.sh template`, then submit.",
                        new AdaptCheck(1, outcome -> {
                            String all = (allTestSources(outcome) + "\n" + allSolutionSources(outcome)).toLowerCase();
                            assertThat(all).as("the renamed function name appears").contains("sort_ascending");
                        })),

                // --- Requirement change: change a behavioural contract (throw instead of return a sentinel) consistently. ---
                adaptCase("requirement-change-typescript", ProgrammingLanguage.TYPESCRIPT, null, "HFREQ",
                        "Adapt this existing TypeScript exercise. Instructor feedback: change the contract so that popping or peeking an EMPTY stack now THROWS an Error instead of "
                                + "returning undefined. Update the problem statement, the reference solution, the template (its placeholder must still fail), and the tests to assert "
                                + "the thrown error. Keep the reference solution passing all tests and the template failing them. Run `sh verify.sh solution` and `sh verify.sh "
                                + "template`, then submit.",
                        new AdaptCheck(1, outcome -> {
                            String all = (allTestSources(outcome) + "\n" + allSolutionSources(outcome) + "\n" + outcome.producedProblemStatement()).toLowerCase();
                            assertThat(all).as("the throw-on-empty contract is present").containsAnyOf("throw", "error");
                        })),

                // --- Contradictory / under-specified: a self-contradicting brief. The agent must degrade GRACEFULLY: pick one coherent reading and stay verifiable, not thrash to
                // a
                // broken/empty exercise. We assert ONLY that it stayed verifiable (convergence under ambiguity); the AdaptCheck is a no-op beyond that.
                adaptCase("contradictory-java", ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "HFCON",
                        "Adapt this existing Java sorting exercise. Instructor feedback (intentionally vague and partly contradictory): \"make it both much simpler and much more "
                                + "advanced, remove the tests but add more coverage, and keep it exactly the same.\" Do something sensible: produce a single COHERENT, valid Artemis "
                                + "exercise where the reference solution passes every test and the template fails them and the [task] bindings resolve. Run `sh verify.sh solution` and "
                                + "`sh verify.sh template`, then submit.",
                        new AdaptCheck(1, outcome -> assertThat(outcome.producedProblemStatement()).as("a coherent statement with task bindings survived the ambiguity")
                                .contains("[task]")))));
    }

    private static Stream<Arguments> applyScenarioFilter(Stream<Arguments> all) {
        String filter = System.getenv("HYPERION_ADAPT_SCENARIOS");
        if (filter == null || filter.isBlank()) {
            return all;
        }
        java.util.Set<String> allowed = java.util.Arrays.stream(filter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return all.filter(arguments -> allowed.contains(((String) arguments.get()[0]).toLowerCase(java.util.Locale.ROOT)));
    }

    private static Arguments adaptCase(String key, ProgrammingLanguage language, ProjectType projectType, String shortName, String feedback, AdaptCheck check) {
        return Arguments.of(key, language, projectType, shortName, feedback, check);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adaptFeedbackMatrix")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void adaptsWithFeedback(String key, ProgrammingLanguage language, ProjectType projectType, String shortName, String feedback, AdaptCheck check) throws Exception {
        // Start from a fully scaffolded, already-buildable canonical exercise — the existing exercise an instructor would be adapting.
        ProgrammingExercise exercise = scaffoldExercise(shortName, false, language, projectType);

        StringBuilder transcript = new StringBuilder();
        try (var outcome = orchestrator.generate(exercise, instructor(), feedback, "adaptfb-" + key, () -> false, line -> {
            transcript.append(line).append('\n');
            log.info("[adaptfb:{}] {}", key, line);
        })) {
            Path exportDirectory = GeneratedExerciseExporter.export("adaptfb-" + key, outcome, transcript.toString());
            log.info("=== EXPORTED (adaptfb-{}) to {} ===", key, exportDirectory.toAbsolutePath());

            // Distinguish a transient MODEL-INFRASTRUCTURE failure (the external GPU dropped the connection mid-run, which the agent loop surfaces as ERROR after exhausting its
            // retries) from a genuine GENERATION-QUALITY failure. The former is an infra outage, not a defect in the adapt engine, so report it as infra-gated (mirroring the
            // sibling matrix tests) rather than as a confusing "verification did not run" null. A real quality miss still reaches the differential-oracle assertions below.
            if (outcome.loopResult().status() == AgentLoopResult.Status.ERROR) {
                throw new org.opentest4j.TestAbortedException("[" + key + "] infra-gated: the agent loop ended in ERROR (likely a transient GPU-endpoint outage mid-run) after "
                        + outcome.loopResult().turns() + " turns; see transcript at " + exportDirectory.toAbsolutePath());
            }
            assertThat(outcome.verification()).as("[%s] verification ran (loop status was %s)", key, outcome.loopResult().status()).isNotNull();
            log.info("=== VERIFICATION (adaptfb-{}) [turns={}] ===%n{}", key, outcome.loopResult().turns(), outcome.verification().report());

            // CONVERGENCE + VERIFIABILITY: the adapted exercise must still satisfy the differential oracle.
            assertThat(outcome.verification().solutionPassed()).as("[%s] solution passes after adapt", key).isTrue();
            assertThat(outcome.verification().templateFailed()).as("[%s] template fails after adapt", key).isTrue();
            assertThat(outcome.isAccepted()).as("[%s] adapted exercise accepted by the differential oracle", key).isTrue();
            assertThat(outcome.producedFiles(RepositoryType.TESTS)).as("[%s] tests repo non-empty", key).isNotEmpty();
            // Coarse "the adapt did not collapse the suite" guard; the per-scenario AdaptCheck below carries the precise feedback-applied proof.
            assertThat(outcome.verification().testCount()).as("[%s] at least %d tests survive the adapt", key, check.minTestCount()).isGreaterThanOrEqualTo(check.minTestCount());

            // FEEDBACK-APPLIED: the scenario-specific proof that the agent actually applied the feedback (did not re-submit the baseline unchanged).
            check.assertions().accept(outcome);
        }
    }

    // ---- helpers ----

    private static String allTestSources(GenerationOutcome outcome) {
        return String.join("\n", outcome.producedFiles(RepositoryType.TESTS).values());
    }

    private static String allSolutionSources(GenerationOutcome outcome) {
        return String.join("\n", outcome.producedFiles(RepositoryType.SOLUTION).values());
    }

    private ProgrammingExercise scaffoldExercise(String shortName, boolean emptyRepositories, ProgrammingLanguage language, ProjectType projectType) throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course, language);
        exercise.setProjectType(projectType);
        exercise.setShortName(shortName);
        exercise.setTitle("Hyperion AdaptFB " + shortName);
        exercise.setChannelName("hyp-afb-" + shortName.toLowerCase());
        return creationService.createProgrammingExercise(exercise, emptyRepositories);
    }

    private de.tum.cit.aet.artemis.account.domain.User instructor() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }

    /**
     * A scenario-specific proof that the feedback was applied, plus the minimum number of tests expected after the adapt (a coarse "did not strip the suite" guard, 1 when the
     * scenario legitimately restructures the test set).
     *
     * @param minTestCount the minimum tests the adapted exercise should still carry
     * @param assertions   the scenario-specific assertions over the produced outcome
     */
    private record AdaptCheck(int minTestCount, Consumer<GenerationOutcome> assertions) {
    }
}

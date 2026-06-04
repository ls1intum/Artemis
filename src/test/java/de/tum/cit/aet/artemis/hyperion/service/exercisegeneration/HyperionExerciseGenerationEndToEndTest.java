package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Genuine end-to-end test of agentic exercise generation against the full Artemis stack (real scaffold via the production creation path, real sandbox container, real agent loop
 * against the live GPU model, real differential oracle). Covers the structurally-distinct flows — structural-Ares seeding, bare-topic and pasted-problem-statement prompts,
 * draft-then-spec-mode, a larger multi-class exercise — plus a from-scratch language matrix. Gated behind {@code HYPERION_E2E_GPU} (external LLM + ~1 GB build image); a
 * manual/nightly run, not part of fast CI.
 */
@EnabledIfEnvironmentVariable(named = "HYPERION_E2E_GPU", matches = "true")
class HyperionExerciseGenerationEndToEndTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final Logger log = LoggerFactory.getLogger(HyperionExerciseGenerationEndToEndTest.class);

    private static final String TEST_PREFIX = "hypgen";

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
    private de.tum.cit.aet.artemis.hyperion.service.HyperionProblemStatementGenerationService problemStatementGenerationService;

    @Autowired
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Autowired
    private AgentLoopRunner agentLoopRunner;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        // The base class points every build image at a placeholder so CI never pulls one; this real E2E overrides each language back to its production execution image (so the
        // language matrix can run any of them) and points the agent's window-aware compaction at the gpt-oss-120b deployment's real 65536-token context window. Done in-place on
        // the
        // shared beans rather than via @TestPropertySource so no separate Spring context is forked.
        HyperionGpuTestEnvironment.useProductionBuildImages(programmingLanguageConfiguration);
        HyperionGpuTestEnvironment.useGpuContextWindow(agentLoopRunner);
        // Drive the real agent loop with the real GPU model by making the (mocked) ChatModel bean delegate to the live endpoint.
        GpuEndpointChatModel realModel = new GpuEndpointChatModel(GPU_BASE_URL, GPU_API_KEY, GPU_MODEL);
        when(azureOpenAiChatModel.call(ArgumentMatchers.any(Prompt.class))).thenAnswer((InvocationOnMock invocation) -> realModel.call(invocation.getArgument(0, Prompt.class)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generatesExerciseWithStructuralTests_endToEnd() throws Exception {
        // Structural-Ares pipeline: the template OMITS a class the student must add, so the platform auto-generates and seeds structural tests (test.json + ClassTest) from the
        // solution/template structure difference. Asserts that pipeline produced and seeded the structure oracle; differential acceptance is covered by the other scenarios.
        ProgrammingExercise exercise = scaffoldExercise("HYPST", true);
        String prompt = "Create a Java exercise with two integer-array sorters that both implement an interface `Sorter` with `int[] sort(int[] input)`: `BubbleSort` and "
                + "`InsertionSort`. In the SOLUTION implement both. In the TEMPLATE include `Sorter` and `BubbleSort` (with a compiling stub body that returns null), but OMIT the "
                + "`InsertionSort` class entirely — the student must create it. Write @Public behaviour tests for BubbleSort with DIRECT calls (no reflection). Do NOT reference "
                + "InsertionSort in your behaviour tests and do NOT write test.json — the platform automatically generates structural tests checking that InsertionSort exists and "
                + "implements Sorter. Add [task] bindings. Run `sh verify.sh solution` and `sh verify.sh template` to confirm the solution passes and the template fails, then submit.";

        StringBuilder transcript = new StringBuilder();
        try (var outcome = orchestrator.generate(exercise, instructor(), prompt, "e2e-structural", () -> false, line -> {
            transcript.append(line).append('\n');
            log.info("[agent:structural] {}", line);
        })) {
            Path exportDirectory = GeneratedExerciseExporter.export("structural", outcome, transcript.toString());
            log.info("=== EXPORTED (structural) to {} ===", exportDirectory.toAbsolutePath());
            var testFiles = outcome.producedFiles(RepositoryType.TESTS);
            assertThat(testFiles.keySet()).as("the platform generated and seeded a structure oracle (test.json)").anyMatch(path -> path.endsWith("test.json"));
            assertThat(testFiles.keySet()).as("the platform generated and seeded structural test classes").anyMatch(path -> path.endsWith("ClassTest.java"));
        }
    }

    // Realistic instructor prompts feed ONLY what a human would type (intent, no layout/verify/contract mechanics — the system prompt carries those), to surface honest quality.

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generatesFromBareTopic_realisticPrompt_endToEnd() throws Exception {
        ProgrammingExercise exercise = scaffoldExercise("HYPBT", true);
        runAndAssertAccepted(exercise, "A bubble sort exercise.", "realistic-bare-topic", outcome -> {
            assertThat(outcome.producedProblemStatement().toLowerCase()).as("the statement is about sorting").contains("sort");
            assertThat(outcome.producedProblemStatement()).as("problem statement binds tasks").contains("[task]");
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generatesFromPastedProblemStatement_realisticPrompt_endToEnd() throws Exception {
        // "Paste an existing problem statement": the instructor pastes finished prose (no [task] markup) and the agent builds solution/template/tests to match it.
        ProgrammingExercise exercise = scaffoldExercise("HYPLRU", true);
        String pasted = """
                We want a small exercise on the Least-Recently-Used (LRU) cache. Implement a cache that stores integer keys and integer values and is created with a fixed positive \
                capacity. It supports two operations: get, which returns the value stored for a key or -1 if the key is not present, and put, which inserts or updates a key with a \
                value. Whenever an operation touches a key — either a successful get or a put — that key becomes the most recently used. When a put would exceed the capacity, the \
                cache first evicts the key that has been used least recently to make room for the new entry. Updating the value of a key that already exists must not grow the size \
                and must also count as a use.

                For example, with capacity 2: after put(1, 1) and put(2, 2), a get(1) returns 1 and marks key 1 as most recently used. A following put(3, 3) exceeds the capacity, \
                so key 2 (the least recently used) is evicted; a get(2) then returns -1, while get(3) returns 3.

                Please turn this into a complete exercise for an intro data-structures course.""";
        runAndAssertAccepted(exercise, pasted, "realistic-pasted-lru", outcome -> {
            String statement = outcome.producedProblemStatement().toLowerCase();
            assertThat(statement).as("the statement preserves the pasted LRU intent").contains("lru").contains("evict");
            assertThat(outcome.producedProblemStatement()).as("problem statement binds tasks").contains("[task]");
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void fullFlow_draftedProblemStatementThenSpecModeGeneration_endToEnd() throws Exception {
        // The unified create-page flow: draft a problem statement from instructor requirements, then run agentic generation in SPEC MODE to build artifacts matching that draft.
        ProgrammingExercise exercise = scaffoldExercise("HYPSPEC", true);
        String instructions = "Create an exercise about a bounded stack of integers for a first-semester course: push, pop, peek, an isEmpty check, and a fixed capacity that "
                + "rejects a push when the stack is already full.";
        // Steps 1+2: draft the initial problem statement from the instructor's requirements (the real HyperionProblemStatementGenerationService, routed through the live model).
        String draftedStatement = problemStatementGenerationService.generateProblemStatement(exercise.getCourseViaExerciseGroupOrCourseMember(), instructions)
                .draftProblemStatement();
        log.info("=== DRAFTED PROBLEM STATEMENT (spec-flow) ===\n{}", draftedStatement);
        assertThat(draftedStatement).as("the draft problem statement was generated").isNotBlank();
        assertThat(AgentSystemPromptService.isNonTrivialProblemStatement(draftedStatement)).as("the draft is a real spec, so spec mode engages").isTrue();
        exercise.setProblemStatement(draftedStatement);

        // Step 3: "Generate with AI" — agentic generation in spec mode (the instruction the resource sends when the prompt is empty and a statement is present).
        String specInstruction = "An initial problem statement is already in problem-statement.md. Treat it as the authoritative specification and build the solution, template, and "
                + "tests to match it, keeping its intent and every stated requirement; refine its wording and add the [task] bindings for the tests you write.";
        runAndAssertAccepted(exercise, specInstruction, "spec-from-drafted-ps", outcome -> {
            String produced = outcome.producedProblemStatement();
            assertThat(produced.toLowerCase()).as("the produced statement preserves the drafted stack topic").contains("stack");
            assertThat(produced).as("the produced statement links the tests via [task] bindings").contains("[task]");
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generatesLargerMultiClassExercise_endToEnd() throws Exception {
        // Scale: a larger, multi-class Java exercise (several collaborating classes) to confirm the engine scales beyond a single class within the turn/context budget.
        ProgrammingExercise exercise = scaffoldExercise("HYPBIG", true);
        String prompt = "Create a Java exercise modelling a small library system with several collaborating classes: a Book (title, author, and an available flag), a Member (a name and "
                + "the books they have borrowed), and a Library that registers books and members and supports borrowing and returning a book by title. Borrowing makes a book "
                + "unavailable; returning makes it available again; borrowing an unavailable or unknown book, or by an unknown member, must be rejected. Cover the borrow/return rules "
                + "and the rejection cases with tests.";
        runAndAssertAccepted(exercise, prompt, "java-large-multiclass", outcome -> {
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION).keySet().stream().filter(path -> path.endsWith(".java")).count())
                    .as("the larger exercise produced multiple solution classes").isGreaterThanOrEqualTo(2);
            assertThat(outcome.producedProblemStatement()).as("problem statement binds tasks").contains("[task]");
        });
    }

    /**
     * The from-scratch language matrix: one entry per supported language, so a gated nightly run verifies generation across the breadth of Artemis languages and their per-language
     * {@link LanguageGenerationProfile} guidance. C cannot be scaffolded in this harness (see the note below) and compile-only languages (Assembler, VHDL) are incompatible with
     * the
     * differential oracle, so both are excluded.
     */
    static Stream<Arguments> fromScratchLanguageMatrix() {
        return applyLanguageFilter(Stream.of(
                matrixCase(ProgrammingLanguage.JAVASCRIPT, null, "HYPJS", "A JavaScript exercise: a Stack class over numbers with push, pop, peek and isEmpty.", "stack"),
                matrixCase(ProgrammingLanguage.TYPESCRIPT, null, "HYPTS", "A TypeScript exercise: a Stack class over numbers with push, pop, peek and isEmpty.", "stack"),
                matrixCase(ProgrammingLanguage.RUST, null, "HYPRS", "A Rust exercise: implement factorial(n: u64) -> u64 and fibonacci(n: u64) -> u64.", "factorial"),
                matrixCase(ProgrammingLanguage.C_PLUS_PLUS, null, "HYPCPP",
                        "A C++ exercise: a Stack of ints (push, pop, top, empty) declared in a header and implemented in a source file.", "stack"),
                matrixCase(ProgrammingLanguage.C_SHARP, null, "HYPCS",
                        "A C# exercise: a Calculator class with Add, Subtract, Multiply and Divide (Divide throws on division by zero).", "calculator"),
                matrixCase(ProgrammingLanguage.DART, null, "HYPDART", "A Dart exercise: functions reverseString and isPalindrome (ignoring case and spaces).", "palindrome"),
                matrixCase(ProgrammingLanguage.RUBY, null, "HYPRB", "A Ruby exercise: a Stack class with push, pop, peek and empty?.", "stack"),
                matrixCase(ProgrammingLanguage.R, null, "HYPR", "An R exercise: a function column_sums(mat) returning the sum of each column of a numeric matrix.", "column"),
                matrixCase(ProgrammingLanguage.HASKELL, null, "HYPHS", "A Haskell exercise: implement factorial :: Integer -> Integer and fibonacci :: Int -> Integer.",
                        "factorial"),
                matrixCase(ProgrammingLanguage.SWIFT, ProjectType.PLAIN, "HYPSW", "A Swift exercise: a Stack struct over Int with push, pop, peek and isEmpty.", "stack")));
    }

    private static Arguments matrixCase(ProgrammingLanguage language, ProjectType projectType, String shortName, String prompt, String topicKeyword) {
        return Arguments.of(language, projectType, shortName, prompt, topicKeyword);
    }

    /** Optional comma-separated language allowlist via {@code HYPERION_MATRIX_LANGS} (empty/unset runs the full matrix), so subsets can run in parallel isolated JVMs. */
    private static Stream<Arguments> applyLanguageFilter(Stream<Arguments> all) {
        String filter = System.getenv("HYPERION_MATRIX_LANGS");
        if (filter == null || filter.isBlank()) {
            return all;
        }
        java.util.Set<String> allowed = java.util.Arrays.stream(filter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return all.filter(arguments -> allowed.contains(((ProgrammingLanguage) arguments.get()[0]).name()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fromScratchLanguageMatrix")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generatesFromScratchExerciseForLanguage(ProgrammingLanguage language, ProjectType projectType, String shortName, String prompt, String topicKeyword) throws Exception {
        ProgrammingExercise exercise = scaffoldExercise(shortName, true, language, projectType);
        // The core guarantee is the differential acceptance (runAndAssertAccepted) plus well-formed [task] bindings; the topic keyword is logged for inspection but not
        // hard-asserted,
        // since on a from-scratch brief the agent legitimately has latitude over the exact topic.
        runAndAssertAccepted(exercise, prompt, "matrix-" + language.name().toLowerCase(), outcome -> {
            assertThat(outcome.producedProblemStatement()).as("%s problem statement binds tasks", language).contains("[task]");
            if (!outcome.producedProblemStatement().toLowerCase().contains(topicKeyword)) {
                log.info("[matrix:{}] note: produced statement does not contain the suggested topic keyword '{}'", language, topicKeyword);
            }
        });
    }

    // NOTE on C (and other root-sources languages like Go/Haskell/OCaml): the generation engine itself is language-agnostic (verify.sh runs the exercise's real per-language build
    // phases — templates/phases/c/gcc.yaml exists — and the verifier reads the JUnit/test-reports XML), but a C *starting* exercise cannot be scaffolded through
    // createProgrammingExercise in this harness: C ships only project-type-level templates (templates/c/gcc/…, no language-level templates/c/exercise/), and getRepositoryResources
    // loads the language-level path first, which ResourceLoaderService rejects when absent. That is an exercise-creation limitation, not a Hyperion one, so C is not covered here.

    private ProgrammingExercise scaffoldExercise(String shortName, boolean emptyRepositories) throws Exception {
        return scaffoldExercise(shortName, emptyRepositories, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);
    }

    private ProgrammingExercise scaffoldExercise(String shortName, boolean emptyRepositories, ProgrammingLanguage language, ProjectType projectType) throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course, language);
        exercise.setProjectType(projectType);
        exercise.setShortName(shortName);
        exercise.setTitle("Hyperion E2E " + shortName);
        exercise.setChannelName("hyp-" + shortName.toLowerCase());
        return creationService.createProgrammingExercise(exercise, emptyRepositories);
    }

    private de.tum.cit.aet.artemis.account.domain.User instructor() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }

    private void runAndAssertAccepted(ProgrammingExercise exercise, String prompt, String mode, Consumer<GenerationOutcome> extraChecks) {
        StringBuilder transcript = new StringBuilder();
        try (var outcome = orchestrator.generate(exercise, instructor(), prompt, "e2e-" + mode, () -> false, line -> {
            transcript.append(line).append('\n');
            log.info("[agent:{}] {}", mode, line);
        })) {
            // Export the full run (problem statement, all three repositories, verification, transcript) to disk for inspection and side-by-side diffing against the canonical
            // templates in src/main/resources/templates/. This is the durable artifact; the logs are just a live trace.
            Path exportDirectory = GeneratedExerciseExporter.export(mode, outcome, transcript.toString());
            log.info("=== EXPORTED ({}) to {} ===", mode, exportDirectory.toAbsolutePath());
            assertThat(outcome.loopResult()).as("agent loop produced a result").isNotNull();
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            log.info("=== VERIFICATION ({}) ===\n{}", mode, outcome.verification().report());
            // The real differential oracle, running the real build in the real container, must accept the exercise.
            assertThat(outcome.verification().solutionPassed()).as("solution passes its tests (%s)", mode).isTrue();
            assertThat(outcome.verification().templateFailed()).as("template compiles but fails the tests (%s)", mode).isTrue();
            assertThat(outcome.verification().testCount()).as("at least one test discovered (%s)", mode).isGreaterThan(0);
            assertThat(outcome.isAccepted()).as("exercise accepted by the differential oracle (%s)", mode).isTrue();
            // An accepted exercise must actually have produced solution and test files (guards against an empty-but-accepted edge and confirms the export has real content).
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION)).as("solution repository is non-empty (%s)", mode).isNotEmpty();
            assertThat(outcome.producedFiles(RepositoryType.TESTS)).as("tests repository is non-empty (%s)", mode).isNotEmpty();
            extraChecks.accept(outcome);
        }
    }
}

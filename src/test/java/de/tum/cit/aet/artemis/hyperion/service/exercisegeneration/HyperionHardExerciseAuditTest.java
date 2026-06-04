package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
 * ADVERSARIAL QUALITY AUDIT (additive, non-production). Drives REAL GPU+sandbox generations of NON-TRIVIAL, diverse-shape exercises that the existing trivial Stack/factorial
 * matrix never exercises: recursion-heavy recursive-descent parsers, stateful data structures with invariants, unicode/encoding edge cases, and generics/templates across a
 * diverse language set. Each method exports the full run to build/hyperion-e2e/<mode> and asserts the SAME acceptance gates runAndAssertAccepted uses, plus extra logging of the
 * quality dimensions (turn count, test count, [task] count) so the artifacts can be inspected by hand afterwards.
 * <p>
 * This is a NEW test file — it does not touch the verifier or shared E2E harness. Gated on HYPERION_E2E_GPU=true like the other GPU E2E tests.
 */
@EnabledIfEnvironmentVariable(named = "HYPERION_E2E_GPU", matches = "true")
class HyperionHardExerciseAuditTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final Logger log = LoggerFactory.getLogger(HyperionHardExerciseAuditTest.class);

    private static final String TEST_PREFIX = "hypaudit";

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
        // Override the placeholder build images (java/python/c++/rust/haskell — the hard-shape language set) and the agent context window in place on the shared beans, rather than
        // via @TestPropertySource which would fork a separate Spring context.
        HyperionGpuTestEnvironment.useProductionBuildImages(programmingLanguageConfiguration, ProgrammingLanguage.JAVA, ProgrammingLanguage.PYTHON, ProgrammingLanguage.C_PLUS_PLUS,
                ProgrammingLanguage.RUST, ProgrammingLanguage.HASKELL);
        HyperionGpuTestEnvironment.useGpuContextWindow(agentLoopRunner);
        GpuEndpointChatModel realModel = new GpuEndpointChatModel(GPU_BASE_URL, GPU_API_KEY, GPU_MODEL);
        when(azureOpenAiChatModel.call(ArgumentMatchers.any(Prompt.class))).thenAnswer((InvocationOnMock invocation) -> realModel.call(invocation.getArgument(0, Prompt.class)));
    }

    // ---- HARD SHAPE 1: recursion-heavy / multiple interacting functions — recursive-descent expression evaluator (Java)
    // ----------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void java_recursiveDescentExpressionEvaluator() throws Exception {
        ProgrammingExercise exercise = scaffold("HAUEVAL", true, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);
        String prompt = "Create a Java exercise on a recursive-descent calculator. The student implements an Evaluator class with a method `double evaluate(String expression)` "
                + "that parses and evaluates arithmetic expressions over doubles with +, -, *, /, parentheses, unary minus, and standard operator precedence and left-associativity "
                + "(e.g. \"2+3*4\" -> 14, \"(2+3)*4\" -> 20, \"-3+-2\" -> -5, \"2*-(1+1)\" -> -4). A malformed expression (unbalanced parentheses, a dangling operator, an unexpected "
                + "character) must throw IllegalArgumentException. Division by zero must throw ArithmeticException. Cover precedence, associativity, nesting, unary minus, whitespace "
                + "tolerance, and both error contracts.";
        runAndAssertAccepted(exercise, prompt, "hard-java-eval");
    }

    // ---- HARD SHAPE 2: stateful data structure with invariants — directed graph with cycle detection + topological sort (Java)
    // ----------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void java_directedGraphTopologicalSort() throws Exception {
        ProgrammingExercise exercise = scaffold("HAUGRAPH", true, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN);
        String prompt = "Create a Java exercise on a directed graph. The student implements a Graph class with addNode(int id), addEdge(int from, int to), boolean hasCycle(), and "
                + "List<Integer> topologicalSort() which returns a valid topological ordering of the nodes or throws IllegalStateException when the graph has a cycle. addEdge "
                + "referencing an unknown node must throw IllegalArgumentException. Cover: a DAG's topo order respects every edge, a self-loop and a longer cycle are detected, "
                + "topologicalSort throws on a cyclic graph, the empty graph, a single node, and the unknown-node error contract.";
        runAndAssertAccepted(exercise, prompt, "hard-java-graph");
    }

    // ---- HARD SHAPE 3: strings / unicode / encoding edge cases (Python)
    // -----------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void python_unicodeTextProcessing() throws Exception {
        ProgrammingExercise exercise = scaffold("HAUUNI", true, ProgrammingLanguage.PYTHON, null);
        String prompt = "Create a Python exercise on UNICODE-AWARE text processing. The student implements, in a module, a function normalize_whitespace(s) that collapses runs of "
                + "any Unicode whitespace to a single ASCII space and strips ends; a function count_graphemes(s) that returns the number of user-perceived characters (so a base "
                + "letter plus a combining accent, e.g. 'e' + U+0301, counts as ONE, and an emoji counts as one); and a function reverse_preserving_graphemes(s) that reverses the "
                + "string without splitting any combining sequence. The exercise MUST be tested on real non-ASCII input: accented Latin (café), a combining-mark sequence, CJK "
                + "characters, and at least one emoji. Make sure the tests assert the Unicode behaviour, not just ASCII.";
        runAndAssertAccepted(exercise, prompt, "hard-python-unicode");
    }

    // ---- HARD SHAPE 4: generics / templates + multi-file header/source (C++)
    // -------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void cpp_genericRingBuffer() throws Exception {
        ProgrammingExercise exercise = scaffold("HAURING", true, ProgrammingLanguage.C_PLUS_PLUS, null);
        String prompt = "Create a C++ exercise on a GENERIC fixed-capacity ring buffer: a class template RingBuffer<T> (header-only is fine, declared in a header) with a "
                + "constructor taking a capacity, push(const T&) that overwrites the oldest element when full, pop() returning the oldest (throwing std::out_of_range when empty), "
                + "size(), capacity(), and full(). Test it at MULTIPLE instantiations: RingBuffer<int> and RingBuffer<std::string>, including the wrap-around overwrite behaviour, "
                + "the empty-pop exception, and the full/size invariants across a sequence of pushes and pops.";
        runAndAssertAccepted(exercise, prompt, "hard-cpp-generic");
    }

    // ---- HARD SHAPE 5: generics + trait (Rust)
    // ------------------------------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rust_genericBinarySearchTree() throws Exception {
        ProgrammingExercise exercise = scaffold("HAUBST", true, ProgrammingLanguage.RUST, null);
        String prompt = "Create a Rust exercise on a GENERIC binary search tree: a struct Bst<T: Ord> with new(), insert(&mut self, value: T), contains(&self, value: &T) -> bool, "
                + "len(&self) -> usize, and in_order(&self) -> Vec<T> where T: Clone returning the values in sorted order. Test it with i32 AND with String, covering insertion "
                + "ordering, duplicate handling (a duplicate is not inserted twice), contains on present and absent values, the empty tree, and that in_order is sorted for a "
                + "shuffled insertion sequence.";
        runAndAssertAccepted(exercise, prompt, "hard-rust-generic");
    }

    // ---- HARD SHAPE 6: typed FP / recursion (Haskell)
    // -----------------------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void haskell_recursiveJsonValue() throws Exception {
        ProgrammingExercise exercise = scaffold("HAUJSON", true, ProgrammingLanguage.HASKELL, null);
        String prompt = "Create a Haskell exercise on a small recursive data type: data Json = JNull | JBool Bool | JNumber Double | JString String | JArray [Json] | "
                + "JObject [(String, Json)]. The student implements render :: Json -> String that serialises a Json value to compact JSON text (no spaces), and depth :: Json -> Int "
                + "that returns the maximum nesting depth (a scalar has depth 1, an array/object adds one to the max depth of its children, an empty array/object has depth 1). Cover "
                + "scalars, nested arrays and objects, the empty cases, and a deeply nested mixed value.";
        runAndAssertAccepted(exercise, prompt, "hard-haskell-recursive");
    }

    // ---- shared driver --------------------------------------------------------------------------------------------------------------------------------------------------------

    private void runAndAssertAccepted(ProgrammingExercise exercise, String prompt, String mode) {
        runAndAssertAccepted(exercise, prompt, mode, outcome -> {
        });
    }

    private void runAndAssertAccepted(ProgrammingExercise exercise, String prompt, String mode, Consumer<GenerationOutcome> extraChecks) {
        StringBuilder transcript = new StringBuilder();
        try (var outcome = orchestrator.generate(exercise, instructor(), prompt, "audit-" + mode, () -> false, line -> {
            transcript.append(line).append('\n');
            log.info("[audit:{}] {}", mode, line);
        })) {
            Path exportDirectory = GeneratedExerciseExporter.export(mode, outcome, transcript.toString());
            log.info("=== EXPORTED ({}) to {} ===", mode, exportDirectory.toAbsolutePath());
            // Rich quality logging so the artifacts can be read by hand afterwards regardless of pass/fail.
            log.info("=== AUDIT SUMMARY ({}) turns={} status={} accepted={} ===", mode, outcome.loopResult().turns(), outcome.loopResult().status(), outcome.isAccepted());
            if (outcome.verification() != null) {
                log.info("=== VERIFICATION ({}) testCount={} solutionPassed={} templateFailed={} ===%n{}", mode, outcome.verification().testCount(),
                        outcome.verification().solutionPassed(), outcome.verification().templateFailed(), outcome.verification().report());
            }
            String ps = outcome.producedProblemStatement();
            int taskCount = countOccurrences(ps, "[task]");
            Map<String, String> solutionFiles = outcome.producedFiles(RepositoryType.SOLUTION);
            Map<String, String> testFiles = outcome.producedFiles(RepositoryType.TESTS);
            log.info("=== QUALITY ({}) problemStatementChars={} taskBindings={} solutionFiles={} testFiles={} ===", mode, ps.length(), taskCount, solutionFiles.keySet(),
                    testFiles.keySet());

            assertThat(outcome.loopResult()).as("agent loop produced a result").isNotNull();
            assertThat(outcome.verification()).as("verification ran").isNotNull();
            assertThat(outcome.verification().solutionPassed()).as("solution passes its tests (%s)", mode).isTrue();
            assertThat(outcome.verification().templateFailed()).as("template compiles but fails the tests (%s)", mode).isTrue();
            assertThat(outcome.verification().testCount()).as("at least one test discovered (%s)", mode).isGreaterThan(0);
            assertThat(outcome.isAccepted()).as("exercise accepted by the differential oracle (%s)", mode).isTrue();
            assertThat(outcome.producedFiles(RepositoryType.SOLUTION)).as("solution repository is non-empty (%s)", mode).isNotEmpty();
            assertThat(outcome.producedFiles(RepositoryType.TESTS)).as("tests repository is non-empty (%s)", mode).isNotEmpty();
            assertThat(ps).as("problem statement binds tasks (%s)", mode).contains("[task]");
            extraChecks.accept(outcome);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private ProgrammingExercise scaffold(String shortName, boolean emptyRepositories, ProgrammingLanguage language, ProjectType projectType) throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course, language);
        exercise.setProjectType(projectType);
        exercise.setShortName(shortName);
        exercise.setTitle("Hyperion Hard Audit " + shortName);
        exercise.setChannelName("hyp-audit-" + shortName.toLowerCase());
        return creationService.createProgrammingExercise(exercise, emptyRepositories);
    }

    private de.tum.cit.aet.artemis.account.domain.User instructor() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }
}

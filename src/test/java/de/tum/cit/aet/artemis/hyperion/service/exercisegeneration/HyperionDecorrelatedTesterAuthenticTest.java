package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.HarmonyScrubbingChatModel;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.TesterAgentTools;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.IndependentTesterAgentService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.CorrectnessCrossCheck;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.CorrectnessCrossCheckService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * GPU-gated authentic proof of the LIVE half of Design F: a real independent-examiner agent, given ONLY the {@code realistic-pasted-lru} problem statement + template (never the
 * reference solution), authors a test suite that CATCHES the co-authored false-accept — its suite FAILS on the buggy LRU solution (which evicts the wrong key) and PASSES on a
 * correct one.
 * <p>
 * It seeds the sandbox containers directly with the checked-in fixture (no git/persistence), runs the real tester loop through {@link AgentLoopRunner} + {@link TesterAgentTools}
 * (which provably cannot read {@code solution/} — it is never seeded), and runs the authored suite against the real solution/template via {@link CorrectnessCrossCheckService}.
 * Gated
 * on {@code HYPERION_E2E_GPU=true} and Docker — a manual/nightly run, left for a human to execute (used to measure the false-reject rate before enabling
 * {@code reject-on-contradiction}).
 */
@EnabledIfEnvironmentVariable(named = "HYPERION_E2E_GPU", matches = "true")
@EnabledIf("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.HyperionMockedLlmE2eSupport#isDockerAvailable")
class HyperionDecorrelatedTesterAuthenticTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final Logger log = LoggerFactory.getLogger(HyperionDecorrelatedTesterAuthenticTest.class);

    private static final String GPU_BASE_URL = System.getenv().getOrDefault("GPU_BASE_URL", "https://staging.hephaestus.aet.cit.tum.de/logos/v1");

    private static final String GPU_API_KEY = System.getenv("GPU_API_KEY");

    private static final String GPU_MODEL = System.getenv().getOrDefault("GPU_MODEL", "openai/gpt-oss-120b");

    // The realistic-pasted-lru fixture, embedded so the test is committed and self-contained (build/hyperion-e2e is gitignored).
    private static final String PROBLEM_STATEMENT = """
            # Implement an integer LRU cache

            Implement a fixed-capacity Least-Recently-Used (LRU) cache for integer keys and integer values, storing at most *capacity* entries.

            - `int get(int key)`: returns the value for *key* or -1 if absent; a successful get marks the key most recently used.
            - `void put(int key, int value)`: inserts or updates; the key becomes most recently used; if inserting would exceed the capacity, the LEAST-recently-used entry is evicted first.

            Updating an existing key must not increase the number of stored entries. A non-positive capacity must make the constructor throw `IllegalArgumentException`.

            ## Tasks
            [task][Constructor rejects non-positive capacity](illegalCapacityThrows)
            [task][get on a missing key returns -1](getNonExistingReturnsMinusOne)
            [task][put then get](putAndGetSimple)
            [task][updating an existing key does not grow the cache](updateExistingDoesNotIncreaseSize)
            [task][eviction after capacity is exceeded](capacityEvictionExample)
            """;

    private static final String TEMPLATE_SOURCE = """
            package de.test;

            public class LRUCache {
                public LRUCache(int capacity) {
                    throw new UnsupportedOperationException("Not implemented");
                }
                public int get(int key) {
                    throw new UnsupportedOperationException("Not implemented");
                }
                public void put(int key, int value) {
                    throw new UnsupportedOperationException("Not implemented");
                }
            }
            """;

    /** The BUGGY solution: new entries are inserted at the LRU end (addLast) while eviction removes tail.prev, so put(1),put(2),put(3) evicts key 2 instead of the LRU key 1. */
    private static final String BUGGY_SOLUTION = """
            package de.test;
            import java.util.HashMap;
            import java.util.Map;

            public class LRUCache {
                private final int capacity;
                private final Map<Integer, Node> map = new HashMap<>();
                private final Node head = new Node(0, 0);
                private final Node tail = new Node(0, 0);

                private static class Node { int key; int value; Node prev; Node next; Node(int k, int v) { key = k; value = v; } }

                public LRUCache(int capacity) {
                    if (capacity <= 0) { throw new IllegalArgumentException("capacity must be positive"); }
                    this.capacity = capacity; head.next = tail; tail.prev = head;
                }
                public int get(int key) {
                    Node n = map.get(key);
                    if (n == null) { return -1; }
                    remove(n); addFirst(n); return n.value;
                }
                public void put(int key, int value) {
                    Node n = map.get(key);
                    if (n != null) { n.value = value; remove(n); addFirst(n); return; }
                    if (map.size() >= capacity) { Node lru = tail.prev; remove(lru); map.remove(lru.key); }
                    Node nn = new Node(key, value); map.put(key, nn); addLast(nn);
                }
                private void remove(Node n) { n.prev.next = n.next; n.next.prev = n.prev; }
                private void addFirst(Node n) { n.next = head.next; n.prev = head; head.next.prev = n; head.next = n; }
                private void addLast(Node n) { n.prev = tail.prev; n.next = tail; tail.prev.next = n; tail.prev = n; }
            }
            """;

    /** The CORRECT solution: new entries go to the MRU end (addFirst), eviction removes tail.prev (the true LRU). */
    private static final String CORRECT_SOLUTION = BUGGY_SOLUTION.replace("Node nn = new Node(key, value); map.put(key, nn); addLast(nn);",
            "Node nn = new Node(key, value); map.put(key, nn); addFirst(nn);");

    private static final String TESTS_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
                <groupId>de.test</groupId><artifactId>Hyperion-E2E-HYPLRU-Tests</artifactId><packaging>jar</packaging><version>1.0</version>
                <dependencies><dependency><groupId>de.tum.in.ase</groupId><artifactId>artemis-java-test-sandbox</artifactId><version>1.15.0</version></dependency></dependencies>
                <build><sourceDirectory>${project.basedir}/assignment/src</sourceDirectory><testSourceDirectory>${project.basedir}/test</testSourceDirectory>
                    <testResources><testResource><directory>${project.basedir}/test</directory></testResource></testResources>
                    <plugins>
                        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version>
                            <configuration><source>17</source><target>17</target></configuration></plugin>
                        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version></plugin>
                    </plugins>
                </build>
            </project>
            """;

    /** The produced-artifact fixtures the examiner is seeded from THROUGH the production path ({@link IndependentTesterAgentService#authorShadowSuite}). */
    private static final Map<String, String> FIXTURE_TEMPLATE_FILES = Map.of("src/de/test/LRUCache.java", TEMPLATE_SOURCE);

    /** Harness only (no sample test sources), so {@code stripSampleTestSources} is a no-op here; the examiner authors the tests itself. */
    private static final Map<String, String> FIXTURE_TESTS_HARNESS = Map.of("pom.xml", TESTS_POM);

    @Autowired
    private Optional<InteractiveSandbox> interactiveSandbox;

    @Autowired
    private IndependentTesterAgentService independentTesterAgent;

    @Autowired
    private AgentLoopRunner agentLoopRunner;

    @Autowired
    private GenerationWorkspaceService workspace;

    @Autowired
    private CorrectnessCrossCheckService correctnessCrossCheckService;

    @Autowired
    private BuildPhasesTemplateService buildPhasesTemplateService;

    @Autowired
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private Map<ProgrammingLanguage, String> replacedBuildImages;

    @BeforeEach
    void setUp() {
        replacedBuildImages = HyperionMockedLlmE2eSupport.useProductionBuildImages(programmingLanguageConfiguration, ProgrammingLanguage.JAVA);
        HyperionGpuTestEnvironment.useGpuContextWindow(agentLoopRunner);
        // Drive the real agent loop with the real GPU model through the production transport, delegating the injected mock ChatModel (same pattern as the authentic grading test).
        OpenAiChatOptions options = OpenAiChatOptions.builder().baseUrl(GPU_BASE_URL).apiKey(GPU_API_KEY).model(GPU_MODEL).build();
        ChatModel wrapped = new HarmonyScrubbingChatModel(OpenAiChatModel.builder().options(options).build());
        when(azureOpenAiChatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model(GPU_MODEL).build());
        when(azureOpenAiChatModel.call(ArgumentMatchers.any(Prompt.class))).thenAnswer((InvocationOnMock invocation) -> wrapped.call(invocation.getArgument(0, Prompt.class)));
    }

    /**
     * How many independent-examiner attempts to allow. The MECHANISM is deterministic; the examiner's per-run reliability at authoring the discriminating test is model-bounded.
     */
    private static final int MAX_EXAMINER_ATTEMPTS = 4;

    /**
     * Proves the decorrelated mechanism can EXPOSE the co-authored false-accept through the production seeding path. Honest scope: the harness is correct by construction, but the
     * examiner here is the SAME model as the author (gpt-oss-120b), so it (a) is unreliable at authoring the subtle put,put,put insertion-order test and (b) can inherit the
     * author's
     * blind spot — a single run catches the bug only sometimes. So we allow a few attempts and assert the mechanism exposes it in AT LEAST ONE (and that the catching suite does
     * not
     * false-reject a correct solution). Improving per-run catch reliability is an LLM lever (a stronger, or genuinely model-decorrelated, examiner), not a harness one.
     */
    @Test
    void liveExaminerCatchesTheBuggyLruFalseAccept() throws Exception {
        InteractiveSandbox sandbox = interactiveSandbox.orElseThrow(() -> new IllegalStateException("no sandbox bean on this node"));
        ProgrammingExercise exercise = lruExercise();

        boolean exposed = false;
        for (int attempt = 1; attempt <= MAX_EXAMINER_ATTEMPTS && !exposed; attempt++) {
            final int currentAttempt = attempt;
            // LIVE independent examiner THROUGH THE PRODUCTION PATH: authorShadowSuite owns its own solution-free session, seeds from the produced template + tests maps (never the
            // solution), runs the real tester loop, and reads the authored suite back out — validating seedTesterWorkspace(...produced maps), not a hand-seed.
            // Decorrelation-by-absence
            // is proven deterministically by GenerationWorkspaceServiceTesterSeedingTest.
            Map<String, String> shadowSuite = independentTesterAgent.authorShadowSuite(exercise, FIXTURE_TEMPLATE_FILES, FIXTURE_TESTS_HARNESS, () -> false, null,
                    line -> log.info("[tester attempt {}] {}", currentAttempt, line));
            if (shadowSuite.isEmpty()) {
                continue;
            }
            CorrectnessCrossCheck.Status onBuggy = runCrossCheck(sandbox, exercise, BUGGY_SOLUTION, shadowSuite).status();
            log.info("[examiner attempt {}] cross-check on the buggy solution = {}", attempt, onBuggy);
            if (onBuggy == CorrectnessCrossCheck.Status.CONTRADICTION) {
                exposed = true;
                // The suite that exposed the bug must NOT reject a correct solution (a catching suite that also false-rejects would be useless).
                assertThat(runCrossCheck(sandbox, exercise, CORRECT_SOLUTION, shadowSuite).status()).as("the catching suite does not wrongly reject a correct solution")
                        .isNotEqualTo(CorrectnessCrossCheck.Status.CONTRADICTION);
            }
        }
        assertThat(exposed).as("the decorrelated examiner exposed the co-authored LRU false-accept (CONTRADICTION on the buggy solution) within %d attempts", MAX_EXAMINER_ATTEMPTS)
                .isTrue();
    }

    /**
     * FALSE-REJECT measurement: the full examiner, through the real seeding path, must NOT reject a KNOWN-GOOD exercise. This is the guard a human runs to confirm that enabling
     * the
     * advisory gate (and, later, {@code reject-on-contradiction}) will not wrongly reject correct exercises.
     */
    @Test
    void liveExaminerDoesNotFalseRejectAKnownGoodExercise() throws Exception {
        InteractiveSandbox sandbox = interactiveSandbox.orElseThrow(() -> new IllegalStateException("no sandbox bean on this node"));
        ProgrammingExercise exercise = lruExercise();

        Map<String, String> shadowSuite = independentTesterAgent.authorShadowSuite(exercise, FIXTURE_TEMPLATE_FILES, FIXTURE_TESTS_HARNESS, () -> false, null,
                line -> log.info("[tester] {}", line));
        assertThat(shadowSuite).as("the examiner authored a suite against the produced template map").isNotEmpty();

        // The gate rejects ONLY on CONTRADICTION (a correct solution failing an independent test = a real false-reject). CONSISTENT and INCONCLUSIVE both mean "not rejected":
        // INCONCLUSIVE is the safe fail-open when this examiner run's suite does not compile against the produced API — no rejection, just an ineffective (no-op) run. Whether the
        // examiner reliably produces a compiling, discriminating suite is bounded by the examiner MODEL's quality, not the harness; the harness guarantee under test is only that a
        // correct exercise is never wrongly rejected.
        assertThat(runCrossCheck(sandbox, exercise, CORRECT_SOLUTION, shadowSuite).status()).as("enabling the gate must never wrongly REJECT a correct exercise (CONTRADICTION)")
                .isNotEqualTo(CorrectnessCrossCheck.Status.CONTRADICTION);
    }

    /** Runs the cross-check against a container seeded with the given solution + the template, using the live-authored shadow suite. */
    private CorrectnessCrossCheck runCrossCheck(InteractiveSandbox sandbox, ProgrammingExercise exercise, String solutionSource, Map<String, String> shadowSuite) {
        String session = sandbox.createSession(workspace.sessionSpec(exercise));
        try {
            Map<String, String> seed = new LinkedHashMap<>();
            seed.put("solution/src/de/test/LRUCache.java", solutionSource);
            seed.put("template/src/de/test/LRUCache.java", TEMPLATE_SOURCE);
            seed(sandbox, session, seed);
            return correctnessCrossCheckService.runAgainstShadowSuite(sandbox, session, exercise, shadowSuite);
        }
        finally {
            sandbox.destroySession(session);
        }
    }

    private ProgrammingExercise lruExercise() throws Exception {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);
        exercise.setProblemStatement(PROBLEM_STATEMENT);
        exercise.setStaticCodeAnalysisEnabled(false);
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        exercise.setBuildConfig(buildConfig);
        buildConfig.setBuildScript(null);
        buildConfig.setBuildPlanConfiguration(
                new BuildPlanPhasesDTO(buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise), buildPhasesTemplateService.getDefaultDockerImageFor(exercise))
                        .toBuildPlanConfiguration());
        return exercise;
    }

    /** Seeds the given path -> content files into {@code /workspace} of the session in one tar; {@code .sh} files are made executable. */
    private static void seed(InteractiveSandbox sandbox, String sessionId, Map<String, String> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, String> entry : files.entrySet()) {
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry tarEntry = new TarArchiveEntry(entry.getKey());
                tarEntry.setSize(bytes.length);
                tarEntry.setMode(entry.getKey().endsWith(".sh") ? 0755 : 0644);
                tar.putArchiveEntry(tarEntry);
                tar.write(bytes);
                tar.closeArchiveEntry();
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        sandbox.copyIn(sessionId, "/workspace", new ByteArrayInputStream(out.toByteArray()));
    }
}

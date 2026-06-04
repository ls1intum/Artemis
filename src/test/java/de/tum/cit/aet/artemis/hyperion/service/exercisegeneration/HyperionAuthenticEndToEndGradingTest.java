package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.dockerclient.TransportConfig;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.transport.SSLConfig;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.service.LocalCIEventListenerService;
import de.tum.cit.aet.artemis.localci.service.LocalCIResultListenerService;
import de.tum.cit.aet.artemis.localci.service.LocalCIResultProcessingService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * The culminating, fully-authentic Hyperion end-to-end test: real agentic GENERATION → real versioned PERSISTENCE → real LocalCI/LocalVC GRADING, with NOTHING mocked or faked in
 * the generation, persistence, sandbox, or grading paths.
 * <p>
 * <b>What this proves that nothing else does.</b> Every prior Hyperion acceptance signal stops at the in-sandbox {@code verify.sh} differential oracle
 * ({@link AuthoritativeVerificationService}). That oracle is a <i>proxy</i> for production grading. The deterministic {@code HyperionGeneratedExerciseGradingIntegrationTest}
 * proved
 * that the canonical (model-free) Java exercise grades correctly on the real LocalCI path, and {@code HyperionExerciseGenerationEndToEndTest} proved that the real model + real
 * sandbox produce an oracle-accepted exercise. Neither connected the two: nothing proved that the <i>model-generated</i> exercise, once <i>persisted through production</i>,
 * actually
 * grades correctly on the real pipeline. This test closes that loop for every generation-capable language.
 * <p>
 * <b>The chain, per (language × projectType).</b>
 * <ol>
 * <li><b>Generate</b> — scaffold through the production {@link ProgrammingExerciseCreationUpdateService#createProgrammingExercise(ProgrammingExercise, boolean)} path, then run the
 * real {@link ExerciseGenerationOrchestrationService#generate} which drives the real {@code InteractiveSandbox} (a real Docker container), the real agent loop (the mocked
 * {@code ChatModel} delegates to the live GPU endpoint), and the real differential oracle. We assert the oracle accepted.</li>
 * <li><b>Persist</b> — feed the real {@link GenerationOutcome} into the production {@link GenerationPersistenceService#persist} (commit order template → solution → tests, problem
 * statement update, real tests-build trigger for test-case synchronisation, and {@code ExerciseVersion} creation). We assert a version was recorded and the synchronised
 * {@link de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase} set is non-empty.</li>
 * <li><b>Grade</b> — on the real LocalCI Docker build + LocalVC checkout + Ares/JUnit report parsing + {@code ProgrammingExerciseGradingService} scoring: a student submission of
 * the
 * persisted SOLUTION scores 100% (every synchronised test case passes); a student submission of the persisted TEMPLATE scores 0% (it compiles but behaviourally fails). The score
 * comes from the production {@link Result#getScore()}, not from any sandbox proxy.</li>
 * </ol>
 * <p>
 * <b>Feasibility — sandbox vs LocalCI DockerClient coexistence (the crux).</b> Both the interactive sandbox and the LocalCI build path obtain their Docker client from the single
 * accessor {@code BuildAgentConfiguration#getDockerClient()} ({@link de.tum.cit.aet.artemis.buildagent.service.InteractiveSandboxService} calls it for every container op; the
 * build
 * agent calls it for every build). This test installs ONE real {@code DockerClient} on that accessor (the exact {@code switchToRealDockerClient} reflection plumbing from
 * {@code LocalCIDockerImageIntegrationTest}), so the real sandbox generation and the real LocalCI grading share it. There is no second Docker access path to reconcile.
 * <p>
 * <b>Gating.</b> Gated on BOTH {@code HYPERION_E2E_GPU=true} (it calls the external GPU model) and {@code @EnabledIf("isDockerAvailable")} (it pulls/runs ~1 GB execution images
 * and
 * runs real builds). It is a manual / nightly evaluation, never part of the fast CI suite.
 * <p>
 * <b>The orphaned-harness divergence this test once exposed, now CLOSED.</b> For a FROM-SCRATCH generation ({@code emptyRepositories=true}), the scaffold keeps the canonical
 * sample's tests repo VERBATIM ({@code ProgrammingExerciseRepositoryService}: "the test repository is kept intact") — including the sample's behaviour test and structure oracle
 * for
 * a DIFFERENT exercise (Java {@code SortingExampleBehaviorTest} + {@code test.json} for SortStrategy/Context/Policy; Python {@code behavior/behavior_test.py} +
 * {@code structural/structural_test.py}). The agent removes those sample test sources INSIDE the sandbox and writes its own, and {@link StructuralOracleSeedingService} regenerates
 * {@code test.json} for the generated classes; the sandbox {@code verify.sh} oracle runs that sandbox-final set and accepts. The bug was in PERSISTENCE: it used to OVERLAY the
 * produced files onto the scaffolded git tree, re-orphaning the canonical sources into the persisted tests repo, where real Artemis grading ran them and the different-topic
 * solution failed them (Java → ~44%, Python → 0%) despite the sandbox accepting. {@link GenerationPersistenceService#persist} now makes the committed tree MIRROR the sandbox-final
 * tree (deleting every tracked file the agent did not produce, except the immutable build harness), so the persisted tests repo contains ONLY the generated exercise's tests and
 * the
 * regenerated oracle. Both a FROM-SCRATCH Java PLAIN_MAVEN ({@link #javaPlainMaven_fromScratch_generatePersistGrade_authenticEndToEnd}) and the from-scratch non-Java matrix legs
 * now
 * grade solution 100% / template 0% on the REAL pipeline with NO orphaned structural cases (asserted explicitly). The {@code emptyRepositories=false} Java canonical-ADAPT scenario
 * still works too (the agent keeps the canonical harness, so nothing is orphaned); the deterministic proof that a canonical exercise persisted through this path grades 100% / 0%
 * lives in {@code HyperionGeneratedExerciseGradingIntegrationTest}.
 * <p>
 * <b>Residual, distinct divergence (NOT the orphan bug).</b> The sandbox oracle accepts a template that fails at least HALF its tests, plus the strict gate that every
 * {@code [task]}-bound test fails on the template; an unbound test whose placeholder accidentally returns the expected value can still pass on the real template, so a model that
 * under-binds or picks a too-lucky placeholder can land the real template above 0%. That is a template-QUALITY concern orthogonal to the orphaned-harness fix; each leg surfaces it
 * loudly (the divergence note + full feedback) rather than hiding it.
 */
@EnabledIfEnvironmentVariable(named = "HYPERION_E2E_GPU", matches = "true")
@EnabledIf("isDockerAvailable")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class HyperionAuthenticEndToEndGradingTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final Logger log = LoggerFactory.getLogger(HyperionAuthenticEndToEndGradingTest.class);

    private static final String TEST_PREFIX = "hypauth";

    private static final String GPU_BASE_URL = System.getenv().getOrDefault("GPU_BASE_URL", "https://gpu.ase.cit.tum.de");

    private static final String GPU_API_KEY = System.getenv("GPU_API_KEY");

    private static final String GPU_MODEL = System.getenv().getOrDefault("GPU_MODEL", "openai/gpt-oss-120b");

    private static final Duration BUILD_JOB_CREATION_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration TEST_CASE_SYNC_TIMEOUT = Duration.ofMinutes(10);

    @Autowired
    private ExerciseGenerationOrchestrationService orchestrator;

    @Autowired
    private GenerationPersistenceService persistenceService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseCreationUpdateService creationService;

    @Autowired
    private de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    private ExerciseVersionTestRepository exerciseVersionRepository;

    @Autowired
    private de.tum.cit.aet.artemis.buildagent.service.BuildAgentDockerService buildAgentDockerService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private de.tum.cit.aet.artemis.core.service.TempFileUtilService tempFileUtilService;

    @Autowired
    private de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Autowired
    private AgentLoopRunner agentLoopRunner;

    private LocalCIResultListenerService localCIResultListenerService;

    private DockerClient realDockerClient;

    private String originalDockerConnectionUri;

    private String originalImageArchitecture;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    // ---- Docker gating (identical to LocalCIDockerImageIntegrationTest / HyperionGeneratedExerciseGradingIntegrationTest) -----------------------------------------------------

    static boolean isDockerAvailable() {
        TransportConfig dockerTransportConfig = discoverDockerTransportConfig();
        if (dockerTransportConfig == null) {
            return false;
        }
        try (DockerClient dockerClient = createDockerClient(dockerTransportConfig)) {
            dockerClient.versionCmd().exec();
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    private static TransportConfig discoverDockerTransportConfig() {
        DockerClientFactory dockerClientFactory = DockerClientFactory.instance();
        if (!dockerClientFactory.isDockerAvailable()) {
            return null;
        }
        return dockerClientFactory.getTransportConfig();
    }

    private static DockerClient createDockerClient(TransportConfig dockerTransportConfig) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerTransportConfig.getDockerHost().toString()).build();
        SSLConfig sslConfig = dockerTransportConfig.getSslConfig();
        if (sslConfig == null) {
            sslConfig = config.getSSLConfig();
        }
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(sslConfig).connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(45)).build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    // ---- Real Docker wiring shared by the sandbox (generation) AND the LocalCI build (grading)
    // -----------------------------------------------------------------------------------
    // Both go through BuildAgentConfiguration#getDockerClient(): InteractiveSandboxService for every container op, and the build agent for every build. Installing one real client
    // here (the exact LocalCIDockerImageIntegrationTest plumbing) makes the real sandbox generation and the real LocalCI grading share a single real Docker daemon connection.

    @BeforeEach
    void switchToRealDockerClient() {
        initializeLazyLocalCIServices();
        TransportConfig dockerTransportConfig = Objects.requireNonNull(discoverDockerTransportConfig());
        originalDockerConnectionUri = (String) ReflectionTestUtils.getField(buildAgentConfiguration, "dockerConnectionUri");
        originalImageArchitecture = (String) ReflectionTestUtils.getField(buildAgentDockerService, "imageArchitecture");
        buildAgentConfiguration.closeBuildAgentServices();
        ReflectionTestUtils.setField(buildAgentConfiguration, "dockerConnectionUri", dockerTransportConfig.getDockerHost().toString());
        buildAgentConfiguration.openBuildAgentServices();
        realDockerClient = (DockerClient) ReflectionTestUtils.getField(buildAgentConfiguration, "dockerClient");
        doReturn(realDockerClient).when(buildAgentConfiguration).getDockerClient();
        doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
        dockerClient = realDockerClient;
        String architecture = normalizeDockerArchitecture(realDockerClient.infoCmd().exec().getArchitecture());
        log.info("Running Hyperion authentic E2E with Docker architecture: {}", architecture);
        ReflectionTestUtils.setField(buildAgentDockerService, "imageArchitecture", architecture);
        distributedDataAccessService.getDistributedBuildJobQueue().clear();
        distributedDataAccessService.getDistributedProcessingJobs().clear();
        distributedDataAccessService.getDistributedBuildResultQueue().clear();
        sharedQueueProcessingService.resetInitializedState();
        sharedQueueProcessingService.setPauseState(false);
        sharedQueueProcessingService.init();
        sharedQueueProcessingService.updateBuildAgentInformation();

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        // The base class points every build image at a placeholder so the normal (mocked-build) buckets never pull one. Override each language back to its production execution
        // image (the same images the Hyperion GPU E2E uses) so both the sandbox and the LocalCI grading build run on the real toolchain, and point the agent's window-aware
        // compaction at the gpt-oss-120b deployment's real 65536-token context window. Done in-place on the shared beans rather than via @TestPropertySource so no separate Spring
        // context is forked.
        HyperionGpuTestEnvironment.useProductionBuildImages(programmingLanguageConfiguration);
        HyperionGpuTestEnvironment.useGpuContextWindow(agentLoopRunner);

        // Drive the real agent loop with the real GPU model by making the (mocked) ChatModel bean delegate to the live endpoint.
        GpuEndpointChatModel realModel = new GpuEndpointChatModel(GPU_BASE_URL, GPU_API_KEY, GPU_MODEL);
        when(azureOpenAiChatModel.call(ArgumentMatchers.any(Prompt.class))).thenAnswer((InvocationOnMock invocation) -> realModel.call(invocation.getArgument(0, Prompt.class)));
    }

    @AfterEach
    void tearDownRealDockerClient() {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
        distributedDataAccessService.getDistributedBuildJobQueue().clear();
        distributedDataAccessService.getDistributedProcessingJobs().clear();
        distributedDataAccessService.getDistributedBuildResultQueue().clear();
        buildAgentConfiguration.closeBuildAgentServices();
        realDockerClient = null;
        if (originalDockerConnectionUri != null) {
            ReflectionTestUtils.setField(buildAgentConfiguration, "dockerConnectionUri", originalDockerConnectionUri);
            originalDockerConnectionUri = null;
        }
        if (originalImageArchitecture != null) {
            ReflectionTestUtils.setField(buildAgentDockerService, "imageArchitecture", originalImageArchitecture);
            originalImageArchitecture = null;
        }
    }

    // The canonical Java scaffold ships a topic-specific Ares structural oracle (test.json: SortStrategy / BubbleSort / MergeSort / Context / Policy) in the TESTS repo, which —
    // by production design — is kept verbatim even under emptyRepositories=true (ProgrammingExerciseRepositoryService: "the test repository is kept intact"). Real grading runs
    // that
    // structural oracle; the sandbox verify.sh oracle does NOT. So the only way a generated Java exercise grades 100% on the REAL pipeline is to honour that canonical structure.
    // This adapt prompt keeps the canonical sorting-strategy structure intact and makes a coherent additive change, exactly as the production "adapt the canonical exercise" flow.
    private static final String JAVA_CANONICAL_ADAPT_PROMPT = """
            Adapt this existing Java sorting exercise based on instructor feedback. Keep the EXACT existing public structure that the structural tests require — the SortStrategy \
            interface, the BubbleSort and MergeSort classes implementing it, the Context class (getDates/setDates/getSortAlgorithm/setSortAlgorithm/sort and its private fields), \
            and the Policy class (its Context constructor, configure(), and private context field) — do not rename, remove, or change the signature of any of them, and keep the \
            reference solution passing every test and the template failing them. As the additive change, add ONE behaviour test that sorts an already-sorted list and asserts it \
            stays sorted, and mention in the problem statement that the input may already be sorted. Keep every existing test. Run `sh verify.sh solution` and `sh verify.sh \
            template` to confirm the solution passes and the template fails, then submit.""";

    // ---- The Java proof leg ----------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * The definitive single-config proof: Java PLAIN_MAVEN, the full authentic generate → persist (+version) → grade chain, asserting the persisted SOLUTION grades 100% and the
     * persisted TEMPLATE grades 0% on the real LocalCI/LocalVC pipeline.
     * <p>
     * Scaffolds the full canonical Java exercise ({@code emptyRepositories=false}) and runs a real generation that makes a coherent additive change while keeping the canonical
     * structure (which the scaffold's verbatim-kept Ares structural oracle grades) intact — the production "adapt the canonical exercise" flow. This is what lets the persisted
     * exercise grade 100% / 0% on the real pipeline, where the sandbox {@code verify.sh} oracle alone cannot see the structural test cases.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void javaPlainMaven_generatePersistGrade_authenticEndToEnd() throws Exception {
        runAuthenticChain(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "HYPAJ", JAVA_CANONICAL_ADAPT_PROMPT, false);
    }

    /**
     * Dedicated single-config proof for the Java PLAIN_GRADLE canonical-ADAPT scenario, the complement to {@link #javaPlainMaven_generatePersistGrade_authenticEndToEnd}. Exercises
     * the Gradle structural-oracle / persist / real-{@code ./gradlew clean test} grading path: the persisted tests repo must carry no orphaned structural case and the persisted
     * solution must grade 100% / template 0% on the real LocalCI pipeline.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void javaPlainGradle_adapt_generatePersistGrade_authenticEndToEnd() throws Exception {
        runAuthenticChain(ProgrammingLanguage.JAVA, ProjectType.PLAIN_GRADLE, "HYPAJPG", JAVA_CANONICAL_ADAPT_PROMPT, false);
    }

    /**
     * Dedicated single-config proof for the Java MAVEN_MAVEN canonical-ADAPT scenario (a Maven harness that builds the assignment as a Maven project). Exercises the path whose
     * build-phase template was previously missing (so the persist-triggered tests build ran an empty script and synchronised zero test cases): with the {@code plain_maven} phases
     * family now mapped for MAVEN_MAVEN, the tests build runs {@code mvn clean test}, synchronises the test cases, and the persisted solution grades 100% / template 0% on the real
     * LocalCI pipeline.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void javaMavenMaven_adapt_generatePersistGrade_authenticEndToEnd() throws Exception {
        runAuthenticChain(ProgrammingLanguage.JAVA, ProjectType.MAVEN_MAVEN, "HYPAJMM", JAVA_CANONICAL_ADAPT_PROMPT, false);
    }

    // A FROM-SCRATCH (emptyRepositories=true) Java prompt for a DIFFERENT topic than the canonical sorting sample. Before the persist-mirror fix this was the failing case: the
    // scaffolded TESTS repo keeps the canonical sample's behaviour test (SortingExampleBehaviorTest) and structure oracle (test.json for SortStrategy/Context/Policy) verbatim; the
    // agent removes them inside the sandbox and writes its own tests, the sandbox verify.sh oracle accepts — but persist used to OVERLAY the produced files onto the scaffolded git
    // tree, re-orphaning the canonical sources into the persisted tests repo, where real Artemis grading runs them and the different-topic solution fails them (solution << 100%).
    // With the fix the persisted tree mirrors the sandbox-final tree, so the orphans are gone and a from-scratch Java exercise grades solution 100% / template 0% on the REAL
    // pipeline.
    private static final String JAVA_FROM_SCRATCH_PROMPT = """
            Create a NEW Java exercise (different topic from any sample): a BankAccount class in package de.test with a private balance, a deposit(double amount) method, a \
            withdraw(double amount) method that throws IllegalArgumentException when the amount exceeds the balance, and a getBalance() method. Write the reference solution, a \
            template whose method bodies are unimplemented placeholders that fail every test (prefer throwing "not implemented"), and JUnit tests covering deposit, withdraw, the \
            overdraft exception, and getBalance. Replace the sample test sources in tests/ with your own — the tests repo must contain ONLY your BankAccount tests. Bind every test \
            with a [task] in the problem statement. Run `sh verify.sh solution` and `sh verify.sh template` to confirm the solution passes and the template fails, then submit.""";

    /**
     * The fix proof: a FROM-SCRATCH (emptyRepositories=true) Java PLAIN_MAVEN generation of a DIFFERENT topic than the canonical sorting sample, asserting the persisted SOLUTION
     * grades 100% and the persisted TEMPLATE grades 0% on the real LocalCI/LocalVC pipeline WITH NO orphaned canonical structural/behaviour cases. This is the configuration the
     * persist-mirror fix repairs: the persisted tests repo now contains only the generated exercise's tests (plus the regenerated structure oracle), so the reference solution is
     * no
     * longer failed by a leftover sample test the sandbox oracle never ran.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void javaPlainMaven_fromScratch_generatePersistGrade_authenticEndToEnd() throws Exception {
        runAuthenticChain(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "HYPFSJ", JAVA_FROM_SCRATCH_PROMPT, true);
    }

    private static final String JAVA_SCA_FROM_SCRATCH_PROMPT = """
            Create a NEW Java exercise (different topic from any sample): a BankAccount class in package de.test with a private balance, a deposit(double amount) method, a \
            withdraw(double amount) method that throws IllegalArgumentException when the amount exceeds the balance, and a getBalance() method. Static code analysis is ENABLED and \
            GRADED for this exercise, so the REFERENCE SOLUTION must be clean of static-analysis findings in the graded categories (no SpotBugs/Checkstyle warnings such as missing \
            braces, bad practice, or style issues) — otherwise it would not grade 100%. Write a clean reference solution, a template whose method bodies are unimplemented \
            placeholders that fail every test, and JUnit tests covering deposit, withdraw, the overdraft exception, and getBalance. Replace the sample test sources in tests/ with \
            your own. Bind every test with a [task] in the problem statement. Run `sh verify.sh solution` and `sh verify.sh template` to confirm, then submit.""";

    /**
     * D2 SCA-parity proof (GPU-gated like the rest). Scaffolds a Java PLAIN_MAVEN exercise with static code analysis ENABLED and one category PROMOTED to GRADED with a positive
     * penalty, so the {@code *_static.yaml} phases run the SCA tools in BOTH the sandbox and production, and production's {@code calculateTotalPenalty} would dock a solution with
     * a
     * graded violation. The new oracle gate REJECTS a reference solution that trips a graded SCA category, so for the run to be ACCEPTED the agent must produce an SCA-clean
     * solution; the persisted solution then grades 100% / template 0% on the real LocalCI pipeline (a graded-but-clean solution loses no SCA points). This is the end-to-end
     * closure
     * of the divergence the deterministic {@link SandboxProductionParityDivergenceTest} and the oracle/script unit tests pin down without the GPU.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void javaPlainMaven_staticCodeAnalysisGraded_generatePersistGrade_authenticEndToEnd() throws Exception {
        runAuthenticChain(ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, "HYPSCA", JAVA_SCA_FROM_SCRATCH_PROMPT, true, true);
    }

    /**
     * Gap 2 proof: a FROM-SCRATCH (emptyRepositories=true) Java PLAIN_GRADLE generation. PLAIN_GRADLE ships a BINARY {@code gradle/wrapper/gradle-wrapper.jar} in the template and
     * solution repositories. The bug this closes: the extract/persist round-trip read every produced file as a UTF-8 String and re-wrote it as UTF-8, mangling that binary so the
     * persisted wrapper was corrupt and the real Gradle build could not bootstrap. With the binary-safe fix the wrapper is excluded from the String pipeline on read-back and
     * preserved byte-exact from the scaffold on persist. This leg asserts BOTH: (1) the persisted solution and template wrapper JAR is byte-identical to the canonical scaffold's,
     * and (2) the real LocalCI Gradle build grades solution 100% / template 0%.
     * <p>
     * The wrapper bootstraps Gradle from {@code services.gradle.org} on first build, so the real grading needs network egress from the build container (this test's
     * {@code DockerRunConfig} uses the regular build network). If that egress is unavailable the Gradle build cannot bootstrap; the leg then fails as a real build failure with the
     * container log, which is reported honestly rather than masked — see the explicit wrapper-intact assertion, which still holds independently of whether the build could
     * bootstrap.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void javaPlainGradle_fromScratch_generatePersistGrade_authenticEndToEnd_wrapperIntact() throws Exception {
        // Capture the canonical scaffold's wrapper bytes BEFORE generation (the agent never edits this binary), then run the full chain and assert the persisted repos still carry
        // those exact bytes. The wrapper-intact assertion runs first inside runAuthenticChain's persist step output; we re-read the persisted repos here.
        ProgrammingExercise persisted = runAuthenticChain(ProgrammingLanguage.JAVA, ProjectType.PLAIN_GRADLE, "HYPFSG", JAVA_FROM_SCRATCH_PROMPT, true);

        byte[] referenceWrapper = readCanonicalGradleWrapperBytes();
        assertGradleWrapperByteIdentical(new LocalVCRepositoryUri(persisted.getSolutionRepositoryUri()), referenceWrapper, "solution");
        assertGradleWrapperByteIdentical(new LocalVCRepositoryUri(persisted.getTemplateRepositoryUri()), referenceWrapper, "template");
    }

    private static final String GRADLE_WRAPPER_PATH = "gradle/wrapper/gradle-wrapper.jar";

    /** Reads the canonical PLAIN_GRADLE scaffold's wrapper JAR bytes from the classpath templates — the byte-exact reference the persisted repos must still match. */
    private byte[] readCanonicalGradleWrapperBytes() throws Exception {
        Path resource = Path.of("src/main/resources/templates/java/plain_gradle/solution/" + GRADLE_WRAPPER_PATH);
        return java.nio.file.Files.readAllBytes(resource);
    }

    /**
     * Clones the persisted bare repository and asserts its {@code gradle/wrapper/gradle-wrapper.jar} is byte-identical to the canonical scaffold's — proving the binary survived
     * the
     * extract/persist round-trip uncorrupted (the binary-safe fix preserves it from the scaffold rather than re-writing a mangled UTF-8 re-encode).
     */
    private void assertGradleWrapperByteIdentical(LocalVCRepositoryUri uri, byte[] reference, String repoLabel) throws Exception {
        Path bare = uri.getLocalRepositoryPath(localVCBasePath);
        File checkout = tempFileUtilService.createTempDirectory("hyp-gradle-read").toFile();
        try (var git = org.eclipse.jgit.api.Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(checkout).call()) {
            Path wrapper = checkout.toPath().resolve(GRADLE_WRAPPER_PATH);
            assertThat(java.nio.file.Files.exists(wrapper)).as("[%s] persisted gradle-wrapper.jar exists", repoLabel).isTrue();
            byte[] persistedBytes = java.nio.file.Files.readAllBytes(wrapper);
            assertThat(persistedBytes).as("[%s] persisted gradle-wrapper.jar is byte-identical to the canonical scaffold (binary survived persist uncorrupted)", repoLabel)
                    .containsExactly(reference);
        }
        finally {
            FileUtils.deleteQuietly(checkout);
        }
    }

    // ---- The full language matrix ----------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Every generation-capable (language × projectType) the generator supports, each running the full authentic generate → persist → grade chain with its real execution image.
     * <p>
     * Excluded by design (the differential oracle is structurally impossible — these are compile-only / no test-report toolchains, as the per-language profiles admit): Assembler,
     * VHDL, OCaml, Bash, MATLAB. Java's MAVEN_MAVEN / GRADLE_GRADLE / PLAIN_GRADLE variants and C (GCC / FACT) are exercised here in addition to PLAIN_MAVEN; Kotlin is included
     * but
     * is known to be infra-gated on the canonical Kotlin Maven template toolchain (see the note in {@code HyperionExerciseGenerationEndToEndTest}). A config whose image is not
     * present locally and cannot be pulled fails loudly as "infra-gated: image X unavailable" rather than being silently skipped.
     */
    static Stream<Arguments> authenticMatrix() {
        return applyFilter(Stream.of(
                // Java variants: keep the canonical-ADAPT scenario (emptyRepositories=false) here, which keeps the verbatim Ares structural oracle satisfied — the complementary
                // coverage to the dedicated FROM-SCRATCH Java leg (javaPlainMaven_fromScratch_...). Since the persist-mirror fix, a from-scratch different-topic Java prompt no
                // longer orphans the canonical structure oracle (it is removed/regenerated and the persisted tree mirrors the sandbox); the adapt cases stay to exercise the
                // work-WITH-the-canonical-harness path across project types.
                javaAdaptCase(ProjectType.PLAIN_MAVEN, "HYPMJPM"), javaAdaptCase(ProjectType.PLAIN_GRADLE, "HYPMJPG"), javaAdaptCase(ProjectType.MAVEN_MAVEN, "HYPMJMM"),
                javaAdaptCase(ProjectType.GRADLE_GRADLE, "HYPMJGG"),
                // Non-Java from-scratch (emptyRepositories=true): these languages ship no structural oracle in the tests repo, so the agent's own behavioural tests are the entire
                // graded set and the sandbox oracle and the real pipeline should agree (solution 100%, template 0%).
                fromScratchCase(ProgrammingLanguage.JAVASCRIPT, null, "HYPMJS", "A JavaScript exercise: a Stack class over numbers with push, pop, peek and isEmpty."),
                fromScratchCase(ProgrammingLanguage.TYPESCRIPT, null, "HYPMTS", "A TypeScript exercise: a Stack class over numbers with push, pop, peek and isEmpty."),
                fromScratchCase(ProgrammingLanguage.PYTHON, null, "HYPMPY",
                        "A Python exercise: a function reverse_string(s) and a function is_palindrome(s) that ignores case and spaces."),
                fromScratchCase(ProgrammingLanguage.GO, null, "HYPMGO",
                        "A Go exercise: a function ReverseString(s string) string and a function IsPalindrome(s string) bool that ignores case and spaces."),
                fromScratchCase(ProgrammingLanguage.RUST, null, "HYPMRS", "A Rust exercise: implement factorial(n: u64) -> u64 and fibonacci(n: u64) -> u64."),
                fromScratchCase(ProgrammingLanguage.C_PLUS_PLUS, null, "HYPMCPP",
                        "A C++ exercise: a Stack of ints (push, pop, top, empty) declared in a header and implemented in a source file."),
                fromScratchCase(ProgrammingLanguage.C_SHARP, null, "HYPMCS",
                        "A C# exercise: a Calculator class with Add, Subtract, Multiply and Divide (Divide throws on division by zero)."),
                fromScratchCase(ProgrammingLanguage.DART, null, "HYPMDART", "A Dart exercise: functions reverseString and isPalindrome (ignoring case and spaces)."),
                fromScratchCase(ProgrammingLanguage.RUBY, null, "HYPMRB", "A Ruby exercise: a Stack class with push, pop, peek and empty?."),
                fromScratchCase(ProgrammingLanguage.R, null, "HYPMR", "An R exercise: a function column_sums(mat) returning the sum of each column of a numeric matrix."),
                fromScratchCase(ProgrammingLanguage.HASKELL, null, "HYPMHS", "A Haskell exercise: implement factorial :: Integer -> Integer and fibonacci :: Int -> Integer."),
                fromScratchCase(ProgrammingLanguage.SWIFT, ProjectType.PLAIN, "HYPMSW", "A Swift exercise: a Stack struct over Int with push, pop, peek and isEmpty."),
                fromScratchCase(ProgrammingLanguage.KOTLIN, null, "HYPMKT",
                        "A Kotlin exercise on a bounded integer stack: push, pop, peek, isEmpty, and a fixed capacity that rejects a push when full.")));
    }

    private static Arguments javaAdaptCase(ProjectType projectType, String shortName) {
        return Arguments.of(ProgrammingLanguage.JAVA, projectType, shortName, JAVA_CANONICAL_ADAPT_PROMPT, false);
    }

    private static Arguments fromScratchCase(ProgrammingLanguage language, ProjectType projectType, String shortName, String prompt) {
        return Arguments.of(language, projectType, shortName, prompt, true);
    }

    /**
     * Optional comma-separated allowlist of languages (enum names) to run, via {@code HYPERION_MATRIX_LANGS}; empty/unset runs the full matrix. Lets several configs be validated
     * in
     * parallel by launching one isolated JVM per subset (each run is GPU + Docker heavy), without an in-process concurrency refactor of the shared per-test Docker switch.
     */
    private static Stream<Arguments> applyFilter(Stream<Arguments> all) {
        String filter = System.getenv("HYPERION_MATRIX_LANGS");
        if (filter == null || filter.isBlank()) {
            return all;
        }
        Set<String> allowed = java.util.Arrays.stream(filter.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.toUpperCase(java.util.Locale.ROOT))
                .collect(Collectors.toSet());
        return all.filter(arguments -> allowed.contains(((ProgrammingLanguage) arguments.get()[0]).name()));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("authenticMatrix")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void authenticEndToEndForConfig(ProgrammingLanguage language, ProjectType projectType, String shortName, String prompt, boolean emptyRepositories) throws Exception {
        runAuthenticChain(language, projectType, shortName, prompt, emptyRepositories);
    }

    // ---- The authentic chain ---------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Runs the full authentic chain for one config: real generation (GPU + sandbox + oracle) → real persistence (+version) → real LocalCI grading (solution 100%, template 0%).
     *
     * @param emptyRepositories {@code false} for the Java canonical-adapt scenario (the canonical structural oracle is kept and must be honoured); {@code true} for from-scratch
     *                              languages with no structural oracle.
     */
    private ProgrammingExercise runAuthenticChain(ProgrammingLanguage language, ProjectType projectType, String shortName, String prompt, boolean emptyRepositories)
            throws Exception {
        return runAuthenticChain(language, projectType, shortName, prompt, emptyRepositories, false);
    }

    private ProgrammingExercise runAuthenticChain(ProgrammingLanguage language, ProjectType projectType, String shortName, String prompt, boolean emptyRepositories,
            boolean staticCodeAnalysisEnabled) throws Exception {
        String label = language.name().toLowerCase() + (projectType != null ? "-" + projectType.name().toLowerCase() : "");

        // 0. Ensure the real execution image is present (pull if needed). A genuinely unavailable image is reported as infra-gated rather than masquerading as a generation
        // failure.
        ProgrammingExercise scaffolded = scaffoldExercise(shortName, language, projectType, emptyRepositories, staticCodeAnalysisEnabled);
        String executionImage = buildPhasesTemplateService.getDefaultDockerImageFor(scaffolded);
        try {
            ensureDockerImageAvailable(executionImage);
        }
        catch (RuntimeException e) {
            throw new AssertionError("infra-gated: image " + executionImage + " unavailable for " + label + " (" + e.getMessage() + ")", e);
        }

        // 1. GENERATE — real GPU + real sandbox container + real differential oracle. Persist while the outcome (sandbox session) is still open.
        StringBuilder transcript = new StringBuilder();
        ProgrammingExercise persisted;
        try (GenerationOutcome outcome = orchestrator.generate(scaffolded, instructor(), prompt, "authentic-" + label, () -> false, line -> {
            transcript.append(line).append('\n');
            log.info("[agent:{}] {}", label, line);
        })) {
            Path exportDirectory = GeneratedExerciseExporter.export("authentic-" + label, outcome, transcript.toString());
            log.info("=== EXPORTED ({}) to {} ===", label, exportDirectory.toAbsolutePath());
            assertThat(outcome.verification()).as("[%s] verification ran", label).isNotNull();
            log.info("=== SANDBOX ORACLE ({}) ===%n{}", label, outcome.verification().report());
            assertThat(outcome.verification().solutionPassed()).as("[%s] sandbox oracle: solution passes", label).isTrue();
            assertThat(outcome.verification().templateFailed()).as("[%s] sandbox oracle: template compiles but fails", label).isTrue();
            assertThat(outcome.isAccepted()).as("[%s] sandbox oracle accepted the exercise", label).isTrue();

            // 2. PERSIST — production GenerationPersistenceService: commit template → solution → tests, update problem statement, trigger the tests-build for test-case sync, and
            // record an ExerciseVersion. This is the same path the production task service uses after acceptance.
            persistenceService.persist(scaffolded, instructor(), outcome);
        }
        // Re-load the exercise so the post-persist state (problem statement, repositories) is fresh.
        persisted = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(scaffolded.getId()).orElseThrow();

        // 2a. A version was recorded by the persist step (snapshotting the committed repository state).
        assertThat(exerciseVersionRepository.findAllByExerciseId(persisted.getId())).as("[%s] persist recorded an exercise version", label).isNotEmpty();

        // 2b. The persisted SOLUTION repository is non-empty (the generated reference solution was committed into LocalVC).
        assertThat(repositoryContainsSourceFile(new LocalVCRepositoryUri(persisted.getSolutionRepositoryUri()))).as("[%s] persisted solution repo contains source", label).isTrue();

        // 2c. The real tests-build (triggered by persist) synchronises the canonical test-case set through the normal result-processing pipeline. Grading needs this set populated.
        List<String> syncedTestCaseNames = awaitSynchronisedTestCases(persisted);
        int totalTestCaseCount = syncedTestCaseNames.size();
        log.info("[{}] synchronised {} test case(s): {}", label, totalTestCaseCount, syncedTestCaseNames);
        assertThat(totalTestCaseCount).as("[%s] the tests-build synchronised at least one test case", label).isGreaterThan(0);

        // 2d. Divergence detector: name any synchronised structural test case (testClass[X] / testMethods[X] / …) whose target class is absent from the persisted solution. Such a
        // case is an ORPHANED canonical structural oracle the sandbox verify.sh oracle never runs but the real pipeline does — the precise, high-value sandbox-vs-real divergence.
        // Surfaced as a named diagnostic so a sub-100% solution leg reads as "orphaned structural oracle" rather than an inscrutable score.
        List<String> orphanedStructuralCases = detectOrphanedStructuralCases(persisted, syncedTestCaseNames);
        if (!orphanedStructuralCases.isEmpty()) {
            log.warn("[{}] DIVERGENCE: synchronised structural test cases target classes absent from the generated solution (sandbox oracle cannot see these, real grading runs "
                    + "them): {}", label, orphanedStructuralCases);
        }
        // The persist-mirror invariant: the persisted tests repo contains ONLY the generated exercise's tests (and a structure oracle regenerated for the generated classes), so no
        // synchronised structural case can target a class the generated solution lacks. A from-scratch run that leaves such an orphan would be the exact verify.sh-vs-production
        // divergence this fix closes, so assert it is empty rather than only logging it.
        assertThat(orphanedStructuralCases).as("[%s] no orphaned canonical structural cases survived into the persisted tests repo", label).isEmpty();

        // 3. GRADE — real LocalCI/LocalVC. The persisted SOLUTION must score 100% (every active test case passes); the persisted TEMPLATE must score 0% (compiles, fails all, no
        // build failure). Scores come from the production Result#getScore(). The passed-test-case count is asserted against the grading result's OWN active test-case count (all
        // pass
        // for the solution, none for the template) rather than the pre-grade synced count — pytest/JUnit report method-level cases that the coarse pre-grade sync may not yet have
        // expanded (a behaviour file vs its N methods), so pinning to the early count would be racy; "solution passes ALL active cases, template passes NONE" is the real
        // invariant.
        gradeLegAndAssert(persisted, label, "sol", new LocalVCRepositoryUri(persisted.getSolutionRepositoryUri()), true, 100.0, orphanedStructuralCases);
        gradeLegAndAssert(persisted, label, "tmpl", new LocalVCRepositoryUri(persisted.getTemplateRepositoryUri()), false, 0.0, orphanedStructuralCases);
        return persisted;
    }

    /**
     * Names synchronised structural test cases ({@code testClass[X]}, {@code testMethods[X]}, {@code testAttributes[X]}, {@code testConstructors[X]}) whose target class {@code X}
     * does not appear in the persisted solution sources — i.e. an orphaned canonical structural oracle the sandbox {@code verify.sh} oracle never runs but the real pipeline does.
     */
    private List<String> detectOrphanedStructuralCases(ProgrammingExercise exercise, List<String> testCaseNames) {
        String solutionSources = readSolutionSources(exercise);
        java.util.regex.Pattern structural = java.util.regex.Pattern.compile("^test(?:Class|Methods|Attributes|Constructors)\\[(\\w+)]$");
        return testCaseNames.stream().filter(name -> {
            var matcher = structural.matcher(name);
            return matcher.matches() && !solutionSources.contains(matcher.group(1));
        }).sorted().toList();
    }

    private String readSolutionSources(ProgrammingExercise exercise) {
        try {
            java.nio.file.Path solutionBare = new LocalVCRepositoryUri(exercise.getSolutionRepositoryUri()).getLocalRepositoryPath(localVCBasePath);
            java.io.File checkout = tempFileUtilService.createTempDirectory("hyp-sol-read").toFile();
            try (var git = org.eclipse.jgit.api.Git.cloneRepository().setURI(solutionBare.toUri().toString()).setDirectory(checkout).call()) {
                StringBuilder sources = new StringBuilder();
                try (var paths = java.nio.file.Files.walk(checkout.toPath())) {
                    for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.filter(java.nio.file.Files::isRegularFile)::iterator) {
                        String relative = path.toString();
                        if (relative.contains(java.io.File.separator + ".git" + java.io.File.separator)) {
                            continue;
                        }
                        // Only Java sources carry the class names the structural-case classifier compares against. A PLAIN_GRADLE / GRADLE_GRADLE solution also ships a BINARY
                        // gradle/wrapper/gradle-wrapper.jar; reading it as UTF-8 throws MalformedInputException, which — if it aborted the whole walk — would empty the source scan
                        // and make EVERY structural case look orphaned (a false positive: the structural oracle is present and the solution actually grades 100%). Restrict the
                        // scan
                        // to .java files and skip any single file that is not decodable, so one binary or stray non-UTF-8 file never derails the orphan classification.
                        if (!relative.endsWith(".java")) {
                            continue;
                        }
                        try {
                            sources.append(java.nio.file.Files.readString(path)).append('\n');
                        }
                        catch (java.io.IOException perFile) {
                            log.debug("Skipping non-UTF-8 solution file {} while classifying structural cases: {}", relative, perFile.getMessage());
                        }
                    }
                }
                return sources.toString();
            }
            finally {
                org.apache.commons.io.FileUtils.deleteQuietly(checkout);
            }
        }
        catch (Exception e) {
            log.warn("Could not read persisted solution sources to classify structural cases: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Drives one full real grading leg: seed a fresh student repository from the given persisted source bare repository (solution or template), push a trigger commit, run the real
     * LocalCI Docker build, and assert the resulting {@link Result} through the production scoring path.
     */
    private void gradeLegAndAssert(ProgrammingExercise exercise, String label, String legSuffix, LocalVCRepositoryUri sourceUri, boolean allTestsPass, double expectedScore,
            List<String> orphanedStructuralCases) throws Exception {
        String studentLogin = TEST_PREFIX + "student1";
        String projectKey = exercise.getProjectKey();
        String studentSlug = localVCLocalCITestService.getRepositorySlug(projectKey, studentLogin) + "-" + legSuffix;

        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, studentLogin);
        participation.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(studentLogin, projectKey, studentSlug));
        participation.setBranch(localVCLocalCITestService.getDefaultBranch());
        participation = programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        LocalRepository studentRepository = seedStudentRepositoryFromBare(projectKey, studentSlug, sourceUri);

        String commitHash = localVCLocalCITestService.commitFile(studentRepository.workingCopyGitRepoFile.toPath(), studentRepository.workingCopyGitRepo,
                "trigger-" + legSuffix + ".txt");
        studentRepository.workingCopyGitRepo.push().call();
        RepositoryExportTestUtil.waitForBareRepositoryReady(studentRepository);

        ProgrammingSubmission submission = createManualSubmission(participation, commitHash);
        localCITriggerService.triggerBuild(participation, commitHash, RepositoryType.USER);

        awaitCreatedBuildJob(participation.getId());
        BuildJob buildJob = awaitCompletedBuildJob(participation.getId());
        ProgrammingSubmission persistedSubmission = awaitLatestSubmissionWithResult(participation.getId());

        if (persistedSubmission.isBuildFailed() || persistedSubmission.getLatestResult() == null) {
            var logs = buildLogEntryService.getLatestBuildLogs(persistedSubmission);
            log.error("[{}/{}] unexpected build failure — buildStatus={}, buildFailed={}, container log:%n{}", label, legSuffix, buildJob.getBuildStatus(),
                    persistedSubmission.isBuildFailed(), logs.stream().map(de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry::getLog).collect(Collectors.joining()));
        }

        assertThat(buildJob.getBuildStatus()).as("[%s/%s] real LocalCI build completed", label, legSuffix).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(persistedSubmission.getCommitHash()).as("[%s/%s] result is for the pushed commit", label, legSuffix).isEqualTo(commitHash);
        assertThat(persistedSubmission.getId()).as("[%s/%s] result is for the pushed submission", label, legSuffix).isEqualTo(submission.getId());
        assertThat(persistedSubmission.isBuildFailed()).as("[%s/%s] the exercise compiles (a behavioural failure is not a build failure)", label, legSuffix).isFalse();

        Result result = persistedSubmission.getLatestResult();
        assertThat(result).as("[%s/%s] a result was produced by the real grading path", label, legSuffix).isNotNull();
        String feedbackDiagnostics = loadAndFormatTestCaseFeedback(result.getId());
        String divergenceNote = orphanedStructuralCases.isEmpty() ? ""
                : String.format("%nORPHANED canonical structural oracle (sandbox oracle never ran these; real grading did): %s", orphanedStructuralCases);
        // The divergence signal we most want: if the REAL grading disagrees with the sandbox oracle (solution != 100% or template != 0%), surface it loudly with full feedback and
        // the orphaned-structural-oracle classification so the cause is named, not inferred.
        assertThat(result.getScore()).as("[%s/%s] REAL grading score (sandbox oracle accepted); divergence here is the high-value signal.%s feedback:%n%s", label, legSuffix,
                divergenceNote, feedbackDiagnostics).isEqualTo(expectedScore);
        // The passed-test-case count is checked against the grading result's OWN active test-case count: the solution must pass EVERY active case, the template NONE. This is
        // robust
        // to the pre-grade synced count not yet having expanded coarse (file-level) cases into method-level ones, while still proving the full differential on the real pipeline.
        int activeTestCaseCount = result.getTestCaseCount();
        int expectedPassedTestCaseCount = allTestsPass ? activeTestCaseCount : 0;
        assertThat(result.getPassedTestCaseCount())
                .as("[%s/%s] passed-test-case count (of %d active);%s feedback:%n%s", label, legSuffix, activeTestCaseCount, divergenceNote, feedbackDiagnostics)
                .isEqualTo(expectedPassedTestCaseCount);
    }

    // ---- Fixture scaffolding ---------------------------------------------------------------------------------------------------------------------------------------------------

    private ProgrammingExercise scaffoldExercise(String shortName, ProgrammingLanguage language, ProjectType projectType, boolean emptyRepositories) throws Exception {
        return scaffoldExercise(shortName, language, projectType, emptyRepositories, false);
    }

    /**
     * @param staticCodeAnalysisEnabled when {@code true}, enables SCA on the exercise, sets a positive {@code maxStaticCodeAnalysisPenalty}, the {@code *_static.yaml} build phases
     *                                      (so the SCA tools run), creates the production default SCA categories, and PROMOTES one to {@code GRADED} with a positive penalty — so
     *                                      production would dock a solution with that category's violations and the oracle's D2 SCA-parity gate is exercised end-to-end (the agent
     *                                      must produce a lint-clean reference solution for the run to be accepted).
     */
    private ProgrammingExercise scaffoldExercise(String shortName, ProgrammingLanguage language, ProjectType projectType, boolean emptyRepositories,
            boolean staticCodeAnalysisEnabled) throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course, language);
        exercise.setProjectType(projectType);
        exercise.setShortName(shortName);
        exercise.setTitle("Hyperion Authentic " + shortName);
        exercise.setChannelName("hyp-auth-" + shortName.toLowerCase());
        exercise.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        if (staticCodeAnalysisEnabled) {
            exercise.setMaxStaticCodeAnalysisPenalty(50);
        }
        // Wire the default build phases and the production execution image, mirroring how the production setup endpoint and the deterministic grading test configure the build, so
        // the LocalCI grading build runs the real per-language recipe rather than the generic gradle fallback. With SCA enabled the phase resolver selects the *_static.yaml
        // family,
        // so both the sandbox verify.sh and the real grading run the SCA tools.
        exercise.getBuildConfig().setBuildScript(null);
        var phases = buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise);
        var dockerImage = buildPhasesTemplateService.getDefaultDockerImageFor(exercise);
        exercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(phases, dockerImage).toBuildPlanConfiguration());
        // emptyRepositories=true: scaffold the language skeleton, then clear the template/solution sources (exactly as production does before from-scratch AI generation); the
        // tests
        // repo (including any structural oracle) is kept intact by production design. emptyRepositories=false: keep the full canonical exercise for the adapt scenario.
        ProgrammingExercise created = creationService.createProgrammingExercise(exercise, emptyRepositories);
        if (staticCodeAnalysisEnabled) {
            // Create the production default SCA categories (the creation endpoint does this in production), then PROMOTE one mapped category to GRADED with a positive penalty so
            // production's calculateTotalPenalty would deduct for a violation in it — making the oracle's SCA-parity gate live for this run.
            staticCodeAnalysisService.createDefaultCategories(created);
            var categories = staticCodeAnalysisCategoryRepository.findByExerciseId(created.getId());
            categories.stream().filter(c -> "Bad Practice".equals(c.getName()) || "Code Style".equals(c.getName())).findFirst().ifPresent(c -> {
                c.setState(de.tum.cit.aet.artemis.assessment.domain.CategoryState.GRADED);
                if (c.getPenalty() == null || c.getPenalty() <= 0) {
                    c.setPenalty(1.0);
                }
                staticCodeAnalysisService.updateCategories(created.getId(), java.util.Set.of(c));
            });
        }
        return created;
    }

    private LocalRepository seedStudentRepositoryFromBare(String projectKey, String studentSlug, LocalVCRepositoryUri sourceUri) throws Exception {
        LocalRepository target = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, studentSlug));
        File sourceBareDir = sourceUri.getLocalRepositoryPath(localVCBasePath).toFile();
        FileUtils.copyDirectory(sourceBareDir, target.remoteBareGitRepoFile);
        target.workingCopyGitRepo.fetch().setRemote("origin").call();
        target.workingCopyGitRepo.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/" + target.workingCopyGitRepo.getRepository().getBranch()).call();
        return target;
    }

    private ProgrammingSubmission createManualSubmission(ProgrammingExerciseStudentParticipation participation, String commitHash) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(participation);
        return programmingSubmissionRepository.saveAndFlush(submission);
    }

    private boolean repositoryContainsSourceFile(LocalVCRepositoryUri uri) {
        File bareDir = uri.getLocalRepositoryPath(localVCBasePath).toFile();
        return bareDir.exists();
    }

    // ---- Awaits ----------------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Waits for the persist-triggered tests-build to complete and the real result-processing pipeline to synchronise the test-case set, then returns the synchronised test names.
     */
    private List<String> awaitSynchronisedTestCases(ProgrammingExercise exercise) {
        try {
            await().atMost(TEST_CASE_SYNC_TIMEOUT).pollInterval(Duration.ofSeconds(1)).until(() -> {
                localCIResultListenerService.processQueuedResults();
                return !testCaseRepository.findByExerciseId(exercise.getId()).isEmpty();
            });
        }
        catch (ConditionTimeoutException e) {
            throw new AssertionError("The persist-triggered tests-build did not synchronise any test case for exercise " + exercise.getId(), e);
        }
        return testCaseRepository.findByExerciseId(exercise.getId()).stream().map(de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase::getTestName).sorted()
                .toList();
    }

    private BuildJob awaitCreatedBuildJob(Long participationId) {
        try {
            await().atMost(BUILD_JOB_CREATION_TIMEOUT).until(() -> buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(participationId).isPresent());
        }
        catch (ConditionTimeoutException e) {
            throw new AssertionError("Real LocalCI build job was not created for participation " + participationId, e);
        }
        return buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(participationId).orElseThrow();
    }

    private BuildJob awaitCompletedBuildJob(Long participationId) {
        try {
            await().atMost(BUILD_TIMEOUT).until(() -> {
                localCIResultListenerService.processQueuedResults();
                return buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(participationId)
                        .filter(buildJob -> buildJob.getBuildStatus() != BuildStatus.QUEUED && buildJob.getBuildStatus() != BuildStatus.BUILDING).isPresent();
            });
        }
        catch (ConditionTimeoutException e) {
            throw new AssertionError("Real LocalCI build did not complete for participation " + participationId, e);
        }
        return buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(participationId).orElseThrow();
    }

    private ProgrammingSubmission awaitLatestSubmissionWithResult(Long participationId) {
        try {
            await().atMost(BUILD_TIMEOUT).pollInterval(Duration.ofMillis(500)).until(() -> {
                localCIResultListenerService.processQueuedResults();
                participantScoreScheduleService.executeScheduledTasks();
                if (!participantScoreScheduleService.isIdle()) {
                    return false;
                }
                return resultRepository.findFirstWithSubmissionsByParticipationIdOrderByCompletionDateDesc(participationId).isPresent();
            });
            await().atMost(BUILD_TIMEOUT).until(() -> programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId)
                    .map(ProgrammingSubmission::getLatestResult).isPresent());
        }
        catch (ConditionTimeoutException e) {
            throw new AssertionError("Real LocalCI result was not persisted for participation " + participationId, e);
        }
        return programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId).orElseThrow();
    }

    private String loadAndFormatTestCaseFeedback(long resultId) {
        var resultWithFeedbacks = resultRepository.findResultWithFeedbacksAndTestCasesById(resultId);
        if (resultWithFeedbacks.isEmpty() || resultWithFeedbacks.get().getFeedbacks().isEmpty()) {
            return "(no feedback recorded)";
        }
        return resultWithFeedbacks.get().getFeedbacks().stream().map(feedback -> {
            String name = feedback.getTestCase() != null ? feedback.getTestCase().getTestName() : feedback.getText();
            String status = Boolean.TRUE.equals(feedback.isPositive()) ? "PASS" : "FAIL";
            String detail = feedback.getDetailText();
            return "  - " + name + " [" + status + "]" + (detail != null && !detail.isBlank() ? ": " + detail : "");
        }).sorted().collect(Collectors.joining(System.lineSeparator()));
    }

    private void ensureDockerImageAvailable(String dockerImage) {
        new RemoteDockerImage(DockerImageName.parse(dockerImage)).get();
    }

    private void initializeLazyLocalCIServices() {
        applicationContext.getBean(LocalCIEventListenerService.class);
        applicationContext.getBean(LocalCIResultProcessingService.class);
        localCIResultListenerService = applicationContext.getBean(LocalCIResultListenerService.class);
    }

    private String normalizeDockerArchitecture(String dockerArchitecture) {
        return switch (dockerArchitecture) {
            case "aarch64" -> "arm64";
            case "x86_64" -> "amd64";
            default -> dockerArchitecture;
        };
    }

    private User instructor() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }
}

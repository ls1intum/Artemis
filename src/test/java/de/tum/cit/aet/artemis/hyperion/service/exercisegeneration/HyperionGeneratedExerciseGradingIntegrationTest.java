package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.service.LocalCIEventListenerService;
import de.tum.cit.aet.artemis.localci.service.LocalCIResultListenerService;
import de.tum.cit.aet.artemis.localci.service.LocalCIResultProcessingService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * Closed-loop grading test for a Hyperion-scaffolded programming exercise.
 * <p>
 * <b>What gap this closes.</b> Every existing Hyperion acceptance signal stops at the sandbox {@code verify.sh} differential oracle (run inside the {@code InteractiveSandbox}
 * container by {@code AuthoritativeVerificationService}). That oracle is a proxy for production grading: it assembles and runs the build with its own recipe, which provably
 * diverges from how the real Artemis LocalCI/LocalVC pipeline assembles, runs, parses, and scores a submission. Nothing in the suite proved that an exercise persisted into
 * Artemis actually <i>grades</i> correctly on the real path. This test proves exactly that: a canonical (deterministic, model-free) Java exercise, scaffolded through the same
 * production {@link de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService#createProgrammingExercise(ProgrammingExercise, boolean)} entry point the
 * Hyperion generation flow uses, is graded through the <i>real</i> LocalCI Docker build + LocalVC checkout + Ares/JUnit report parsing + {@code ProgrammingExerciseGradingService}
 * scoring. The reference solution scores 100% (all tests pass); the student template scores 0% (it compiles but behaviourally fails).
 * <p>
 * <b>Determinism / no model.</b> There is no GPU, no LLM, no nondeterministic generation. The fixture is the canonical Artemis Java sorting exercise shipped under
 * {@code src/main/resources/templates/java/} — the production gold standard that is known to build and grade. {@code createProgrammingExercise(exercise, false)} scaffolds the
 * real,
 * buildable solution / template / tests repositories (with placeholder substitution applied) into LocalVC, exactly as the Hyperion E2E scaffold step does.
 * <p>
 * <b>Why this is a real build, and why it is gated.</b> The default LocalCI/LocalVC test bucket does <i>not</i> run a real build: {@code TestBuildAgentConfiguration} installs a
 * mocked {@code DockerClient} and the "result" is injected from pre-baked JUnit XML fixtures (see {@code DockerClientTestService.mockTestResults} and the
 * {@code java-gradle/all-succeed | all-fail} folders). In that mode the repository contents are irrelevant to the score — so it cannot prove that the <i>real</i> build grades the
 * <i>real</i> sources. The only established pattern in the codebase that runs a genuine container build and asserts {@code result.getScore()} is
 * {@code LocalCIDockerImageIntegrationTest}, which is gated behind {@code @EnabledIf("isDockerAvailable")} and switches the build agent to a real {@code DockerClient}. This test
 * follows that exact pattern so it runs in the same conditions the suite already supports: it is skipped when Docker is unavailable, and when Docker is present it pulls/runs the
 * real Java Maven build image and grades for real. {@code LocalCIDockerImageIntegrationTest} only ever proves the solution→100% leg (for C); this test adds the missing
 * template→0% leg and does both for Java through the Hyperion scaffold path.
 */
@EnabledIf("isDockerAvailable")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class HyperionGeneratedExerciseGradingIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final Logger log = LoggerFactory.getLogger(HyperionGeneratedExerciseGradingIntegrationTest.class);

    private static final String TEST_PREFIX = "hypgrade";

    private static final String JAVA_MAVEN_DOCKER_IMAGE = "ls1tum/artemis-maven-template:java17-25";

    // The canonical Artemis Java sorting exercise produces exactly these 13 Ares test cases (4 behaviour + structural Class/Method/Attribute/Constructor tests). These names match
    // the JUnit reports the real build emits, so the real result-processing path binds and scores them.
    private static final List<String> CANONICAL_TEST_CASE_NAMES = List.of("testClass[SortStrategy]", "testAttributes[Context]", "testAttributes[Policy]", "testClass[MergeSort]",
            "testClass[BubbleSort]", "testConstructors[Policy]", "testMethods[Context]", "testMethods[Policy]", "testMethods[SortStrategy]", "testMergeSort()",
            "testUseBubbleSortForSmallList()", "testUseMergeSortForBigList()", "testBubbleSort()");

    private static final int TOTAL_TEST_CASE_COUNT = CANONICAL_TEST_CASE_NAMES.size();

    private static final Duration BUILD_JOB_CREATION_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(8);

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService creationService;

    @Autowired
    private de.tum.cit.aet.artemis.buildagent.service.BuildAgentDockerService buildAgentDockerService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private LocalCIResultListenerService localCIResultListenerService;

    private DockerClient realDockerClient;

    private String originalDockerConnectionUri;

    private String originalImageArchitecture;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    // ---- Docker gating (identical to LocalCIDockerImageIntegrationTest) -------------------------------------------------------------------------------------------------------

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

    // ---- Real Docker wiring (identical to LocalCIDockerImageIntegrationTest) --------------------------------------------------------------------------------------------------

    @BeforeEach
    void switchToRealDockerClient() {
        // The base class points the Java/Kotlin build image at a placeholder so the normal (mocked-build) buckets never pull one. Override it back to the real production
        // Java/Maven
        // execution image for this gated real-build test (same image the Hyperion GPU E2E uses), so the canonical Maven exercise actually compiles and runs in the container. Done
        // in-place on the shared bean rather than via @TestPropertySource so no separate Spring context is forked.
        HyperionGpuTestEnvironment.useProductionBuildImages(programmingLanguageConfiguration, ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN);
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
        log.info("Running Hyperion grading test with Docker architecture: {}", architecture);
        ReflectionTestUtils.setField(buildAgentDockerService, "imageArchitecture", architecture);
        distributedDataAccessService.getDistributedBuildJobQueue().clear();
        distributedDataAccessService.getDistributedProcessingJobs().clear();
        distributedDataAccessService.getDistributedBuildResultQueue().clear();
        sharedQueueProcessingService.resetInitializedState();
        sharedQueueProcessingService.setPauseState(false);
        sharedQueueProcessingService.init();
        sharedQueueProcessingService.updateBuildAgentInformation();
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

    // ---- The closed-loop test --------------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void scaffoldedExerciseGradesSolutionAt100AndTemplateAt0_onRealLocalCILocalVC() throws Exception {
        ensureDockerImageAvailable(JAVA_MAVEN_DOCKER_IMAGE);
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        // Scaffold the canonical, buildable Java exercise through the same production creation path the Hyperion flow uses. This commits the real reference solution, the real
        // failing template stub, and the real assembled Ares test repository into LocalVC.
        ProgrammingExercise exercise = scaffoldCanonicalJavaExercise("HYPGRD");

        // Register the canonical Ares test cases deterministically (independent of the asynchronous creation-time test-build sync), so the real grading path has the test-case set
        // to
        // bind the parsed JUnit reports against and to score.
        registerCanonicalTestCases(exercise);

        // SOLUTION leg: a student submission whose sources are the reference solution must pass every test -> score 100%.
        gradeLegAndAssert(exercise, "sol", new LocalVCRepositoryUri(exercise.getSolutionRepositoryUri()), TOTAL_TEST_CASE_COUNT, 100.0);

        // TEMPLATE leg: a student submission whose sources are the template stub must compile but fail every behavioural/structural test -> 0 passed, score 0%, build NOT failed.
        gradeLegAndAssert(exercise, "tmpl", new LocalVCRepositoryUri(exercise.getTemplateRepositoryUri()), 0, 0.0);
    }

    /**
     * Drives one full real grading leg: seed a fresh student repository from the given source bare repository (solution or template), push a trigger commit, run the real LocalCI
     * Docker build, and assert the resulting {@link Result} through the production scoring path.
     */
    private void gradeLegAndAssert(ProgrammingExercise exercise, String legSuffix, LocalVCRepositoryUri sourceUri, int expectedPassedTestCaseCount, double expectedScore)
            throws Exception {
        String studentLogin = TEST_PREFIX + "student1";
        String projectKey = exercise.getProjectKey();
        // Use a distinct slug per leg so the two legs do not collide on the same student bare repo.
        String studentSlug = localVCLocalCITestService.getRepositorySlug(projectKey, studentLogin) + "-" + legSuffix;

        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, studentLogin);
        participation.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(studentLogin, projectKey, studentSlug));
        participation.setBranch(localVCLocalCITestService.getDefaultBranch());
        participation = programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        // Seed the student bare repo with the source (solution / template) content by cloning the source bare into a working copy and pushing it into the student bare.
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

        // On an unexpected build failure, dump the real container build log so a CI failure names the cause directly (mirrors the diagnostics in
        // LocalCIDockerImageIntegrationTest).
        if (persistedSubmission.isBuildFailed() || persistedSubmission.getLatestResult() == null) {
            var logs = buildLogEntryService.getLatestBuildLogs(persistedSubmission);
            log.error("[{}] unexpected build failure — buildStatus={}, buildFailed={}, container log:\n{}", legSuffix, buildJob.getBuildStatus(),
                    persistedSubmission.isBuildFailed(), logs.stream().map(de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry::getLog).collect(Collectors.joining()));
        }

        // The build itself must succeed (the container ran and produced reports) for BOTH legs: the solution passes, and the template compiles but fails behaviourally — a failing
        // template is a 0% result, NOT a failed build.
        assertThat(buildJob.getBuildStatus()).as("[%s] real LocalCI build completed", legSuffix).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(persistedSubmission.getId()).as("[%s] result is for the pushed submission", legSuffix).isEqualTo(submission.getId());
        assertThat(persistedSubmission.getCommitHash()).as("[%s] result is for the pushed commit", legSuffix).isEqualTo(commitHash);
        assertThat(persistedSubmission.isBuildFailed()).as("[%s] the exercise compiles (a behavioural failure is not a build failure)", legSuffix).isFalse();

        Result result = persistedSubmission.getLatestResult();
        assertThat(result).as("[%s] a result was produced by the real grading path", legSuffix).isNotNull();
        String feedbackDiagnostics = loadAndFormatTestCaseFeedback(result.getId());
        assertThat(result.getTestCaseCount()).as("[%s] all canonical test cases were graded; feedback:%n%s", legSuffix, feedbackDiagnostics).isEqualTo(TOTAL_TEST_CASE_COUNT);
        assertThat(result.getPassedTestCaseCount()).as("[%s] passed-test-case count; feedback:%n%s", legSuffix, feedbackDiagnostics).isEqualTo(expectedPassedTestCaseCount);
        assertThat(result.getScore()).as("[%s] score from the production grading service; feedback:%n%s", legSuffix, feedbackDiagnostics).isEqualTo(expectedScore);
    }

    // ---- Fixture scaffolding ---------------------------------------------------------------------------------------------------------------------------------------------------

    private ProgrammingExercise scaffoldCanonicalJavaExercise(String shortName) throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course,
                ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_MAVEN);
        exercise.setShortName(shortName);
        exercise.setTitle("Hyperion Grading " + shortName);
        exercise.setChannelName("hyp-grade-" + shortName.toLowerCase());
        exercise.setStaticCodeAnalysisEnabled(false);
        // Wire the default Maven build phases (mvn clean test + surefire result paths) and the production Java/Maven execution image, mirroring how the base LocalCI test and the
        // production setup endpoint configure the build. Without this the build config falls back to the generic gradle script (chmod ./gradlew; ./gradlew clean test), which the
        // Maven-layout repositories do not satisfy.
        exercise.getBuildConfig().setBuildScript(null);
        var phases = buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(exercise);
        var dockerImage = buildPhasesTemplateService.getDefaultDockerImageFor(exercise);
        exercise.getBuildConfig().setBuildPlanConfiguration(new BuildPlanPhasesDTO(phases, dockerImage).toBuildPlanConfiguration());
        // emptyRepositories=false -> scaffold the full buildable canonical exercise (solution + template stub + assembled Ares tests), the same path the Hyperion adapt flow uses.
        return creationService.createProgrammingExercise(exercise, false);
    }

    private void registerCanonicalTestCases(ProgrammingExercise exercise) {
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(exercise.getId()));
        testCaseRepository.flush();
        List<ProgrammingExerciseTestCase> testCases = CANONICAL_TEST_CASE_NAMES.stream().map(name -> new ProgrammingExerciseTestCase().testName(name).weight(1.0).active(true)
                .exercise(exercise).visibility(Visibility.ALWAYS).bonusMultiplier(1D).bonusPoints(0D)).toList();
        testCaseRepository.saveAll(testCases);
        testCaseRepository.flush();
        assertThat(testCaseRepository.findByExerciseId(exercise.getId())).as("canonical test cases registered").hasSize(TOTAL_TEST_CASE_COUNT);
    }

    /**
     * Creates a fresh LocalVC student repository for the given slug and seeds it with the content of an existing source bare repository (the exercise's solution or template repo
     * that {@code createProgrammingExercise} already populated). Mirrors {@code RepositoryExportTestUtil.seedLocalVcBareFrom}, but the source is an on-disk bare path rather than a
     * {@link LocalRepository} handle.
     */
    private LocalRepository seedStudentRepositoryFromBare(String projectKey, String studentSlug, LocalVCRepositoryUri sourceUri) throws Exception {
        LocalRepository target = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, studentSlug));
        File sourceBareDir = sourceUri.getLocalRepositoryPath(localVCBasePath).toFile();
        FileUtils.copyDirectory(sourceBareDir, target.remoteBareGitRepoFile);
        // Re-sync the working copy with the freshly-overwritten bare content.
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
        // Set the owning (FK) side directly rather than participation.addSubmission(...), which would force-initialize the participation's lazy submissions collection on the
        // detached entity returned by addStudentParticipationForProgrammingExercise.
        submission.setParticipation(participation);
        return programmingSubmissionRepository.saveAndFlush(submission);
    }

    // ---- Real-build await helpers (mirrors LocalCIDockerImageIntegrationTest) --------------------------------------------------------------------------------------------------

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
}

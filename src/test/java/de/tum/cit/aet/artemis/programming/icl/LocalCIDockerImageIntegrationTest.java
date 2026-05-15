package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.awaitility.core.ConditionTimeoutException;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
import de.tum.cit.aet.artemis.buildagent.service.BuildAgentDockerService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIEventListenerService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIResultListenerService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIResultProcessingService;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

@EnabledIf("isDockerAvailable")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class LocalCIDockerImageIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final Logger log = LoggerFactory.getLogger(LocalCIDockerImageIntegrationTest.class);

    private static final String TEST_PREFIX = "localcidockerimage";

    private static final String GCC_DOCKER_IMAGE = "ls1tum/artemis-c-minimal-docker:1.0.0";

    private static final String FACT_DOCKER_IMAGE = "ls1tum/artemis-fact-minimal-docker:1.1.0";

    private static final int FACT_SUCCESSFUL_TEST_CASES = 3;

    private static final List<String> FACT_TEST_CASE_NAMES = List.of("Compile", "CodeStructure", "InputOutput");

    private static final Duration BUILD_JOB_CREATION_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);

    private static final int MAX_DIAGNOSTIC_LOG_LENGTH = 8000;

    private static final int MAX_BUILD_ATTEMPTS = 2;

    private DockerClient realDockerClient;

    private String originalDockerConnectionUri;

    private String originalImageArchitecture;

    @Autowired
    private BuildAgentDockerService buildAgentDockerService;

    @Autowired
    private ApplicationContext applicationContext;

    private LocalCIResultListenerService localCIResultListenerService;

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

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

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
        log.info("Running with Docker architecture: {}", architecture);
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
    void tearDownRealDockerClient() throws Exception {
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void setupCExerciseWithGcc_usesConfiguredDockerImage() throws Exception {
        assertSuccessfulStudentBuildUsesDockerImage(ProjectType.GCC, GCC_DOCKER_IMAGE, expectedSuccessfulTestCaseCount(ProjectType.GCC));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void setupCExerciseWithFact_usesConfiguredDockerImage() throws Exception {
        assertSuccessfulStudentBuildUsesDockerImage(ProjectType.FACT, FACT_DOCKER_IMAGE, FACT_SUCCESSFUL_TEST_CASES);
    }

    private void assertSuccessfulStudentBuildUsesDockerImage(ProjectType projectType, String expectedDockerImage, int expectedSuccessfulTestCaseCount) throws Exception {
        ensureDockerImageAvailable(expectedDockerImage);
        configureProgrammingExercise(projectType);
        replaceExerciseTestCases(projectType);
        var baseRepositories = createAndSeedBaseRepositories(projectType);

        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String studentRepositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey1, student1Login);
        LocalRepository studentRepository = RepositoryExportTestUtil.seedLocalVcBareFrom(localVCLocalCITestService, projectKey1, studentRepositorySlug,
                baseRepositories.solutionRepository());
        programmingExerciseStudentParticipationRepository.saveAndFlush(participation);

        // Sanitizer-based test cases (especially in the GCC variant) can flake intermittently
        // in Docker due to ASLR / sanitizer interactions on CI runners. Retry the build once
        // before failing — a real configuration regression will fail twice.
        AssertionError lastFailure = null;
        for (int attempt = 1; attempt <= MAX_BUILD_ATTEMPTS; attempt++) {
            String triggerFileName = "trigger-attempt-" + attempt + ".txt";
            String commitHash = localVCLocalCITestService.commitFile(studentRepository.workingCopyGitRepoFile.toPath(), studentRepository.workingCopyGitRepo, triggerFileName);
            studentRepository.workingCopyGitRepo.push().call();
            RepositoryExportTestUtil.waitForBareRepositoryReady(studentRepository);
            ProgrammingSubmission submission = createManualSubmission(participation, commitHash);
            localCITriggerService.triggerBuild(participation, commitHash, RepositoryType.USER);

            awaitCreatedBuildJob(participation.getId());
            BuildJob buildJob = awaitCompletedBuildJob(participation.getId());
            ProgrammingSubmission persistedSubmission = awaitLatestSubmissionWithResult(participation.getId());

            try {
                assertBuildResultMatchesExpectations(buildJob, persistedSubmission, submission, commitHash, expectedDockerImage, projectType, expectedSuccessfulTestCaseCount);
                return;
            }
            catch (AssertionError failure) {
                lastFailure = failure;
                logBuildJobDiagnostics(buildJob, attempt, projectType, failure);
            }
        }
        throw lastFailure;
    }

    private void assertBuildResultMatchesExpectations(BuildJob buildJob, ProgrammingSubmission persistedSubmission, ProgrammingSubmission expectedSubmission, String commitHash,
            String expectedDockerImage, ProjectType projectType, int expectedSuccessfulTestCaseCount) {
        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(buildJob.getDockerImage()).isEqualTo(expectedDockerImage);

        assertThat(persistedSubmission.getId()).isEqualTo(expectedSubmission.getId());
        assertThat(persistedSubmission.getCommitHash()).isEqualTo(commitHash);
        assertThat(persistedSubmission.getLatestResult()).isNotNull();
        assertThat(persistedSubmission.isBuildFailed()).isFalse();
        Result result = persistedSubmission.getLatestResult();
        assertThat(result.getCompletionDate()).isNotNull();

        // Log the DB test case state at assertion time — helps diagnose flaky testCaseCount mismatches
        var dbTestCases = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true);
        log.info("At assertion time for exercise {} (projectType={}): result has testCaseCount={}, passedTestCaseCount={}, score={}; DB has {} active test cases (names: {})",
                programmingExercise.getId(), projectType, result.getTestCaseCount(), result.getPassedTestCaseCount(), result.getScore(), dbTestCases.size(),
                dbTestCases.stream().map(ProgrammingExerciseTestCase::getTestName).sorted().toList());

        // Attach per-feedback PASS/FAIL detail to the assertion description so a CI failure
        // names the offending test case directly instead of just printing the numeric mismatch.
        String feedbackDiagnostics = loadAndFormatTestCaseFeedback(result.getId());
        assertThat(result.getScore()).as("Score for project type %s; feedback:%n%s", projectType, feedbackDiagnostics).isEqualTo(100.0);
        assertThat(result.getTestCaseCount()).as("Test case count for project type %s; feedback:%n%s", projectType, feedbackDiagnostics).isEqualTo(expectedSuccessfulTestCaseCount);
        assertThat(result.getPassedTestCaseCount()).as("Passed test case count for project type %s; feedback:%n%s", projectType, feedbackDiagnostics)
                .isEqualTo(expectedSuccessfulTestCaseCount);
    }

    private String loadAndFormatTestCaseFeedback(long resultId) {
        // Re-fetch the result with feedbacks + test cases eagerly loaded; the persistedSubmission
        // returned from the repository above is detached, so result.getFeedbacks() would otherwise
        // hit a LazyInitializationException.
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

    private void configureProgrammingExercise(ProjectType projectType) throws Exception {
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.C);
        programmingExercise.setProjectType(projectType);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.getBuildConfig().setBuildScript(null);
        var phases = buildPhasesTemplateService.getDefaultBuildPlanPhasesFor(programmingExercise);
        var dockerImage = buildPhasesTemplateService.getDefaultDockerImageFor(programmingExercise);
        var buildPlanPhasesDTO = new BuildPlanPhasesDTO(phases, dockerImage);
        programmingExercise.getBuildConfig().setBuildPlanConfiguration(buildPlanPhasesDTO.toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        // Capture the managed entity returned by merge() to avoid stale detached entity issues
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
    }

    private void replaceExerciseTestCases(ProjectType projectType) {
        List<String> testCaseNames = switch (projectType) {
            case FACT -> FACT_TEST_CASE_NAMES;
            case GCC -> getGccTestCaseNames();
            default -> throw new IllegalArgumentException("Unsupported project type: " + projectType);
        };

        var existingTestCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        log.info("Deleting {} existing test cases for exercise {} (names: {})", existingTestCases.size(), programmingExercise.getId(),
                existingTestCases.stream().map(ProgrammingExerciseTestCase::getTestName).sorted().toList());
        testCaseRepository.deleteAll(existingTestCases);
        testCaseRepository.flush();

        List<ProgrammingExerciseTestCase> testCases = testCaseNames.stream().map(testCaseName -> new ProgrammingExerciseTestCase().testName(testCaseName).weight(1.0).active(true)
                .exercise(programmingExercise).visibility(de.tum.cit.aet.artemis.assessment.domain.Visibility.ALWAYS).bonusMultiplier(1D).bonusPoints(0D)).toList();
        testCaseRepository.saveAll(testCases);
        testCaseRepository.flush();

        // Verify the exact number of test cases persisted — any mismatch here means a JPA/cache issue
        var verifiedTestCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        log.info("After replacement: {} test cases in DB for exercise {} (expected {}, names: {})", verifiedTestCases.size(), programmingExercise.getId(), testCaseNames.size(),
                verifiedTestCases.stream().map(ProgrammingExerciseTestCase::getTestName).sorted().toList());
        assertThat(verifiedTestCases).as("Test case count after replacement for exercise %d", programmingExercise.getId()).hasSize(testCaseNames.size());
    }

    private RepositoryExportTestUtil.BaseRepositories createAndSeedBaseRepositories(ProjectType projectType) throws Exception {
        RepositoryExportTestUtil.BaseRepositories baseRepositories = RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService,
                programmingExercise);

        String templateDirectory = switch (projectType) {
            case FACT -> "fact";
            case GCC -> "gcc";
            default -> throw new IllegalArgumentException("Unsupported project type: " + projectType);
        };

        seedRepositoryFromTemplate(baseRepositories.templateRepository(), Path.of("templates", "c", templateDirectory, "exercise"), "exercise");
        seedRepositoryFromTemplate(baseRepositories.solutionRepository(), Path.of("templates", "c", templateDirectory, "solution"), "solution");
        seedRepositoryFromTemplate(baseRepositories.testsRepository(), Path.of("templates", "c", templateDirectory, "test"), "tests");

        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        // Verify test cases were not disturbed by saving the exercise entity (orphanRemoval/cascade check)
        var testCasesAfterRepoSetup = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true);
        log.info("After repository setup: {} active test cases for exercise {} (names: {})", testCasesAfterRepoSetup.size(), programmingExercise.getId(),
                testCasesAfterRepoSetup.stream().map(ProgrammingExerciseTestCase::getTestName).sorted().toList());

        return baseRepositories;
    }

    private void seedRepositoryFromTemplate(LocalRepository repository, Path resourcePath, String commitSuffix) throws Exception {
        Resource[] resources = resourceLoaderService.getFileResources(resourcePath);
        FileUtil.copyResources(resources, resourcePath, repository.workingCopyGitRepoFile.toPath(), true);
        repository.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(repository.workingCopyGitRepo).setMessage("Seed " + commitSuffix + " repository").call();
        repository.workingCopyGitRepo.push().call();
        RepositoryExportTestUtil.waitForBareRepositoryReady(repository);
    }

    private ProgrammingSubmission createManualSubmission(ProgrammingExerciseStudentParticipation participation, String commitHash) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setCommitHash(commitHash);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(SubmissionType.MANUAL);
        participation.addSubmission(submission);
        return programmingSubmissionRepository.saveAndFlush(submission);
    }

    private void ensureDockerImageAvailable(String dockerImage) {
        new RemoteDockerImage(DockerImageName.parse(dockerImage)).get();
    }

    private BuildJob awaitCreatedBuildJob(Long participationId) {
        try {
            await().atMost(BUILD_JOB_CREATION_TIMEOUT).until(() -> buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(participationId).isPresent());
        }
        catch (ConditionTimeoutException e) {
            throw new AssertionError(buildFailureDiagnostics(participationId), e);
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
            throw new AssertionError(buildFailureDiagnostics(participationId), e);
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
            throw new AssertionError(buildFailureDiagnostics(participationId), e);
        }

        return programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId).orElseThrow();
    }

    private String buildFailureDiagnostics(Long participationId) {
        StringBuilder diagnostics = new StringBuilder("Real LocalCI Docker build did not finish as expected for participation ").append(participationId);

        diagnostics.append(System.lineSeparator()).append("Queued jobs: ").append(distributedDataAccessService.getQueuedJobs().stream()
                .map(job -> job.id() + ":" + job.buildConfig().dockerImage() + ":" + job.buildConfig().commitHashToBuild()).toList());
        diagnostics.append(System.lineSeparator()).append("Processing jobs: ")
                .append(distributedDataAccessService.getProcessingJobs().stream().map(job -> job.id() + ":" + job.buildConfig().dockerImage() + ":" + job.status()).toList());
        diagnostics.append(System.lineSeparator()).append("Result queue ids: ").append(distributedDataAccessService.getResultQueueIds());
        diagnostics.append(System.lineSeparator()).append("Build agents: ").append(distributedDataAccessService.getBuildAgentInformation().stream()
                .map(agent -> agent.buildAgent().name() + ":" + agent.status() + ":" + agent.numberOfCurrentBuildJobs()).toList());

        buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(participationId).ifPresent(buildJob -> {
            diagnostics.append(System.lineSeparator()).append("Latest build job: ").append(buildJob.getBuildJobId()).append(" status=").append(buildJob.getBuildStatus())
                    .append(" dockerImage=").append(buildJob.getDockerImage());

            appendBuildLogFile(diagnostics, buildJob);
        });

        programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId).ifPresent(submission -> {
            diagnostics.append(System.lineSeparator()).append("Latest submission commit=").append(submission.getCommitHash()).append(" buildFailed=")
                    .append(submission.isBuildFailed());
            if (submission.getLatestResult() != null) {
                diagnostics.append(" testCaseCount=").append(submission.getLatestResult().getTestCaseCount()).append(" passedTestCaseCount=")
                        .append(submission.getLatestResult().getPassedTestCaseCount());
                appendPersistedBuildLogs(diagnostics, submission);
            }
        });

        return diagnostics.toString();
    }

    private void logBuildJobDiagnostics(BuildJob buildJob, int attempt, ProjectType projectType, AssertionError failure) {
        StringBuilder diag = new StringBuilder();
        diag.append("Docker build attempt ").append(attempt).append(" for project type ").append(projectType).append(" did not match expectations.");
        diag.append(System.lineSeparator()).append("Failure: ").append(failure.getMessage());
        diag.append(System.lineSeparator()).append("Build job: id=").append(buildJob.getBuildJobId()).append(", status=").append(buildJob.getBuildStatus()).append(", dockerImage=")
                .append(buildJob.getDockerImage());
        if (buildJob.getBuildStartDate() != null && buildJob.getBuildCompletionDate() != null) {
            diag.append(", duration=").append(Duration.between(buildJob.getBuildStartDate().toInstant(), buildJob.getBuildCompletionDate().toInstant()));
        }
        appendBuildLogFile(diag, buildJob);
        if (attempt < MAX_BUILD_ATTEMPTS) {
            log.warn("Build attempt {} failed, retrying. Full diagnostics:{}{}", attempt, System.lineSeparator(), diag);
        }
        else {
            log.error("Build attempt {} failed (final attempt). Full diagnostics:{}{}", attempt, System.lineSeparator(), diag);
        }
    }

    private void appendBuildLogFile(StringBuilder diagnostics, BuildJob buildJob) {
        try {
            FileSystemResource logResource = buildLogEntryService.retrieveBuildLogsFromFileForBuildJob(buildJob.getBuildJobId());
            if (logResource != null && logResource.exists()) {
                String logContent = Files.readString(logResource.getFile().toPath());
                diagnostics.append(System.lineSeparator()).append("Build log file tail:").append(System.lineSeparator())
                        .append(trimToLastCharacters(logContent, MAX_DIAGNOSTIC_LOG_LENGTH));
            }
        }
        catch (IOException ignored) {
            // Best-effort diagnostics only.
        }
    }

    private void appendPersistedBuildLogs(StringBuilder diagnostics, ProgrammingSubmission submission) {
        var buildLogs = buildLogEntryService.getLatestBuildLogs(submission);
        if (buildLogs.isEmpty()) {
            return;
        }

        StringBuilder persistedLogs = new StringBuilder();
        for (BuildLogEntry buildLog : buildLogs) {
            persistedLogs.append(buildLog.getLog());
        }

        diagnostics.append(System.lineSeparator()).append("Persisted build log tail:").append(System.lineSeparator())
                .append(trimToLastCharacters(persistedLogs.toString(), MAX_DIAGNOSTIC_LOG_LENGTH));
    }

    private String trimToLastCharacters(String input, int maxCharacters) {
        if (input.length() <= maxCharacters) {
            return input;
        }
        return input.substring(input.length() - maxCharacters);
    }

    private void initializeLazyLocalCIServices() {
        applicationContext.getBean(LocalCIEventListenerService.class);
        applicationContext.getBean(LocalCIResultProcessingService.class);
        localCIResultListenerService = applicationContext.getBean(LocalCIResultListenerService.class);
    }

    private int expectedSuccessfulTestCaseCount(ProjectType projectType) {
        return switch (projectType) {
            case FACT -> FACT_SUCCESSFUL_TEST_CASES;
            case GCC -> getGccTestCaseNames().size();
            default -> throw new IllegalArgumentException("Unsupported project type: " + projectType);
        };
    }

    private List<String> getGccTestCaseNames() {
        // TestCompileLeak and TestOutputLSan are excluded because LeakSanitizer requires the liblsan library
        // and the SYS_PTRACE capability, both of which are inconsistently available or restricted in CI Docker environments.
        // We only include the 6 core tests that are reliable across all platforms.
        return List.of("TestCompile", "TestOutput", "TestCompileASan", "TestOutputASan", "TestCompileUBSan", "TestOutputUBSan");
    }

    private String normalizeDockerArchitecture(String dockerArchitecture) {
        return switch (dockerArchitecture) {
            case "aarch64" -> "arm64";
            case "x86_64" -> "amd64";
            default -> dockerArchitecture;
        };
    }
}

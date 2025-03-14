package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCALCI_RESULTS_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.hazelcast.collection.IQueue;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultBuildJob;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

// TODO re-enable tests. when Executed in isolation they work

// TestInstance.Lifecycle.PER_CLASS allows all test methods in this class to share the same instance of the test class.
// This reduces the overhead of repeatedly creating and tearing down a new Spring application context for each test method.
// This is especially useful when the test setup is expensive or when we want to share resources, such as database connections or mock objects, across multiple tests.
// In this case, we want to share the same GitService and UsernamePasswordCredentialsProvider.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

// ExecutionMode.SAME_THREAD ensures that all tests within this class are executed sequentially in the same thread, rather than in parallel or in a different thread.
// This is important in the context of LocalCI because it avoids potential race conditions or inconsistencies that could arise if multiple test methods are executed
// concurrently. For example, it prevents overloading the LocalCI's result processing system with too many build job results at the same time, which could lead to flaky tests
// or timeouts. By keeping everything in the same thread, we maintain more predictable and stable test behavior, while not increasing the test execution time significantly.
@Execution(ExecutionMode.SAME_THREAD)
class LocalCIIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localciint";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    private LocalRepository studentAssignmentRepository;

    private LocalRepository testsRepository;

    private String commitHash;

    private IQueue<BuildJobQueueItem> queuedJobs;

    private IMap<String, BuildJobQueueItem> processingJobs;

    @BeforeAll
    void setupAll() {
        CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(localVCUsername, localVCPassword));
    }

    @AfterAll
    void cleanupAll() {
        this.gitService.init();
    }

    @BeforeEach
    void initRepositories() throws Exception {
        studentAssignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);
        commitHash = localVCLocalCITestService.commitFile(studentAssignmentRepository.localRepoFile.toPath(), studentAssignmentRepository.localGit);
        studentAssignmentRepository.localGit.push().call();

        testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, testsRepositorySlug);
        localVCLocalCITestService.commitFile(testsRepository.localRepoFile.toPath(), testsRepository.localGit);
        testsRepository.localGit.push().call();

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, LOCALCI_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));

        localVCLocalCITestService.mockInspectImage(dockerClient);

        queuedJobs = hazelcastInstance.getQueue("buildJobQueue");
        processingJobs = hazelcastInstance.getMap("processingJobs");
    }

    @AfterEach
    void removeRepositories() throws IOException {
        studentAssignmentRepository.resetLocalRepo();
        testsRepository.resetLocalRepo();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitViaOnlineEditor() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        request.postWithoutLocation("/api/programming/repository/" + studentParticipation.getId() + "/commit", null, HttpStatus.OK, null);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), null, 1, false);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildJobPersistence() {
        // Stop the build agent to prevent the build job from being processed.
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptional.isPresent();
        });

        Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());

        BuildJob buildJob = buildJobOptional.orElseThrow();

        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.QUEUED);
        assertThat(buildJob.getRepositoryType()).isEqualTo(RepositoryType.USER);
        assertThat(buildJob.getCommitHash()).isEqualTo(commitHash);
        assertThat(buildJob.getTriggeredByPushTo()).isEqualTo(RepositoryType.USER);
        assertThat(buildJob.getCourseId()).isEqualTo(course.getId());
        assertThat(buildJob.getExerciseId()).isEqualTo(programmingExercise.getId());
        assertThat(buildJob.getParticipationId()).isEqualTo(studentParticipation.getId());
        assertThat(buildJob.getDockerImage()).isEqualTo(programmingExercise.getBuildConfig().getWindfile().metadata().docker().getFullImageName());
        assertThat(buildJob.getRepositoryName()).isEqualTo(assignmentRepositorySlug);
        assertThat(buildJob.getPriority()).isEqualTo(2);
        assertThat(buildJob.getRetryCount()).isEqualTo(0);
        assertThat(buildJob.getName()).isNotEmpty();
        assertThat(buildJob.getBuildAgentAddress()).isNull();
        assertThat(buildJob.getBuildStartDate()).isNull();
        assertThat(buildJob.getBuildCompletionDate()).isNull();

        // resume the build agent
        sharedQueueProcessingService.init();

        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            Optional<BuildJob> buildJobOptionalTemp = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptionalTemp.isPresent() && buildJobOptionalTemp.get().getBuildStatus() == BuildStatus.BUILDING;
        });

        await().atMost(15, TimeUnit.SECONDS).until(() -> {
            Optional<BuildJob> buildJobOptionalTemp = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptionalTemp.isPresent() && buildJobOptionalTemp.get().getBuildStatus() == BuildStatus.SUCCESSFUL;
        });

        buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
        buildJob = buildJobOptional.orElseThrow();

        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(buildJob.getBuildStartDate()).isNotNull();
        assertThat(buildJob.getBuildCompletionDate()).isNotNull();
        assertThat(buildJob.getBuildAgentAddress()).isNotEmpty();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildJobTimeoutPersistence() {
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
            int originalTimeout = buildConfig.getTimeoutSeconds();
            buildConfig.setTimeoutSeconds(1);
            programmingExerciseBuildConfigRepository.save(buildConfig);

            // delay the inspectImageCmd.exec() method by 1 second to simulate a timeout
            InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
            InspectImageResponse inspectImageResponse = new InspectImageResponse();
            doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
            doAnswer(invocation -> {
                var future = scheduler.schedule(() -> inspectImageResponse, 3, TimeUnit.SECONDS);
                return future.get(4, TimeUnit.SECONDS);
            }).when(inspectImageCmd).exec();

            ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

            localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

            await().until(() -> {
                Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
                return buildJobOptional.isPresent() && buildJobOptional.get().getBuildStatus() != BuildStatus.BUILDING
                        && buildJobOptional.get().getBuildStatus() != BuildStatus.QUEUED;
            });

            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());

            BuildJob buildJob = buildJobOptional.orElseThrow();

            assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.TIMEOUT);
            assertThat(buildJob.getRepositoryType()).isEqualTo(RepositoryType.USER);
            assertThat(buildJob.getCommitHash()).isEqualTo(commitHash);
            assertThat(buildJob.getTriggeredByPushTo()).isEqualTo(RepositoryType.USER);
            assertThat(buildJob.getCourseId()).isEqualTo(course.getId());
            assertThat(buildJob.getExerciseId()).isEqualTo(programmingExercise.getId());
            assertThat(buildJob.getParticipationId()).isEqualTo(studentParticipation.getId());
            assertThat(buildJob.getDockerImage()).isEqualTo(programmingExercise.getBuildConfig().getWindfile().metadata().docker().getFullImageName());
            assertThat(buildJob.getRepositoryName()).isEqualTo(assignmentRepositorySlug);
            assertThat(buildJob.getPriority()).isEqualTo(2);
            assertThat(buildJob.getRetryCount()).isEqualTo(0);
            assertThat(buildJob.getName()).isNotEmpty();
            assertThat(buildJob.getBuildStartDate()).isNotNull();
            assertThat(buildJob.getBuildCompletionDate()).isNotNull();
            assertThat(buildJob.getBuildAgentAddress()).isNotEmpty();

            buildConfig.setTimeoutSeconds(originalTimeout);
            programmingExerciseBuildConfigRepository.save(buildConfig);
        }
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMissingBuildJobCheck() {
        // Stop the build agent to prevent the build job from being processed.
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getBuildStatus() == BuildStatus.QUEUED;
        });

        Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());

        BuildJob buildJob = buildJobOptional.orElseThrow();

        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.QUEUED);

        buildJob.setBuildSubmissionDate(ZonedDateTime.now().minusMinutes(6));
        buildJobRepository.save(buildJob);

        hazelcastInstance.getQueue("buildJobQueue").clear();

        localCIEventListenerService.checkPendingBuildJobsStatus();

        buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
        buildJob = buildJobOptional.orElseThrow();
        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.MISSING);

        // resume the build agent
        sharedQueueProcessingService.init();
    }

    @Test
    void testInvalidLocalVCRepositoryUri() {
        // The local repository cannot be resolved to a valid LocalVCRepositoryUri as it is not located at the correct base path and is not a bare repository.
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> localVCServletService.processNewPush(commitHash, studentAssignmentRepository.localGit.getRepository()))
                .withMessageContaining("Could not create valid repository URI from path");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testNoParticipationWhenPushingToTestsRepository() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // When pushing to the tests repository, the local VC filters do not fetch the participation, as there is no participation for the tests repository.
        // However, the local CI system will trigger builds of the solution and template repositories, which the participations are needed for and the processNewPush method will
        // throw an exception in case there is no participation.
        String expectedErrorMessage = "Could not find participation for repository";

        // student participation
        participationVcsAccessTokenService.deleteByParticipationId(studentParticipation.getId());
        programmingExerciseStudentParticipationRepository.delete(studentParticipation);
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository()))
                .withMessageContaining(expectedErrorMessage);

        // solution participation
        LocalRepository solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, solutionRepositorySlug);
        String solutionCommitHash = localVCLocalCITestService.commitFile(solutionRepository.localRepoFile.toPath(), solutionRepository.localGit);
        solutionRepository.localGit.push().call();
        programmingExercise.setSolutionParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        solutionProgrammingExerciseParticipationRepository.delete(solutionParticipation);
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> localVCServletService.processNewPush(solutionCommitHash, solutionRepository.originGit.getRepository()))
                .withMessageContaining(expectedErrorMessage);

        // template participation
        LocalRepository templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, templateRepositorySlug);
        String templateCommitHash = localVCLocalCITestService.commitFile(templateRepository.localRepoFile.toPath(), templateRepository.localGit);
        templateRepository.localGit.push().call();
        programmingExercise.setTemplateParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        templateProgrammingExerciseParticipationRepository.delete(templateParticipation);

        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> localVCServletService.processNewPush(templateCommitHash, templateRepository.originGit.getRepository()))
                .withMessageContaining(expectedErrorMessage);

        // team participation
        programmingExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(programmingExercise);
        String teamShortName = "team1";
        String teamRepositorySlug = projectKey1.toLowerCase() + "-" + teamShortName;
        LocalRepository teamLocalRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, teamRepositorySlug);
        Team team = new Team();
        team.setName("Team 1");
        team.setShortName(teamShortName);
        team.setExercise(programmingExercise);
        team.setStudents(Set.of(student1));
        team.setOwner(student1);
        teamRepository.save(team);
        String teamCommitHash = localVCLocalCITestService.commitFile(teamLocalRepository.localRepoFile.toPath(), teamLocalRepository.localGit);
        teamLocalRepository.localGit.push().call();
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> localVCServletService.processNewPush(teamCommitHash, teamLocalRepository.originGit.getRepository())).withMessageContaining(expectedErrorMessage);

        // Cleanup
        solutionRepository.resetLocalRepo();
        templateRepository.resetLocalRepo();
        teamLocalRepository.resetLocalRepo();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCommitHashNull() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Should still work because in that case the latest commit should be retrieved from the repository.
        localVCServletService.processNewPush(null, studentAssignmentRepository.originGit.getRepository());
        // ToDo: Investigate why specifically this test requires so much time (all other << 5s)
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false, 120);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testNoExceptionWhenResolvingWrongCommitHash() {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Call processNewPush with a wrong commit hash. This should throw an exception.
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> localVCServletService.processNewPush(DUMMY_COMMIT_HASH, studentAssignmentRepository.originGit.getRepository()))
                .withMessageContaining("Could not resolve commit hash");

    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testProjectTypeIsNull() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        programmingExercise.setProjectType(null);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExerciseRepository.save(programmingExercise);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(participation.getId(), commitHash, 1, false);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testResultsNotFound() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Should return a build result that indicates that the build failed.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenThrow(new NotFoundException("Cannot find results"));

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        // Should return a build result that indicates that the build failed.
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, true, false, 0, 20);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testIOExceptionWhenParsingTestResults() {
        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(any());

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Return an InputStream from dockerClient.copyArchiveFromContainerCmd().exec() such that repositoryTarInputStream.getNextTarEntry() throws an IOException.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenReturn(new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("Cannot read from this dummy InputStream");
            }
        });

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

        await().untilAsserted(() -> verify(programmingMessagingService).notifyUserAboutNewResult(any(), Mockito.eq(studentParticipation)));

        // Should notify the user.
        verifyUserNotification(studentParticipation);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFaultyResultFiles() throws IOException {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCLocalCITestService.mockTestResults(dockerClient, FAULTY_FILES_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, true);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testLegacyResultFormat() throws IOException {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCLocalCITestService.mockTestResults(dockerClient, OLD_REPORT_FORMAT_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, false);

        studentParticipation = programmingExerciseStudentParticipationRepository
                .findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(studentParticipation.getId(), ZonedDateTime.now()).orElseThrow();
        var result = studentParticipation.getResults().iterator().next();

        var noPrintTest = result.getFeedbacks().stream().filter(feedback -> feedback.getTestCase().getTestName().equals("testMergeSort()")).findFirst().orElseThrow();
        assertThat(noPrintTest.getDetailText()).isEqualTo("Deine Einreichung enthält keine Ausgabe. (67cac2)");

        var todoTest = result.getFeedbacks().stream().filter(feedback -> feedback.getTestCase().getTestName().equals("testBubbleSort()")).findFirst().orElseThrow();
        assertThat(todoTest.getDetailText()).isEqualTo("""
                test `add` failed on ≥ 1 cases:
                (0, 0)
                Your submission raised an error Failure("TODO add")""");

        var filterTest = result.getFeedbacks().stream().filter(feedback -> feedback.getTestCase().getTestName().equals("testUseMergeSortForBigList()")).findFirst().orElseThrow();
        assertThat(filterTest.getDetailText()).isEqualTo("""
                test `filter` failed on ≥ 1 cases:
                (even, [1; 2; 3; 4])
                Your submission raised an error Failure("TODO filter")""");
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStaticCodeAnalysis() throws IOException {
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        List<Path> resultPaths = new ArrayList<>();
        resultPaths.add(SPOTBUGS_RESULTS_PATH);
        resultPaths.add(CHECKSTYLE_RESULTS_PATH);
        resultPaths.add(PMD_RESULTS_PATH);
        resultPaths.add(PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);

        localVCLocalCITestService.mockTestResults(dockerClient, resultPaths, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false, true, 15, null);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEmptyResultFile() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCLocalCITestService.mockTestResults(dockerClient, EMPTY_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, true);

        studentParticipation = programmingExerciseStudentParticipationRepository
                .findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(studentParticipation.getId(), ZonedDateTime.now()).orElseThrow();
        var result = studentParticipation.getResults().iterator().next();

        var buildLogs = buildLogEntryService.getLatestBuildLogs((ProgrammingSubmission) result.getSubmission());

        assertThat(buildLogs).isNotEmpty().anyMatch(log -> log.getLog().equals("The file results.xml does not contain any testcases.\n"))
                .noneMatch(log -> log.getLog().contains("Exception"));
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogs() throws IOException {

        // Adapt Docker Client mock to return build logs
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        doReturn(execStartCmd).when(dockerClient).execStartCmd(anyString());
        doReturn(execStartCmd).when(execStartCmd).withDetach(anyBoolean());
        doAnswer(invocation -> {
            // Use a raw type for the callback to avoid generic type issues
            ResultCallback<Frame> callback = invocation.getArgument(0);

            // Simulate receiving log entries.
            Frame logEntryFrame1 = mock(Frame.class);
            when(logEntryFrame1.getPayload()).thenReturn("Dummy log entry".getBytes());
            callback.onNext(logEntryFrame1);

            // Simulate the command completing
            callback.onComplete();

            return null;
        }).when(execStartCmd).exec(any());

        FileSystemResource buildLogs = null;

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        try {
            localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
            localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);

            var submissionOptional = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderByLegalSubmissionDateDesc(studentParticipation.getId());

            Result result = submissionOptional.map(ProgrammingSubmission::getLatestResult).orElseThrow(() -> new AssertionError("Submission has no results"));

            BuildJob buildJob = buildJobRepository.findBuildJobByResult(result).orElseThrow();

            Set<ResultBuildJob> resultBuildJobSet = buildJobRepository.findBuildJobIdsForResultIds(List.of(result.getId()));

            assertThat(resultBuildJobSet).hasSize(1);
            assertThat(resultBuildJobSet.iterator().next().buildJobId()).isEqualTo(buildJob.getBuildJobId());

            // Assert that the corresponding build job are stored in the file system
            assertThat(buildLogEntryService.buildJobHasLogFile(buildJob.getBuildJobId(), studentParticipation.getProgrammingExercise())).isTrue();

            // Retrieve the build logs from the file system
            buildLogs = buildLogEntryService.retrieveBuildLogsFromFileForBuildJob(buildJob.getBuildJobId());
            assertThat(buildLogs).isNotNull();
            assertThat(buildLogs.getFile().exists()).isTrue();

            String content = new String(Files.readAllBytes(Path.of(buildLogs.getFile().getAbsolutePath())));

            // Assert that the content contains the expected log entry
            assertThat(content).contains("Dummy log entry");
        }
        finally {
            // Delete log file
            if (buildLogs != null && buildLogs.getFile().exists()) {
                Files.deleteIfExists(Path.of(buildLogs.getFile().getAbsolutePath()));
            }
        }
    }

    private void verifyUserNotification(ProgrammingExerciseStudentParticipation participation) {
        await().untilAsserted(() -> verify(programmingMessagingService).notifyUserAboutNewResult(argThat((Result result) -> {
            assertThat(result.isSuccessful()).isFalse();
            return true;
        }), Mockito.eq(participation)));
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCustomCheckoutPaths() {
        var buildConfig = programmingExercise.getBuildConfig();
        buildConfig.setAssignmentCheckoutPath("customAssignmentPath");
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(participation.getId(), commitHash, 1, false);

        buildConfig.setAssignmentCheckoutPath("");
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDisableNetworkAccessAndEnvVars() {
        var buildConfig = programmingExercise.getBuildConfig();
        buildConfig.setDockerFlags("{\"network\": \"none\", \"env\": {\"key\": \"value\"}}");
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(participation.getId(), commitHash, 1, false);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPerfDockerFlags() {
        var buildConfig = programmingExercise.getBuildConfig();
        buildConfig.setDockerFlags("{\"cpuCount\": 4, \"memory\": 3072, \"memorySwap\": 2048}");
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(participation.getId(), commitHash, 1, false);
        verify(AbstractProgrammingIntegrationLocalCILocalVCTestBase.dockerClientMock.createContainerCmd(anyString())).withHostConfig(argThat(hostConfig -> {
            assertThat(hostConfig.getCpuQuota()).isEqualTo(4L * 100000);
            assertThat(hostConfig.getMemory()).isEqualTo(3072L * 1024 * 1024);
            assertThat(hostConfig.getMemorySwap()).isEqualTo(2048L * 1024 * 1024);
            return true;
        }));
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPauseAndResumeBuildAgent() {
        String buildAgentName = "artemis-build-agent-test";
        hazelcastInstance.getTopic("pauseBuildAgentTopic").publish(buildAgentName);

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        await().until(() -> {
            IQueue<BuildJobQueueItem> buildQueue = hazelcastInstance.getQueue("buildJobQueue");
            IMap<String, BuildJobQueueItem> buildJobMap = hazelcastInstance.getMap("processingJobs");
            BuildJobQueueItem buildJobQueueItem = buildQueue.peek();

            return buildJobQueueItem != null && buildJobQueueItem.buildConfig().commitHashToBuild().equals(commitHash) && !buildJobMap.containsKey(buildJobQueueItem.id());
        });

        hazelcastInstance.getTopic("resumeBuildAgentTopic").publish(buildAgentName);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildJobTimingInfo() {
        // Pause build agent processing
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();
        ProgrammingExerciseBuildStatistics buildStatistics = new ProgrammingExerciseBuildStatistics(programmingExercise.getId(), 20, 100);
        programmingExerciseBuildStatisticsRepository.save(buildStatistics);

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

        await().until(() -> queuedJobs.stream().anyMatch(buildJobQueueItem -> buildJobQueueItem.buildConfig().commitHashToBuild().equals(commitHash)
                && buildJobQueueItem.participationId() == studentParticipation.getId()));

        BuildJobQueueItem item = queuedJobs.stream().filter(i -> i.buildConfig().commitHashToBuild().equals(commitHash) && i.participationId() == studentParticipation.getId())
                .findFirst().orElseThrow();
        assertThat(item.jobTimingInfo().estimatedDuration()).isEqualTo(22);
        sharedQueueProcessingService.init();

        await().until(() -> processingJobs.values().stream().anyMatch(buildJobQueueItem -> buildJobQueueItem.buildConfig().commitHashToBuild().equals(commitHash)
                && buildJobQueueItem.participationId() == studentParticipation.getId()));
        item = processingJobs.values().stream().filter(i -> i.buildConfig().commitHashToBuild().equals(commitHash) && i.participationId() == studentParticipation.getId())
                .findFirst().orElseThrow();
        assertThat(item.jobTimingInfo().estimatedDuration()).isEqualTo(22);
        assertThat(item.jobTimingInfo().estimatedCompletionDate()).isCloseTo(item.jobTimingInfo().buildStartDate().plusSeconds(22), within(500, ChronoUnit.MILLIS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetSubmissionReturnsWhenSubmissionProcessing() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission.setCommitHash(commitHash);
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");

        JobTimingInfo jobTimingInfo = new JobTimingInfo(ZonedDateTime.now().minusSeconds(30), ZonedDateTime.now(), null, ZonedDateTime.now().plusSeconds(30), 60);
        BuildConfig buildConfig = new BuildConfig(null, null, commitHash, commitHash, null, null, null, null, false, false, null, 0, null, null, null, null);
        BuildJobQueueItem buildJobQueueItem = new BuildJobQueueItem("1", "1", null, submission.getParticipation().getId(), 1L, programmingExercise.getId(), 0, 1, null, null,
                jobTimingInfo, buildConfig, null);

        processingJobs.put(buildJobQueueItem.id(), buildJobQueueItem);
        var submissionDto = request.get("/api/programming/programming-exercise-participations/" + submission.getParticipation().getId() + "/latest-pending-submission",
                HttpStatus.OK, SubmissionDTO.class);
        processingJobs.delete(buildJobQueueItem.id());

        assertThat(submissionDto).isNotNull();
        assertThat(submissionDto.isProcessing()).isTrue();
        assertThat(submissionDto.buildStartDate()).isNotNull();
        assertThat(submissionDto.estimatedCompletionDate()).isNotNull();
    }
}

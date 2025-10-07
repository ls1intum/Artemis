package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
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
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
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
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDetailsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultBuildJob;
import de.tum.cit.aet.artemis.core.domain.User;
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
@Isolated
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

    private IMap<String, BuildAgentInformation> buildAgentInformation;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    @Value("${artemis.continuous-integration.build-agent.display-name:}")
    private String buildAgentDisplayName;

    @Value("${artemis.continuous-integration.max-missing-job-retries:3}")
    private int maxMissingJobRetries;

    @BeforeAll
    void setupAll() {
        buildJobRepository.deleteAll();
        CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(localVCUsername, localVCPassword));
    }

    @AfterAll
    void cleanupAll() {
        buildJobRepository.deleteAll();
    }

    // helper method to process a new push
    private void processNewPush(String commitHash, Repository getRepository, User user) {
        localVCServletService.processNewPush(commitHash, getRepository, user, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @BeforeEach
    void initRepositories() throws Exception {
        studentAssignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);
        commitHash = localVCLocalCITestService.commitFile(studentAssignmentRepository.workingCopyGitRepoFile.toPath(), studentAssignmentRepository.workingCopyGitRepo);
        studentAssignmentRepository.workingCopyGitRepo.push().call();

        testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, testsRepositorySlug);
        localVCLocalCITestService.commitFile(testsRepository.workingCopyGitRepoFile.toPath(), testsRepository.workingCopyGitRepo);
        testsRepository.workingCopyGitRepo.push().call();

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        dockerClientTestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));

        dockerClientTestService.mockInspectImage(dockerClient);

        queuedJobs = hazelcastInstance.getQueue("buildJobQueue");
        processingJobs = hazelcastInstance.getMap("processingJobs");
        buildAgentInformation = hazelcastInstance.getMap("buildAgentInformation");
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

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());

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

            processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMissingBuildJobCheck() {
        // Stop the build agent to prevent the build job from being processed.
        sharedQueueProcessingService.removeListenerAndCancelScheduledFuture();

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getBuildStatus() == BuildStatus.QUEUED;
        });

        Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());

        BuildJob buildJob = buildJobOptional.orElseThrow();

        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.QUEUED);

        buildJob.setBuildSubmissionDate(ZonedDateTime.now().minusMinutes(6));
        buildJobRepository.save(buildJob);

        queuedJobs.clear();

        localCIMissingJobService.checkPendingBuildJobsStatus();

        buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
        buildJob = buildJobOptional.orElseThrow();
        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.MISSING);

        // resume the build agent
        sharedQueueProcessingService.init();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMissingBuildJobRetry() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptional.isPresent() && buildJobOptional.get().getBuildStatus() == BuildStatus.QUEUED;
        });

        BuildJob buildJob = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId()).orElseThrow();
        buildJob.setBuildStatus(BuildStatus.MISSING);
        buildJob.setBuildSubmissionDate(ZonedDateTime.now().minusMinutes(10));
        buildJobRepository.save(buildJob);

        localCIMissingJobService.retryMissingJobs();

        // job for participation should be retried so retry count should be 1 and status QUEUED
        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildJobIdDesc(buildJob.getParticipationId());
            if (buildJobOptional.isEmpty()) {
                return false;
            }
            BuildJob retriedBuildJob = buildJobOptional.get();
            return (retriedBuildJob.getBuildStatus() == BuildStatus.QUEUED || retriedBuildJob.getBuildStatus() == BuildStatus.BUILDING) && retriedBuildJob.getRetryCount() == 1;
        });
        processingJobs.clear();
        queuedJobs.clear();
    }

    @Test
    void testMissingBuildJobRetryLimit() {
        BuildJob buildJob = new BuildJob();
        buildJob.setBuildSubmissionDate(ZonedDateTime.now().minusMinutes(10));
        buildJob.setBuildStatus(BuildStatus.MISSING);
        buildJob.setRetryCount(maxMissingJobRetries);
        buildJob.setParticipationId(1L);
        buildJob = buildJobRepository.save(buildJob);

        localCIMissingJobService.retryMissingJobs();

        // latest build job for the participation should be the same because no retry over the limit
        BuildJob latestJob = buildJobRepository.findFirstByParticipationIdOrderByBuildJobIdDesc(buildJob.getParticipationId()).orElseThrow();
        assertThat(latestJob.getBuildJobId()).isEqualTo(buildJob.getBuildJobId());
        assertThat(latestJob.getBuildStatus()).isEqualTo(BuildStatus.MISSING);
        assertThat(latestJob.getRetryCount()).isEqualTo(maxMissingJobRetries);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testInvalidLocalVCRepositoryUri() {
        // this strange looking setup is required to create a Repository object with an invalid git path to trigger the exception.
        // the default LocalRepository now has a working git path, so we need to create a new one with an invalid path.
        Path path = Path.of("abc", "def", "file.txt");
        Repository repositoryWithInvalidPath = new Git(new Repository(new BaseRepositoryBuilder<>().setGitDir(path.toFile())) {

            @Override
            public void create(boolean bare) {

            }

            @Override
            public String getIdentifier() {
                return "";
            }

            @Override
            public ObjectDatabase getObjectDatabase() {
                return null;
            }

            @Override
            public RefDatabase getRefDatabase() {
                return null;
            }

            @Override
            public StoredConfig getConfig() {
                return null;
            }

            @Override
            public AttributesNodeProvider createAttributesNodeProvider() {
                return null;
            }

            @Override
            public void scanForRepoChanges() {

            }

            @Override
            public void notifyIndexChanged(boolean internal) {

            }
        }).getRepository();
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> processNewPush(commitHash, repositoryWithInvalidPath, userTestRepository.getUserWithGroupsAndAuthorities()))
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
                .isThrownBy(() -> processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities()))
                .withMessageContaining(expectedErrorMessage);

        // solution participation
        LocalRepository solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, solutionRepositorySlug);
        String solutionCommitHash = localVCLocalCITestService.commitFile(solutionRepository.workingCopyGitRepoFile.toPath(), solutionRepository.workingCopyGitRepo);
        solutionRepository.workingCopyGitRepo.push().call();
        programmingExercise.setSolutionParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        solutionProgrammingExerciseParticipationRepository.delete(solutionParticipation);
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> processNewPush(solutionCommitHash, solutionRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities()))
                .withMessageContaining(expectedErrorMessage);

        // template participation
        LocalRepository templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, templateRepositorySlug);
        String templateCommitHash = localVCLocalCITestService.commitFile(templateRepository.workingCopyGitRepoFile.toPath(), templateRepository.workingCopyGitRepo);
        templateRepository.workingCopyGitRepo.push().call();
        programmingExercise.setTemplateParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        templateProgrammingExerciseParticipationRepository.delete(templateParticipation);

        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> processNewPush(templateCommitHash, templateRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities()))
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
        String teamCommitHash = localVCLocalCITestService.commitFile(teamLocalRepository.workingCopyGitRepoFile.toPath(), teamLocalRepository.workingCopyGitRepo);
        teamLocalRepository.workingCopyGitRepo.push().call();
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> processNewPush(teamCommitHash, teamLocalRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities()))
                .withMessageContaining(expectedErrorMessage);

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
        processNewPush(null, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
        // ToDo: Investigate why specifically this test requires so much time (all other << 5s)
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false, 120);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testNoExceptionWhenResolvingWrongCommitHash() {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Call processNewPush with a wrong commit hash. This should throw an exception.
        assertThatExceptionOfType(VersionControlException.class).isThrownBy(
                () -> processNewPush(DUMMY_COMMIT_HASH, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities()))
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

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
        localVCLocalCITestService.testLatestSubmission(participation.getId(), commitHash, 1, false);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testResultsNotFound() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Should return a build result that indicates that the build failed.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenThrow(new NotFoundException("Cannot find results"));

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
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
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenReturn(new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("Cannot read from this dummy InputStream");
            }
        });

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());

        await().untilAsserted(() -> verify(programmingMessagingService).notifyUserAboutNewResult(any(), Mockito.eq(studentParticipation)));

        // Should notify the user.
        verifyUserNotification(studentParticipation);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFaultyResultFiles() throws IOException {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        dockerClientTestService.mockTestResults(dockerClient, FAULTY_FILES_TEST_RESULTS_PATH, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);
        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, true);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testLegacyResultFormat() throws IOException {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        dockerClientTestService.mockTestResults(dockerClient, OLD_REPORT_FORMAT_TEST_RESULTS_PATH, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);
        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, false);

        studentParticipation = programmingExerciseParticipationService.findStudentParticipationWithLatestSubmissionResultAndFeedbacksElseThrow(studentParticipation.getId());
        var result = participationUtilService.getResultsForParticipation(studentParticipation).iterator().next();

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

        dockerClientTestService.mockTestResults(dockerClient, resultPaths, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());

        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false, true, 15, null);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEmptyResultFile() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        dockerClientTestService.mockTestResults(dockerClient, EMPTY_TEST_RESULTS_PATH, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);
        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, true);

        studentParticipation = programmingExerciseParticipationService.findStudentParticipationWithLatestSubmissionResultAndFeedbacksElseThrow(studentParticipation.getId());
        var result = participationUtilService.getResultsForParticipation(studentParticipation).iterator().next();

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
            processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
            localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);

            var submissionOptional = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(studentParticipation.getId());

            Result result = submissionOptional.map(ProgrammingSubmission::getLatestResult).orElseThrow(() -> new AssertionError("Submission has no results"));

            BuildJob buildJob = buildJobRepository.findBuildJobByResult(result).orElseThrow();

            Set<ResultBuildJob> resultBuildJobSet = buildJobRepository.findBuildJobIdsWithResultForParticipationId(studentParticipation.getId());

            assertThat(resultBuildJobSet).hasSize(1);
            assertThat(resultBuildJobSet.iterator().next().buildJobId()).isEqualTo(buildJob.getBuildJobId());

            var names = programmingExerciseRepository.findNames(studentParticipation.getProgrammingExercise().getId());
            // Assert that the corresponding build job are stored in the file system
            assertThat(buildLogEntryService.buildJobHasLogFile(buildJob.getBuildJobId(), names)).isTrue();

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

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
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

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
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

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
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

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());
        await().until(() -> {
            IMap<String, BuildJobQueueItem> buildJobMap = hazelcastInstance.getMap("processingJobs");
            BuildJobQueueItem buildJobQueueItem = queuedJobs.peek();

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

        processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithGroupsAndAuthorities());

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

    @Test
    void testSelfPauseTriggersListenerAndEmailNotification() {
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        BuildAgentDTO buildAgentDTO = new BuildAgentDTO(buildAgentShortName, memberAddress, buildAgentDisplayName);
        BuildAgentInformation buildAgent = new BuildAgentInformation(buildAgentDTO, 0, 0, new ArrayList<>(List.of()), BuildAgentInformation.BuildAgentStatus.IDLE, null, null, 100);
        buildAgentInformation.put(memberAddress, buildAgent);
        int consecutiveFailedBuildJobs = 100;
        BuildAgentDetailsDTO updatedDetails = new BuildAgentDetailsDTO(0, 0, 0, 0, 0, 0, null, ZonedDateTime.now(), null, consecutiveFailedBuildJobs);
        BuildAgentInformation updatedInfo = new BuildAgentInformation(buildAgent.buildAgent(), buildAgent.maxNumberOfConcurrentBuildJobs(), buildAgent.numberOfCurrentBuildJobs(),
                buildAgent.runningBuildJobs(), BuildAgentInformation.BuildAgentStatus.SELF_PAUSED, buildAgent.publicSshKey(), updatedDetails,
                buildAgent.pauseAfterConsecutiveBuildFailures());

        buildAgentInformation.put(memberAddress, updatedInfo);
        await().until(() -> buildAgentInformation.get(memberAddress).status() == BuildAgentInformation.BuildAgentStatus.SELF_PAUSED);
        verify(mailService, timeout(1000)).sendBuildAgentSelfPausedEmailToAdmin(any(User.class), eq(buildAgent.buildAgent().name()), eq(consecutiveFailedBuildJobs));
    }
}

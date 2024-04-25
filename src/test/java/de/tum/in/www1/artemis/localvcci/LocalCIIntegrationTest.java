package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.config.Constants.LOCALCI_RESULTS_DIRECTORY;
import static de.tum.in.www1.artemis.config.Constants.LOCALCI_WORKING_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCServletService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

class LocalCIIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    private LocalVCServletService localVCServletService;

    @Autowired
    private BuildJobRepository buildJobRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    private LocalRepository studentAssignmentRepository;

    private LocalRepository testsRepository;

    private String commitHash;

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
    }

    @AfterEach
    void removeRepositories() throws IOException {
        studentAssignmentRepository.resetLocalRepo();
        testsRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitViaOnlineEditor() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        request.postWithoutLocation("/api/repository/" + studentParticipation.getId() + "/commit", null, HttpStatus.OK, null);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), null, 1, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildJobPersistence() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

        await().until(() -> {
            Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());
            return buildJobOptional.isPresent();
        });

        Optional<BuildJob> buildJobOptional = buildJobRepository.findFirstByParticipationIdOrderByBuildStartDateDesc(studentParticipation.getId());

        BuildJob buildJob = buildJobOptional.orElseThrow();

        assertThat(buildJob.getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
        assertThat(buildJob.getRepositoryType()).isEqualTo(RepositoryType.USER);
        assertThat(buildJob.getCommitHash()).isEqualTo(commitHash);
        assertThat(buildJob.getTriggeredByPushTo()).isEqualTo(RepositoryType.USER);
        assertThat(buildJob.getCourseId()).isEqualTo(course.getId());
        assertThat(buildJob.getExerciseId()).isEqualTo(programmingExercise.getId());
        assertThat(buildJob.getParticipationId()).isEqualTo(studentParticipation.getId());
        assertThat(buildJob.getDockerImage()).isEqualTo(programmingExercise.getWindfile().getMetadata().getDocker().getFullImageName());
        assertThat(buildJob.getRepositoryName()).isEqualTo(assignmentRepositorySlug);
        assertThat(buildJob.getBuildAgentAddress()).isNotEmpty();
        assertThat(buildJob.getPriority()).isEqualTo(2);
        assertThat(buildJob.getRetryCount()).isEqualTo(0);
        assertThat(buildJob.getName()).isNotEmpty();
        assertThat(buildJob.getBuildStartDate()).isNotNull();
        assertThat(buildJob.getBuildCompletionDate()).isNotNull();
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCommitHashNull() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Should still work because in that case the latest commit should be retrieved from the repository.
        localVCServletService.processNewPush(null, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testNoExceptionWhenResolvingWrongCommitHash() {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Call processNewPush with a wrong commit hash. This should throw an exception.
        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> localVCServletService.processNewPush(DUMMY_COMMIT_HASH, studentAssignmentRepository.originGit.getRepository()))
                .withMessageContaining("Could not resolve commit hash");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testProjectTypeIsNull() {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        programmingExercise.setProjectType(null);
        programmingExerciseRepository.save(programmingExercise);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(participation.getId(), commitHash, 1, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCannotFindResults() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Should return a build result that indicates that the build failed.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenThrow(new NotFoundException("Cannot find results"));

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        // Should return a build result that indicates that the build failed.
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testIOExceptionWhenParsingTestResults() {
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

        await().untilAsserted(() -> verify(programmingMessagingService).notifyUserAboutSubmissionError(Mockito.eq(studentParticipation), any()));

        // Should notify the user.
        verifyUserNotification(studentParticipation,
                "java.util.concurrent.ExecutionException: de.tum.in.www1.artemis.exception.LocalCIException: Error while parsing test results");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFaultyResultFiles() throws IOException {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCLocalCITestService.mockTestResults(dockerClient, FAULTY_FILES_TEST_RESULTS_PATH, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);
        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, true);
    }

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStaticCodeAnalysis() throws IOException {
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        List<Path> resultPaths = new ArrayList<>();
        resultPaths.add(SPOTBUGS_RESULTS_PATH);
        resultPaths.add(CHECKSTYLE_RESULTS_PATH);
        resultPaths.add(PMD_RESULTS_PATH);
        resultPaths.add(PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);

        localVCLocalCITestService.mockTestResults(dockerClient, resultPaths, LOCALCI_WORKING_DIRECTORY + LOCALCI_RESULTS_DIRECTORY);

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false, true, 15);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogs() throws IOException {

        // Adapt Docker Client mock to return build logs
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        doReturn(execStartCmd).when(dockerClient).execStartCmd(anyString());
        doReturn(execStartCmd).when(execStartCmd).withDetach(anyBoolean());
        doAnswer(invocation -> {
            // Use a raw type for the callback to avoid generic type issues
            ResultCallback callback = invocation.getArgument(0);

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

            long resultId = submissionOptional.map(ProgrammingSubmission::getLatestResult).map(Result::getId).orElseThrow(() -> new AssertionError("Submission has no results"));

            // Assert that the build logs for the result are stored in the file system
            assertThat(buildLogEntryService.resultHasLogFile(String.valueOf(resultId))).isTrue();

            // Retrieve the build logs from the file system
            buildLogs = buildLogEntryService.retrieveBuildLogsFromFileForResult(String.valueOf(resultId));
            assertThat(buildLogs).isNotNull();
            assertThat(buildLogs.getFile().exists()).isTrue();

            String content = new String(Files.readAllBytes(Paths.get(buildLogs.getFile().getAbsolutePath())));

            // Assert that the content contains the expected log entry
            assertThat(content).contains("Dummy log entry");
        }
        finally {
            // Delete log file
            if (buildLogs != null && buildLogs.getFile().exists()) {
                Files.deleteIfExists(Paths.get(buildLogs.getFile().getAbsolutePath()));
            }
        }
    }

    private void verifyUserNotification(Participation participation, String errorMessage) {
        BuildTriggerWebsocketError expectedError = new BuildTriggerWebsocketError(errorMessage, participation.getId());
        await().untilAsserted(
                () -> verify(programmingMessagingService).notifyUserAboutSubmissionError(Mockito.eq(participation), argThat((BuildTriggerWebsocketError actualError) -> {
                    assertThat(actualError.getError()).isEqualTo(expectedError.getError());
                    assertThat(actualError.getParticipationId()).isEqualTo(expectedError.getParticipationId());
                    return true;
                })));
    }
}

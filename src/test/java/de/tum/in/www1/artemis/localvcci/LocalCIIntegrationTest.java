package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIPushService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

class LocalCIIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    private LocalCIPushService localCIPushService;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    private LocalRepository studentAssignmentRepository;

    private String commitHash;

    @BeforeEach
    void initRepositories() throws Exception {
        studentAssignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);
        commitHash = localVCLocalCITestService.commitFile(studentAssignmentRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                studentAssignmentRepository.localGit);
        studentAssignmentRepository.localGit.push().call();
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the student repository.
        localVCLocalCITestService.mockCommitHash(dockerClient, commitHash);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));
    }

    @AfterEach
    void removeRepositories() throws IOException {
        studentAssignmentRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSubmitViaOnlineEditor() throws Exception {
        request.postWithoutLocation("/api/repository/" + studentParticipation.getId() + "/commit", null, HttpStatus.OK, null);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), null, 1, false);
    }

    @Test
    void testInvalidLocalVCRepositoryUrl() {
        // The local repository cannot be resolved to a valid LocalVCRepositoryUrl as it is not located at the correct base path and is not a bare repository.
        LocalCIException exception = assertThrows(LocalCIException.class,
                () -> localCIPushService.processNewPush(commitHash, studentAssignmentRepository.localGit.getRepository()));
        assertThat(exception.getMessage()).contains("Could not create valid repository URL from path");
    }

    @Test
    void testNoParticipation() throws Exception {
        // If there is no participation, processNewPush() should not throw an exception as that would cause the push to get stuck. However, no submission should be created.

        // student participation
        programmingExerciseStudentParticipationRepository.delete(studentParticipation);
        localCIPushService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        Optional<ProgrammingSubmission> studentSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderByLegalSubmissionDateDesc(studentParticipation.getId());
        assertThat(studentSubmission).isEmpty();

        // solution participation
        LocalRepository solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, solutionRepositorySlug);
        String solutionCommitHash = localVCLocalCITestService.commitFile(solutionRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                solutionRepository.localGit);
        solutionRepository.localGit.push().call();
        programmingExercise.setSolutionParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        solutionProgrammingExerciseParticipationRepository.delete(solutionParticipation);
        localCIPushService.processNewPush(solutionCommitHash, solutionRepository.originGit.getRepository());
        Optional<ProgrammingSubmission> solutionSubmission = programmingSubmissionRepository
                .findFirstByParticipationIdOrderByLegalSubmissionDateDesc(solutionParticipation.getId());
        assertThat(solutionSubmission).isEmpty();

        // template participation
        LocalRepository templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, templateRepositorySlug);
        String templateCommitHash = localVCLocalCITestService.commitFile(templateRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                templateRepository.localGit);
        templateRepository.localGit.push().call();
        programmingExercise.setTemplateParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        templateProgrammingExerciseParticipationRepository.delete(templateParticipation);
        localCIPushService.processNewPush(templateCommitHash, templateRepository.originGit.getRepository());
        Optional<ProgrammingSubmission> templateSubmission = programmingSubmissionRepository
                .findFirstByParticipationIdOrderByLegalSubmissionDateDesc(templateParticipation.getId());
        assertThat(templateSubmission).isEmpty();

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
        String teamCommitHash = localVCLocalCITestService.commitFile(teamLocalRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                teamLocalRepository.localGit);
        teamLocalRepository.localGit.push().call();
        localCIPushService.processNewPush(teamCommitHash, teamLocalRepository.originGit.getRepository());
        // No participation to fetch the submission for anyway. Just checking that no exception is thrown.

        // Cleanup
        solutionRepository.resetLocalRepo();
        templateRepository.resetLocalRepo();
        teamLocalRepository.resetLocalRepo();
    }

    @Test
    void testCommitHashNull() {
        // Should still work because in that case the latest commit should be retrieved from the repository.
        localCIPushService.processNewPush(null, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false);
    }

    @Test
    void testNoExceptionWhenResolvingWrongCommitHash() {
        // Call processNewPush with a wrong commit hash. This should not throw an exception, but the notifyUserAboutSubmissionError() method should have been called.
        localCIPushService.processNewPush(DUMMY_COMMIT_HASH, studentAssignmentRepository.originGit.getRepository());
        verifyUserNotification(studentParticipation, "Could not resolve commit hash " + DUMMY_COMMIT_HASH + " in repository");
    }

    @Test
    void testCannotRetrieveBuildScriptPath() throws IOException, URISyntaxException {
        when(resourceLoaderService.getResourceFilePath(Path.of("templates", "localci", "java", "build_and_run_tests.sh"))).thenThrow(new IOException("Resource does not exist"));
        // Should not throw an exception but notify the user about the error.
        localCIPushService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        verifyUserNotification(studentParticipation, "Could not retrieve build script.");
    }

    @Test
    void testProjectTypeIsNull() {
        programmingExercise.setProjectType(null);
        programmingExerciseRepository.save(programmingExercise);
        // Should not throw an exception but notify the user about the error.
        localCIPushService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        verifyUserNotification(studentParticipation, "Project type must be Gradle.");
    }

    @Test
    void testProjectTypeIsNullForTestsRepository() throws Exception {
        programmingExercise.setProjectType(null);
        programmingExerciseRepository.save(programmingExercise);

        String testsRepositorySlug = projectKey1.toLowerCase() + "-" + "tests";
        LocalRepository testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, testsRepositorySlug);
        String testsCommitHash = localVCLocalCITestService.commitFile(testsRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(), testsRepository.localGit);
        testsRepository.localGit.push().call();

        // Should not throw an exception but notify the user about the error.
        localCIPushService.processNewPush(testsCommitHash, testsRepository.originGit.getRepository());
        verifyUserNotification(solutionParticipation, "Project type must be Gradle.");

        testsRepository.resetLocalRepo();
    }

    @Test
    void testImageNotFound() throws InterruptedException {
        // dockerClient.inspectImageCmd().exec() throws NotFoundException.
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        when(dockerClient.inspectImageCmd(anyString())).thenReturn(inspectImageCmd);
        when(inspectImageCmd.exec()).thenThrow(new NotFoundException("Image not found"));

        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        PullImageResultCallback resultCallback = mock(PullImageResultCallback.class);
        when(dockerClient.pullImageCmd(anyString())).thenReturn(pullImageCmd);
        when(pullImageCmd.exec(any())).thenReturn(resultCallback);
        when(resultCallback.awaitCompletion()).thenThrow(new InterruptedException());

        // Should not throw an exception but notify the user about the error.
        localCIPushService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        verifyUserNotification(studentParticipation, "Error while preparing Docker container");
    }

    @Test
    void testCannotRetrieveCommitHash() {
        // Return an InputStream from dockerClient.copyArchiveFromContainerCmd().exec() such that repositoryTarInputStream.getNextTarEntry() throws an IOException.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches("/repositories/assignment-repository/.git/refs/heads/[^/]+");
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenReturn(new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("Cannot read from this dummy InputStream");
            }
        });

        localCIPushService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        // Should return a build result that indicates that the build failed.
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), null, 0, true);
    }

    @Test
    void testCannotFindResults() {
        // Should return a build result that indicates that the build failed.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches("/repositories/test-repository/build/test-results/test");
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenThrow(new NotFoundException("Cannot find results"));

        localCIPushService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        // Should return a build result that indicates that the build failed.
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 0, true);
    }

    @Test
    void testIOExceptionWhenParsingTestResults() {
        // Return an InputStream from dockerClient.copyArchiveFromContainerCmd().exec() such that repositoryTarInputStream.getNextTarEntry() throws an IOException.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches("/repositories/test-repository/build/test-results/test");
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenReturn(new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("Cannot read from this dummy InputStream");
            }
        });

        localCIPushService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        // Should notify the user.
        verifyUserNotification(studentParticipation, "de.tum.in.www1.artemis.exception.LocalCIException: Error while parsing test results");
    }

    @Test
    void testFaultyResultFiles() throws IOException {
        localVCLocalCITestService.mockTestResults(dockerClient, FAULTY_FILES_TEST_RESULTS_PATH);
        localCIPushService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        // Should notify the user.
        verifyUserNotification(studentParticipation, "de.tum.in.www1.artemis.exception.LocalCIException: Error while parsing test results");
    }

    private void verifyUserNotification(Participation participation, String errorMessage) {
        BuildTriggerWebsocketError expectedError = new BuildTriggerWebsocketError(errorMessage, participation.getId());
        verify(programmingMessagingService).notifyUserAboutSubmissionError(Mockito.eq(participation), argThat((BuildTriggerWebsocketError actualError) -> {
            assertEquals(expectedError.getError(), actualError.getError());
            assertEquals(expectedError.getParticipationId(), actualError.getParticipationId());
            return true;
        }));
    }
}

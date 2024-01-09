package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.service.connectors.localci.LocalCIContainerService.WORKING_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
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
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCServletService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

class LocalCIIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    private LocalVCServletService localVCServletService;

    private LocalRepository studentAssignmentRepository;

    private String commitHash;

    @BeforeEach
    void initRepositories() throws Exception {
        studentAssignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);
        commitHash = localVCLocalCITestService.commitFile(studentAssignmentRepository.localRepoFile.toPath(), studentAssignmentRepository.localGit);
        studentAssignmentRepository.localGit.push().call();
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, WORKING_DIRECTORY + "/testing-dir/build/test-results/test");
        localVCLocalCITestService.mockTestResults(dockerClient, PARTLY_SUCCESSFUL_TEST_RESULTS_PATH, WORKING_DIRECTORY + "/testing-dir/target/surefire-reports");
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(dockerClient, WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));
    }

    @AfterEach
    void removeRepositories() throws IOException {
        studentAssignmentRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitViaOnlineEditor() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        request.postWithoutLocation("/api/repository/" + studentParticipation.getId() + "/commit", null, HttpStatus.OK, null);
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), null, 1, false);
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
    void testCannotRetrieveCommitHash() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // An IO Exception happens when trying to retrieve the commit hash for the test repository. This should lead to a build result that indicates a failed build.

        // Return an InputStream from dockerClient.copyArchiveFromContainerCmd().exec() such that repositoryTarInputStream.getNextTarEntry() throws an IOException.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+");
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenReturn(new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("Cannot read from this dummy InputStream");
            }
        });

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        // Should return a build result that indicates that the build failed.
        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), null, 0, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCannotFindResults() {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Should return a build result that indicates that the build failed.
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(WORKING_DIRECTORY + "/testing-dir/build/test-results/test");
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
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(WORKING_DIRECTORY + "/testing-dir/build/test-results/test");
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
        verifyUserNotification(studentParticipation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFaultyResultFiles() throws IOException {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCLocalCITestService.mockTestResults(dockerClient, FAULTY_FILES_TEST_RESULTS_PATH, WORKING_DIRECTORY + "/testing-dir/build/test-results/test");
        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());
        await().untilAsserted(() -> verify(programmingMessagingService).notifyUserAboutSubmissionError(Mockito.eq(studentParticipation), any()));

        // Should notify the user.
        verifyUserNotification(studentParticipation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStaticCodeAnalysis() throws IOException {
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        localVCLocalCITestService.mockTestResults(dockerClient, SPOTBUGS_RESULTS_PATH, WORKING_DIRECTORY + "/testing-dir/target/spotbugsXml.xml");
        localVCLocalCITestService.mockTestResults(dockerClient, CHECKSTYLE_RESULTS_PATH, WORKING_DIRECTORY + "/testing-dir/target/checkstyle-result.xml");
        localVCLocalCITestService.mockTestResults(dockerClient, PMD_RESULTS_PATH, WORKING_DIRECTORY + "/testing-dir/target/pmd.xml");

        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.originGit.getRepository());

        localVCLocalCITestService.testLatestSubmission(studentParticipation.getId(), commitHash, 1, false, true, 15);
    }

    private void verifyUserNotification(Participation participation) {
        BuildTriggerWebsocketError expectedError = new BuildTriggerWebsocketError("de.tum.in.www1.artemis.exception.LocalCIException: Error while parsing test results",
                participation.getId());
        await().untilAsserted(
                () -> verify(programmingMessagingService).notifyUserAboutSubmissionError(Mockito.eq(participation), argThat((BuildTriggerWebsocketError actualError) -> {
                    assertThat(actualError.getError()).isEqualTo(expectedError.getError());
                    assertThat(actualError.getParticipationId()).isEqualTo(expectedError.getParticipationId());
                    return true;
                })));
    }
}

package de.tum.in.www1.artemis.localvcci;

import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.dockerjava.api.DockerClient;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests for the local VC system that should go through successfully to the local CI system.
 */
class LocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private DockerClient mockDockerClient;

    // ---- Repository handles ----
    private LocalRepository templateRepository;

    private String testsRepositorySlug;

    private LocalRepository testsRepository;

    private LocalRepository solutionRepository;

    private LocalRepository assignmentRepository;

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException {
        final String templateRepositorySlug = projectKey1.toLowerCase() + "-exercise";
        Path remoteTemplateRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, templateRepositorySlug);
        templateRepository = new LocalRepository(defaultBranch);
        templateRepository.configureRepos("localTemplate", remoteTemplateRepositoryFolder);

        testsRepositorySlug = projectKey1.toLowerCase() + "-tests";
        Path remoteTestsRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, testsRepositorySlug);
        testsRepository = new LocalRepository(defaultBranch);
        testsRepository.configureRepos("localTests", remoteTestsRepositoryFolder);

        final String solutionRepositorySlug = projectKey1.toLowerCase() + "-solution";
        Path remoteSolutionRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, solutionRepositorySlug);
        solutionRepository = new LocalRepository(defaultBranch);
        solutionRepository.configureRepos("localSolution", remoteSolutionRepositoryFolder);

        Path remoteAssignmentRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, assignmentRepositorySlug);
        assignmentRepository = new LocalRepository(defaultBranch);
        assignmentRepository.configureRepos("localAssignment", remoteAssignmentRepositoryFolder);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", dummyCommitHash), Map.of("testCommitHash", dummyCommitHash));
    }

    @AfterEach
    void removeRepositories() {
        localVCLocalCITestService.removeRepository(templateRepository);
        localVCLocalCITestService.removeRepository(testsRepository);
        localVCLocalCITestService.removeRepository(solutionRepository);
        localVCLocalCITestService.removeRepository(assignmentRepository);
    }

    // ---- Tests for the assignment repository ----

    /**
     * Test that the connection between the local VC and the local CI system is working.
     * Perform a push to the assignment repository and check that a submission is created and the local CI system successfully builds and tests the source code.
     */
    @Test
    void testPush_assignmentRepository_student() throws Exception {
        String commitHash = localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                assignmentRepository.localGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the assignment repository.
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(mockDockerClient, partlySuccessfulTestResultsPath);

        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testLastestSubmission(studentParticipation.getId(), commitHash, 1);
    }

    @Test
    void testPush_assignmentRepository() {
        // Instructors should be able to push to the student's assignment repository.
    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_beforeStartDate() {

    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_afterDueDate() {

    }

    // -------- practice mode ----

    @Test
    void testPush_assignmentRepository_student_practiceMode() {

    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_practiceMode() {
        // Teaching assistants and up should be able to push to the student's practice repository.
    }

    // -------- team mode ----

    @Test
    void testPush_assignmentRepository_student_teamMode() {

    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_teamMode() {
        // Teaching assistants and up should be able to push to the student's team repository.
    }

    // -------- exam mode ----

    @Test
    void testPush_assignmentRepository_student_examMode() {
        // In time, should succeed.
    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_examMode() {
        // Teaching assistants and up should be able to push to the student's exam repository.
    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_beforeExamStartDate() {
        // Should succeed.
    }

    @Test
    void testPush_assignmentRepository_teachingAssistant_afterExamEndDate() {
        // Should succeed.
    }

    // ---- Tests for the tests repository ----

    @Test
    void testFetchPush_testsRepository() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchThrowsException(testsRepository.localGit, student1Login, projectKey1, testsRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(testsRepository.localGit, student1Login, projectKey1, testsRepositorySlug, notAuthorized);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(testsRepository.localGit, tutor1Login, projectKey1, testsRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(testsRepository.localGit, tutor1Login, projectKey1, testsRepositorySlug, notAuthorized);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(testsRepository.localGit, instructorLogin, projectKey1, testsRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(testsRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(), testsRepository.localGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash of the tests repository for both the solution and the template repository.
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash, commitHash);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        // Mock for the solution repository build and for the template repository build that will be triggered as a result of updating the tests.
        Map<String, String> solutionBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(allSucceedTestResultsPath);
        Map<String, String> templateBuildTestResults = localVCLocalCITestService.createMapFromTestResultsFolder(allFailTestResultsPath);
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/build/test-results/test", solutionBuildTestResults,
                templateBuildTestResults);

        // Mock GitService.getOrCheckoutRepository().
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testsRepository.originRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(solutionParticipation);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(testsRepository.originRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(templateParticipation);

        localVCLocalCITestService.testPushSuccessful(testsRepository.localGit, instructorLogin, projectKey1, testsRepositorySlug);

        // Solution submissions created as a result from a push to the tests repository should contain the last commit of the tests repository.
        localVCLocalCITestService.testLastestSubmission(solutionParticipation.getId(), commitHash, 13);
        localVCLocalCITestService.testLastestSubmission(templateParticipation.getId(), commitHash, 0);
    }

    // ---- Tests for the solution repository ----

    @Test
    void testPush_solutionRepository_teachingAssistant() {

    }

    // ---- Tests for the template repository ----

    @Test
    void testPush_templateRepository_teachingAssistant() {

    }
}

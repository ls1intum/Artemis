package de.tum.in.www1.artemis.localvcci;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
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
    protected Git remoteTemplateGit;

    protected Path remoteTemplateRepositoryFolder;

    private String testsRepositorySlug;

    protected Path remoteTestsRepositoryFolder;

    protected Git remoteTestsGit;

    protected Path remoteAssignmentRepositoryFolder;

    private Git localTestsGit;

    private Path localTestsRepositoryFolder;

    protected Git remoteAssignmentGit;

    protected Path localAssignmentRepositoryFolder;

    protected Git localAssignmentGit;

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException {
        // Create template and tests repository
        final String templateRepositoryName = projectKey1.toLowerCase() + "-exercise";
        remoteTemplateRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, templateRepositoryName);
        LocalRepository templateRepository = new LocalRepository(defaultBranch);
        templateRepository.configureRepos("localTemplate", remoteTemplateRepositoryFolder);
        remoteTemplateGit = templateRepository.originGit;

        testsRepositorySlug = projectKey1.toLowerCase() + "-tests";
        remoteTestsRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, testsRepositorySlug);
        LocalRepository testsRepository = new LocalRepository(defaultBranch);
        testsRepository.configureRepos("localTests", remoteTestsRepositoryFolder);
        remoteTestsGit = testsRepository.originGit;
        localTestsRepositoryFolder = testsRepository.localRepoFile.toPath();
        localTestsGit = testsRepository.localGit;

        // Create remote assignment repository
        remoteAssignmentRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, assignmentRepositorySlug);
        LocalRepository assignmentRepository = new LocalRepository(defaultBranch);
        assignmentRepository.configureRepos("localAssignment", remoteAssignmentRepositoryFolder);
        remoteAssignmentGit = assignmentRepository.originGit;
        localAssignmentRepositoryFolder = assignmentRepository.localRepoFile.toPath();
        localAssignmentGit = assignmentRepository.localGit;

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", dummyCommitHash));
    }

    @AfterEach
    void removeRepositories() throws IOException {
        if (remoteTemplateGit != null) {
            remoteTemplateGit.close();
        }
        if (remoteTestsGit != null) {
            remoteTestsGit.close();
        }
        if (localTestsGit != null) {
            localTestsGit.close();
        }
        if (remoteAssignmentGit != null) {
            remoteAssignmentGit.close();
        }
        if (localAssignmentGit != null) {
            localAssignmentGit.close();
        }
        if (remoteTemplateRepositoryFolder != null && Files.exists(remoteTemplateRepositoryFolder)) {
            FileUtils.deleteDirectory(remoteTemplateRepositoryFolder.toFile());
        }
        if (remoteTestsRepositoryFolder != null && Files.exists(remoteTestsRepositoryFolder)) {
            FileUtils.deleteDirectory(remoteTestsRepositoryFolder.toFile());
        }
        if (localTestsRepositoryFolder != null && Files.exists(localTestsRepositoryFolder)) {
            FileUtils.deleteDirectory(localTestsRepositoryFolder.toFile());
        }
        if (remoteAssignmentRepositoryFolder != null && Files.exists(remoteAssignmentRepositoryFolder)) {
            FileUtils.deleteDirectory(remoteAssignmentRepositoryFolder.toFile());
        }
        if (localAssignmentRepositoryFolder != null && Files.exists(localAssignmentRepositoryFolder)) {
            FileUtils.deleteDirectory(localAssignmentRepositoryFolder.toFile());
        }
    }

    // ---- Tests for the assignment repository ----

    /**
     * Test that the connection between the local VC and the local CI system is working.
     * Perform a push to the assignment repository and check that a submission is created and the local CI system successfully builds and tests the source code.
     */
    @Test
    void testPush_assignmentRepository_student() throws Exception {
        String commitHash = localVCLocalCITestService.commitFile(localAssignmentRepositoryFolder, programmingExercise.getPackageFolderName(), localAssignmentGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the assignment repository.
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(mockDockerClient, partlySuccessfulTestResultsPath);

        localVCLocalCITestService.testPushSuccessful(localAssignmentGit, student1Login, projectKey1, assignmentRepositorySlug, commitHash, studentParticipation.getId(), 1);
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
    void testPush_testsRepository_instructor() throws Exception {
        // Students should not be able to fetch and push.
        localVCLocalCITestService.testFetchThrowsException(localTestsGit, student1Login, projectKey1, testsRepositorySlug, notAuthorized);
        localVCLocalCITestService.testPushThrowsException(localTestsGit, student1Login, projectKey1, testsRepositorySlug, notAuthorized);

        // Teaching assistants should be able to fetch but not push.
        localVCLocalCITestService.testFetchSuccessful(localTestsGit, tutor1Login, projectKey1, testsRepositorySlug);
        localVCLocalCITestService.testPushThrowsException(localTestsGit, tutor1Login, projectKey1, testsRepositorySlug, notAuthorized);

        // Instructors should be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(localTestsGit, instructorLogin, projectKey1, testsRepositorySlug);

        String commitHash = localVCLocalCITestService.commitFile(localTestsRepositoryFolder, programmingExercise.getPackageFolderName(), localTestsGit);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the assignment repository.
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash);

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(mockDockerClient, allFailTestResultsPath);

        localVCLocalCITestService.testPushSuccessful(localTestsGit, instructorLogin, projectKey1, testsRepositorySlug, commitHash, studentParticipation.getId(), 1);
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

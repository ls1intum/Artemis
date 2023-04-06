package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * This class contains integration tests for the local VC system that should not touch the local CI system (i.e. either fetch requests or failing push requests).
 */
class LocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Test
    void testFetch_repositoryDoesNotExist() {
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, "SOMENONEXISTENTPROJECTKEY", "some-nonexistent-repository-slug");
        Exception exception = assertThrows(InvalidRemoteException.class, () -> {
            try (Git ignored = Git.cloneRepository().setURI(repositoryUrl).call()) {
                fail("The clone operation should have failed.");
            }
        });
        assertThat(exception.getCause().getMessage()).contains("not found");
    }

    @Test
    void testPush_repositoryDoesNotExist() throws IOException, GitAPIException, URISyntaxException {
        // Create a new repository, delete the remote repository and try to push to the remote repository.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "some-repository-slug";
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, repositorySlug);
        Git remoteGit = localVCLocalCITestService.createGitRepository(remoteRepositoryFolder);
        Path localRepositoryFolder = Files.createTempDirectory("localRepository");
        Git localGit = Git.cloneRepository().setURI(remoteRepositoryFolder.toString()).setDirectory(localRepositoryFolder.toFile()).call();

        // Delete the remote repository.
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());

        // Try to push to the remote repository.
        localVCLocalCITestService.testPushThrowsException(localGit, "someUser", projectKey, repositorySlug, TransportException.class, notFound);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
    }

    @Test
    void testFetch_wrongCredentials() {
        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student1Login, "wrong-password", programmingExercise.getProjectKey(), assignmentRepositoryName,
                TransportException.class, notAuthorized);
    }

    @Test
    void testPush_wrongCredentials() {
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, "wrong-password", programmingExercise.getProjectKey(), assignmentRepositoryName,
                TransportException.class, notAuthorized);
    }

    @Test
    void testFetch_incompleteCredentials() {
        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student1Login, "", programmingExercise.getProjectKey(), assignmentRepositoryName,
                TransportException.class, notAuthorized);
    }

    @Test
    void testPush_incompleteCredentials() {
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, "", programmingExercise.getProjectKey(), assignmentRepositoryName,
                TransportException.class, notAuthorized);
    }

    @Test
    void testFetchPush_programmingExerciseDoesNotExist() throws GitAPIException, IOException, URISyntaxException {
        // Create a repository for an exercise that does not exist.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "someprojectkey-some-repository-slug";
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, repositorySlug);
        Git remoteGit = localVCLocalCITestService.createGitRepository(remoteRepositoryFolder);
        Path localRepositoryFolder = Files.createTempDirectory("localRepository");
        Git localGit = Git.cloneRepository().setURI(remoteRepositoryFolder.toString()).setDirectory(localRepositoryFolder.toFile()).call();

        localVCLocalCITestService.testFetchThrowsException(localGit, student1Login, projectKey, repositorySlug, TransportException.class, internalServerError);
        localVCLocalCITestService.testPushThrowsException(localGit, student1Login, projectKey, repositorySlug, TransportException.class, internalServerError);

        // Cleanup
        localGit.close();
        FileUtils.deleteDirectory(localRepositoryFolder.toFile());
        remoteGit.close();
        FileUtils.deleteDirectory(remoteRepositoryFolder.toFile());
    }

    @Test
    void testFetchPush_offlineIDENotAllowed() {
        programmingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchThrowsException(localAssignmentGit, student1Login, programmingExercise.getProjectKey(), assignmentRepositoryName,
                TransportException.class, forbidden);
        localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, programmingExercise.getProjectKey(), assignmentRepositoryName,
                TransportException.class, forbidden);

        programmingExercise.setAllowOfflineIde(true);
        programmingExerciseRepository.save(programmingExercise);
    }

    // ---- Tests for the assignment repository ----

    @Test
    void testFetch_assignmentRepository_student() {
        localVCLocalCITestService.testFetchSuccessful(localAssignmentGit, student1Login, programmingExercise.getProjectKey(), assignmentRepositoryName);
    }

    @Test
    void testFetch_assignmentRepository_student_noParticipation() {

    }

    @Test
    void testPush_assignmentRepository_student_noParticipation() {

    }

    @Test
    void testFetch_assignmentRepository_student_studentDoesNotOwnParticipation() {

    }

    @Test
    void testPush_assignmentRepository_student_studentDoesNotOwnParticipation() {

    }

    @Test
    void testPush_assignmentRepository_student_tooManySubmissions() {
        // LockRepositoryPolicy is enforced
    }

    @Test
    void testFetch_assignmentRepository_student_beforeStartDate() {
        // Should fail
    }

    @Test
    void testPush_assignmentRepository_student_beforeStartDate() {
        // Should fail
    }

    @Test
    void testFetch_assignmentRepository_student_afterDueDate() {
        // Should be successful
    }

    @Test
    void testPush_assignmentRepository_student_afterDueDate() {
        // Should fail
    }

    // -------- practice mode ----

    @Test
    void testFetch_assignmentRepository_student_practiceMode() {
        // Should be successful
    }

    @Test
    void testFetch_assignmentRepository_student_practiceMode_noParticipation() {

    }

    @Test
    void testPush_assignmentRepository_student_practiceMode_noParticipation() {

    }

    @Test
    void testFetch_assignmentRepository_student_practiceMode_studentDoesNotOwnParticipation() {

    }

    @Test
    void testPush_assignmentRepository_student_practiceMode_studentDoesNotOwnParticipation() {

    }

    // -------- team mode ----

    @Test
    void testFetch_assignmentRepository_student_teamMode() {
        // Should be successful
    }

    @Test
    void testFetch_assignmentRepository_student_teamMode_teamDoesNotExist() {

    }

    @Test
    void testPush_assignmentRepository_student_teamMode_teamDoesNotExist() {

    }

    @Test
    void testFetch_assignmentRepository_student_teamMode_noParticipation() {

    }

    @Test
    void testPush_assignmentRepository_student_teamMode_noParticipation() {

    }

    @Test
    void testFetch_assignmentRepository_student_teamMode_studentIsNotPartOfTeam() {

    }

    @Test
    void testPush_assignmentRepository_student_teamMode_studentIsNotPartOfTeam() {

    }

    // -------- exam mode ----

    @Test
    void testFetch_assignmentRepository_student_examMode() {
        // Should be successful
    }

    @Test
    void testFetch_assignmentRepository_student_examMode_beforeStartDate() {
        // Should fail
    }

    @Test
    void testFetch_assignmentRepository_teachingAssistant_examMode_beforeStartDate() {
        // Should be successful
    }

    @Test
    void testPush_assignmentRepository_student_examMode_beforeStartDate() {
        // Should fail
    }

    @Test
    void testFetch_assignmentRepository_student_examMode_afterDueDate() {
        // Should be successful
    }

    @Test
    void testPush_assignmentRepository_student_examMode_afterDueDate() {
        // Should fail
    }

    // ---- Tests for the tests repository ----

    @Test
    void testFetch_testsRepository_student() {
        // The tests repository can only be fetched by users that are at least teaching assistants in the course.
    }

    @Test
    void testPush_testsRepository_student() {
        // The tests repository can only be pushed to by users that are at least teaching assistants in the course.
    }

    @Test
    void testFetch_testsRepository_teachingAssistant() {
        // Should be successful
    }

    @Test
    void testFetch_testsRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testPush_testsRepository_teachingAssistant_noParticipation() {

    }

    // ---- Tests for the solution repository ----

    @Test
    void testFetch_solutionRepository_teachingAssistant() {
        // Should be successful
    }

    @Test
    void testFetch_solutionRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testPush_solutionRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testFetch_solutionRepository_student() {
        // The tests repository can only be fetched by users that are at least teaching assistants in the course.
    }

    @Test
    void testPush_solutionRepository_student() {
        // The tests repository can only be pushed to by users that are at least teaching assistants in the course.
    }

    // ---- Tests for the template repository ----

    @Test
    void testFetch_templateRepository_teachingAssistant() {
        // Should be successful
    }

    @Test
    void testFetch_templateRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testPush_templateRepository_teachingAssistant_noParticipation() {

    }

    @Test
    void testFetch_templateRepository_student() {
        // The tests repository can only be fetched by users that are at least teaching assistants in the course.
    }

    @Test
    void testPush_templateRepository_student() {
        // The tests repository can only be pushed to by users that are at least teaching assistants in the course.
    }
}

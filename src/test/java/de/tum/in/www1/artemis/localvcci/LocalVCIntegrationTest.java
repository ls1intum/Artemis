package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.user.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests for edge cases pertaining to the local VC system.
 */
class LocalVCIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    ProgrammingSubmissionRepository programmingSubmissionRepository;

    private LocalRepository assignmentRepository;

    private LocalRepository templateRepository;

    private LocalRepository solutionRepository;

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException {
        // Create assignment repository
        assignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);

        // Create template repository
        templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, projectKey1.toLowerCase() + "-exercise");

        // Create solution repository
        solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, projectKey1.toLowerCase() + "-solution");
    }

    @AfterEach
    void removeRepositories() throws IOException {
        assignmentRepository.resetLocalRepo();
        templateRepository.resetLocalRepo();
        solutionRepository.resetLocalRepo();
    }

    @Test
    void testFetchPush_repositoryDoesNotExist() throws IOException, GitAPIException, URISyntaxException {
        // Create a new repository, delete the remote repository and try to fetch and push to the remote repository.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "some-repository-slug";
        LocalRepository someRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, repositorySlug);

        // Delete the remote repository.
        someRepository.originGit.close();
        FileUtils.deleteDirectory(someRepository.originRepoFile);

        // Try to fetch from the remote repository.
        localVCLocalCITestService.testFetchThrowsException(someRepository.localGit, student1Login, USER_PASSWORD, projectKey, repositorySlug, InvalidRemoteException.class, "");

        // Try to push to the remote repository.
        localVCLocalCITestService.testPushReturnsError(someRepository.localGit, student1Login, projectKey, repositorySlug, NOT_FOUND);

        // Cleanup
        someRepository.localGit.close();
        FileUtils.deleteDirectory(someRepository.localRepoFile);
    }

    @Test
    void testFetchPush_wrongCredentials() {
        // Try to access with the wrong password.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Try to access without a password.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
    }

    @Test
    void testFetchPush_programmingExerciseDoesNotExist() throws GitAPIException, IOException, URISyntaxException {
        // Create a repository for an exercise that does not exist.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "someprojectkey-some-repository-slug";
        LocalRepository someRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug);

        localVCLocalCITestService.testFetchReturnsError(someRepository.localGit, student1Login, projectKey, repositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(someRepository.localGit, student1Login, projectKey, repositorySlug, INTERNAL_SERVER_ERROR);

        // Cleanup
        someRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFetchPush_offlineIDENotAllowed() {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        programmingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Teaching assistants and higher should still be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void testFetchPush_assignmentRepository_student_noParticipation() throws GitAPIException, IOException, URISyntaxException {
        // Create a new repository, but don't create a participation for student2.
        String repositorySlug = projectKey1.toLowerCase() + "-" + student2Login;
        LocalRepository student2Repository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, repositorySlug);

        localVCLocalCITestService.testFetchReturnsError(student2Repository.localGit, student2Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(student2Repository.localGit, student2Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);

        // Cleanup
        student2Repository.resetLocalRepo();
    }

    @Test
    void testFetchPush_templateRepository_noParticipation() {
        // Remove the template participation from the programming exercise.
        programmingExercise.setTemplateParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        templateProgrammingExerciseParticipationRepository.delete(templateParticipation);

        localVCLocalCITestService.testFetchReturnsError(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testFetchPush_solutionRepository_noParticipation() {
        // Remove the solution participation from the programming exercise.
        programmingExercise.setSolutionParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        solutionProgrammingExerciseParticipationRepository.delete(solutionParticipation);

        localVCLocalCITestService.testFetchReturnsError(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug, INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserTriesToDeleteBranch() throws GitAPIException {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // ":" prefix in the refspec means delete the branch in the remote repository.
        RefSpec refSpec = new RefSpec(":refs/heads/" + defaultBranch);
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);
        PushResult pushResult = assignmentRepository.localGit.push().setRefSpecs(refSpec).setRemote(repositoryUrl).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot delete a branch.");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserTriesToForcePush() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);

        // Create a second local repository, push a file from there, and then try to force push from the original local repository.
        Path tempDirectory = Files.createTempDirectory("tempDirectory");
        Git secondLocalGit = Git.cloneRepository().setURI(repositoryUrl).setDirectory(tempDirectory.toFile()).call();
        localVCLocalCITestService.commitFile(tempDirectory, secondLocalGit);
        localVCLocalCITestService.testPushSuccessful(secondLocalGit, student1Login, projectKey1, assignmentRepositorySlug);

        localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), assignmentRepository.localGit, "second-test.txt");

        // Try to push normally, should fail because the remote already contains work that does not exist locally.
        PushResult pushResultNormal = assignmentRepository.localGit.push().setRemote(repositoryUrl).call().iterator().next();
        RemoteRefUpdate remoteRefUpdateNormal = pushResultNormal.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdateNormal.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD);

        // Force push from the original local repository.
        PushResult pushResultForce = assignmentRepository.localGit.push().setForce(true).setRemote(repositoryUrl).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResultForce.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot force push.");

        // Cleanup
        secondLocalGit.close();
        FileUtils.deleteDirectory(tempDirectory.toFile());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserCreatesNewBranch() throws Exception {
        ProgrammingExerciseStudentParticipation studentParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Users can create new branches, but pushing to them should not result in a new submission. A warning message should be returned.
        assignmentRepository.localGit.branchCreate().setName("new-branch").setStartPoint("refs/heads/" + defaultBranch).call();
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);

        // Push the new branch.
        PushResult pushResult = assignmentRepository.localGit.push().setRemote(repositoryUrl).setRefSpecs(new RefSpec("refs/heads/new-branch:refs/heads/new-branch")).call()
                .iterator().next();
        assertThat(pushResult.getMessages()).contains("Only pushes to the default branch will be graded.");
        Optional<ProgrammingSubmission> submission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(studentParticipation.getId());
        assertThat(submission).isNotPresent();

        // Commit a new file to the new branch and push again.
        assignmentRepository.localGit.checkout().setName("new-branch").call();
        Path testFilePath = assignmentRepository.localRepoFile.toPath().resolve("new-file.txt");
        Files.createFile(testFilePath);
        assignmentRepository.localGit.add().addFilepattern(".").call();
        assignmentRepository.localGit.commit().setMessage("Add new file").call();
        pushResult = assignmentRepository.localGit.push().setRemote(repositoryUrl).call().iterator().next();
        assertThat(pushResult.getMessages()).contains("Only pushes to the default branch will be graded.");
        submission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(studentParticipation.getId());
        assertThat(submission).isNotPresent();
    }
}

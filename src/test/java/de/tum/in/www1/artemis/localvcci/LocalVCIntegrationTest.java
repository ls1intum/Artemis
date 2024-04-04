package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.user.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

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

import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests for edge cases pertaining to the local VC system.
 */
class LocalVCIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

    @Autowired
    ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    private LocalRepository assignmentRepository;

    private LocalRepository templateRepository;

    private LocalRepository solutionRepository;

    private LocalRepository testsRepository;

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException {
        // Create assignment repository
        assignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);

        // Create template repository
        templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, projectKey1.toLowerCase() + "-exercise");

        // Create solution repository
        solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, projectKey1.toLowerCase() + "-solution");

        // Create tests repository
        testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, projectKey1.toLowerCase() + "-tests");
    }

    @AfterEach
    void removeRepositories() throws IOException {
        assignmentRepository.resetLocalRepo();
        templateRepository.resetLocalRepo();
        solutionRepository.resetLocalRepo();
        testsRepository.resetLocalRepo();
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
        someRepository.resetLocalRepo();
    }

    @Test
    void testFetchPush_wrongCredentials() throws InvalidNameException {
        var student1 = new LdapUserDto().username(TEST_PREFIX + "student1");
        student1.setUid(new LdapName("cn=student1,ou=test,o=lab"));

        var fakeUser = new LdapUserDto().username(localVCBaseUsername);
        fakeUser.setUid(new LdapName("cn=" + localVCBaseUsername + ",ou=test,o=lab"));

        doReturn(Optional.of(student1)).when(ldapUserService).findByUsername(student1.getUsername());
        doReturn(Optional.of(fakeUser)).when(ldapUserService).findByUsername(localVCBaseUsername);

        doReturn(false).when(ldapTemplate).compare(anyString(), anyString(), any());

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
        String repositoryUri = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);
        PushResult pushResult = assignmentRepository.localGit.push().setRefSpecs(refSpec).setRemote(repositoryUri).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot delete a branch.");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStudentTriesToForcePush() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String repositoryUri = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);

        RemoteRefUpdate remoteRefUpdate = setupAndTryForcePush(assignmentRepository, repositoryUri, student1Login, projectKey1, assignmentRepositorySlug);

        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot force push.");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorTriesToForcePush() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        String assignmentRepoUri = localVCLocalCITestService.constructLocalVCUrl(instructor1Login, projectKey1, assignmentRepositorySlug);
        String templateRepoUri = localVCLocalCITestService.constructLocalVCUrl(instructor1Login, projectKey1, templateRepositorySlug);
        String solutionRepoUri = localVCLocalCITestService.constructLocalVCUrl(instructor1Login, projectKey1, solutionRepositorySlug);
        String testsRepoUri = localVCLocalCITestService.constructLocalVCUrl(instructor1Login, projectKey1, testsRepositorySlug);

        // Force push to assignment repository (should not be possible)
        RemoteRefUpdate remoteRefUpdate = setupAndTryForcePush(assignmentRepository, assignmentRepoUri, instructor1Login, projectKey1, assignmentRepositorySlug);
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot force push.");

        // Force push to template repository (should be possible)
        remoteRefUpdate = setupAndTryForcePush(templateRepository, templateRepoUri, instructor1Login, projectKey1, templateRepositorySlug);
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);

        // Force push to solution repository (should be possible)
        remoteRefUpdate = setupAndTryForcePush(solutionRepository, solutionRepoUri, instructor1Login, projectKey1, solutionRepositorySlug);
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);

        // Force push to rests repository (should be possible)
        remoteRefUpdate = setupAndTryForcePush(testsRepository, testsRepoUri, instructor1Login, projectKey1, testsRepositorySlug);
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);
    }

    private RemoteRefUpdate setupAndTryForcePush(LocalRepository originalRepository, String repositoryUri, String login, String projectKey, String repositorySlug)
            throws Exception {

        // Create a second local repository and push a file from there
        Path tempDirectory = Files.createTempDirectory("tempDirectory");
        Git secondLocalGit = Git.cloneRepository().setURI(repositoryUri).setDirectory(tempDirectory.toFile()).call();
        localVCLocalCITestService.commitFile(tempDirectory, secondLocalGit);
        localVCLocalCITestService.testPushSuccessful(secondLocalGit, login, projectKey, repositorySlug);

        // Commit a file to the original local repository
        localVCLocalCITestService.commitFile(originalRepository.localRepoFile.toPath(), originalRepository.localGit, "second-test.txt");

        // Try to push normally, should fail because the remote already contains work that does not exist locally
        PushResult pushResultNormal = originalRepository.localGit.push().setRemote(repositoryUri).call().iterator().next();
        RemoteRefUpdate remoteRefUpdateNormal = pushResultNormal.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdateNormal.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD);

        // Force push from the original local repository
        PushResult pushResultForce = originalRepository.localGit.push().setForce(true).setRemote(repositoryUri).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResultForce.getRemoteUpdates().iterator().next();

        // Cleanup
        secondLocalGit.close();
        FileUtils.deleteDirectory(tempDirectory.toFile());

        return remoteRefUpdate;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserCreatesNewBranch() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Users cannot create new branches.
        assignmentRepository.localGit.branchCreate().setName("new-branch").setStartPoint("refs/heads/" + defaultBranch).call();
        String repositoryUri = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);

        // Push the new branch.
        PushResult pushResult = assignmentRepository.localGit.push().setRemote(repositoryUri).setRefSpecs(new RefSpec("refs/heads/new-branch:refs/heads/new-branch")).call()
                .iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot push to a branch other than the default branch.");
    }

    @Test
    void testRepositoryFolderName() {

        // we specifically choose logins containing "git" to test it does not accidentally get replaced
        String login1 = "ab123git";
        String login2 = "git123ab";

        LocalVCRepositoryUri studentAssignmentRepositoryUri1 = new LocalVCRepositoryUri(projectKey1, projectKey1.toLowerCase() + "-" + login1, localVCBaseUrl);
        LocalVCRepositoryUri studentAssignmentRepositoryUri2 = new LocalVCRepositoryUri(projectKey1, projectKey1.toLowerCase() + "-" + login2, localVCBaseUrl);

        // assert that the URIs are correct
        assertThat(studentAssignmentRepositoryUri1.getURI().toString()).isEqualTo(localVCBaseUrl + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-" + login1 + ".git");
        assertThat(studentAssignmentRepositoryUri2.getURI().toString()).isEqualTo(localVCBaseUrl + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-" + login2 + ".git");

        // assert that the folder names are correct
        assertThat(studentAssignmentRepositoryUri1.folderNameForRepositoryUri()).isEqualTo("/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-" + login1);
        assertThat(studentAssignmentRepositoryUri2.folderNameForRepositoryUri()).isEqualTo("/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-" + login2);
    }
}

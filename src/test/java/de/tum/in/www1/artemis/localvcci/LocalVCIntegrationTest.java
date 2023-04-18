package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
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

import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
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
        Path remoteAssignmentRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, assignmentRepositorySlug);
        assignmentRepository = new LocalRepository(defaultBranch);
        assignmentRepository.configureRepos("localAssignment", remoteAssignmentRepositoryFolder);

        // Create template repository
        Path remoteTemplateRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, (projectKey1 + "-exercise").toLowerCase());
        templateRepository = new LocalRepository(defaultBranch);
        templateRepository.configureRepos("localTemplate", remoteTemplateRepositoryFolder);

        // Create solution repository
        Path remoteSolutionRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, (projectKey1 + "-solution").toLowerCase());
        solutionRepository = new LocalRepository(defaultBranch);
        solutionRepository.configureRepos("localSolution", remoteSolutionRepositoryFolder);
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
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository someRepository = new LocalRepository(defaultBranch);
        someRepository.configureRepos("localRepository", remoteRepositoryFolder);

        // Delete the remote repository.
        someRepository.originGit.close();
        FileUtils.deleteDirectory(someRepository.originRepoFile);

        // Try to fetch from the remote repository.
        localVCLocalCITestService.testFetchThrowsException(someRepository.localGit, student1Login, USER_PASSWORD, projectKey, repositorySlug, InvalidRemoteException.class, "");

        // Try to push to the remote repository.
        localVCLocalCITestService.testPushThrowsException(someRepository.localGit, student1Login, projectKey, repositorySlug, NOT_FOUND);

        // Cleanup
        someRepository.localGit.close();
        FileUtils.deleteDirectory(someRepository.localRepoFile);
    }

    @Test
    void testFetchPush_wrongCredentials() {
        // Try to access with the wrong password.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Try to access without a password.
        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
    }

    @Test
    void testFetchPush_programmingExerciseDoesNotExist() throws GitAPIException, IOException, URISyntaxException {
        // Create a repository for an exercise that does not exist.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "someprojectkey-some-repository-slug";
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, repositorySlug);
        LocalRepository someRepository = new LocalRepository(defaultBranch);
        someRepository.configureRepos("localRepository", remoteRepositoryFolder);

        localVCLocalCITestService.testFetchThrowsException(someRepository.localGit, student1Login, projectKey, repositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushThrowsException(someRepository.localGit, student1Login, projectKey, repositorySlug, INTERNAL_SERVER_ERROR);

        // Cleanup
        someRepository.resetLocalRepo();
    }

    @Test
    void testFetchPush_offlineIDENotAllowed() {
        programmingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushThrowsException(assignmentRepository.localGit, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Teaching assistants and higher should still be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.localGit, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.localGit, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void testFetchPush_assignmentRepository_student_noParticipation() throws GitAPIException, IOException, URISyntaxException {
        // Create a new repository, but don't create a participation for student2.
        String repositorySlug = projectKey1.toLowerCase() + "-" + student2Login;
        Path remoteRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, repositorySlug);
        LocalRepository student2Repository = new LocalRepository(defaultBranch);
        student2Repository.configureRepos("localRepository", remoteRepositoryFolder);

        localVCLocalCITestService.testFetchThrowsException(student2Repository.localGit, student2Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushThrowsException(student2Repository.localGit, student2Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);

        // Cleanup
        student2Repository.resetLocalRepo();
    }

    @Test
    void testFetchPush_templateRepository_noParticipation() {
        // Remove the template participation from the programming exercise.
        programmingExercise.setTemplateParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        templateProgrammingExerciseParticipationRepository.delete(templateParticipation);

        localVCLocalCITestService.testFetchThrowsException(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushThrowsException(templateRepository.localGit, instructor1Login, projectKey1, templateRepositorySlug, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testFetchPush_solutionRepository_noParticipation() {
        // Remove the solution participation from the programming exercise.
        programmingExercise.setSolutionParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        solutionProgrammingExerciseParticipationRepository.delete(solutionParticipation);

        localVCLocalCITestService.testFetchThrowsException(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushThrowsException(solutionRepository.localGit, instructor1Login, projectKey1, solutionRepositorySlug, INTERNAL_SERVER_ERROR);
    }

    @Test
    void testUserTriesToDeleteBranch() throws GitAPIException {
        // ":" prefix in the refspec means delete the branch in the remote repository.
        RefSpec refSpec = new RefSpec(":refs/heads/" + defaultBranch);
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);
        PushResult pushResult = assignmentRepository.localGit.push().setRefSpecs(refSpec).setRemote(repositoryUrl).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot delete a branch.");
    }

    @Test
    void testUserTriesToForcePush() throws Exception {
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);

        // Create a second local repository, push a file from there, and then try to force push from the original local repository.
        Path tempDirectory = Files.createTempDirectory("tempDirectory");
        Git secondLocalGit = Git.cloneRepository().setURI(repositoryUrl).setDirectory(tempDirectory.toFile()).call();
        localVCLocalCITestService.commitFile(tempDirectory, programmingExercise.getPackageFolderName(), secondLocalGit);
        localVCLocalCITestService.testPushSuccessful(secondLocalGit, student1Login, projectKey1, assignmentRepositorySlug);

        localVCLocalCITestService.commitFile(assignmentRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(), assignmentRepository.localGit,
                "second-test.txt");

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
    void testUserCreatesNewBranch() throws GitAPIException {
        // Users can create new branches, but pushing them should not result in a new submission.
        assignmentRepository.localGit.branchCreate().setName("new-branch").setStartPoint("refs/heads/" + defaultBranch).call();
        String repositoryUrl = localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositorySlug);

        assignmentRepository.localGit.push().setRemote(repositoryUrl).setRefSpecs(new RefSpec("refs/heads/new-branch:refs/heads/new-branch")).call().iterator().next();
        Optional<ProgrammingSubmission> submission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(studentParticipation.getId());
        assertThat(submission).isNotPresent();
    }
}

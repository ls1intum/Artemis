package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIPushService;
import de.tum.in.www1.artemis.util.LocalRepository;

class LocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private LocalCIPushService localCIPushService;

    private LocalRepository studentAssignmentRepository;

    private String commitHash;

    @BeforeEach
    void initRepositories() throws Exception {
        Path localRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, assignmentRepositorySlug);
        studentAssignmentRepository = new LocalRepository(defaultBranch);
        studentAssignmentRepository.configureRepos("localRepo", localRepositoryFolder);
        commitHash = localVCLocalCITestService.commitFile(studentAssignmentRepository.localRepoFile.toPath(), programmingExercise.getPackageFolderName(),
                studentAssignmentRepository.localGit);
        studentAssignmentRepository.localGit.push().call();
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the student repository.
        localVCLocalCITestService.mockCommitHash(mockDockerClient, commitHash);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        localVCLocalCITestService.mockTestResults(mockDockerClient, partlySuccessfulTestResultsPath);
        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", dummyCommitHash), Map.of("testCommitHash", dummyCommitHash));
    }

    @AfterEach
    void removeRepositories() {
        localVCLocalCITestService.removeRepository(studentAssignmentRepository);
    }

    @Test
    void testCommitHashNull() {
        localCIPushService.processNewPush(null, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLastestSubmission(studentParticipation.getId(), commitHash, 1);
    }

    @Test
    void testInvalidLocalVCRepositoryUrl() {
        // The local repository cannot be resolved to a valid LocalVCRepositoryUrl as it is not located at the correct base path and is not a bare repository.
        LocalCIException exception = assertThrows(LocalCIException.class,
                () -> localCIPushService.processNewPush(commitHash, studentAssignmentRepository.localGit.getRepository()));
        assertThat(exception.getMessage()).contains("Could not create valid repository URL from path");
    }

    @Test
    void testExceptionWhenResolvingCommit() {
        // Author name, email, and commit message are set to null, if the correct commit can not be resolved using the commit hash. This should not cause problems for the result.

        // Call processNewPush with a wrong commit hash.
        localCIPushService.processNewPush(dummyCommitHash, studentAssignmentRepository.originGit.getRepository());
        localVCLocalCITestService.testLastestSubmission(studentParticipation.getId(), dummyCommitHash, 1);
    }

    @Test
    void testNoParticipation() {
        // Should throw a LocalCIException if there is no paricipation for the exercise and the repositoryTypeOrUserName.

        // solution participation

        // template participation

        // team participation

        // student participation
    }

    @Test
    void testCannotRetrieveBuildScriptPath() {
        // Should throw a LocalCIException
    }

    @Test
    void testProjectTypeIsNull() {

    }

    @Test
    void testImageNotFound() {
        // dockerClient.inspectImageCmd().exec() throws NotFoundException.
    }

    @Test
    void testCannotRetrieveCommitHash() {
        // Should stop the container and return a build result that indicates that the build failed.
    }

    @Test
    void testCannotFindResults() {
        // Should stop the container and return a build result that indicates that the build failed.

    }

    @Test
    void testExceptionWhenParsingTestResults() {

    }

    @Test
    void testBuildFailed() {

    }
}

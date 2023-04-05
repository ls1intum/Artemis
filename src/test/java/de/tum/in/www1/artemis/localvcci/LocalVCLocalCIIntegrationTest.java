package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.dockerjava.api.DockerClient;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.util.GitUtilService;

class LocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private GitUtilService gitUtilService;

    @Autowired
    private DockerClient mockDockerClient;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    /**
     * Test that the connection between the local VC and the local CI system is working.
     * Perform a push to the assignment repository and check that a submission is created and the local CI system successfully builds and tests the source code.
     */
    @Test
    public void testPushAndReceiveResult() throws Exception {
        // Create a file and push the changes to the remote assignment repository.
        Path testJsonFilePath = Path.of(localAssignmentRepositoryFolder.toString(), "src", programmingExercise.getPackageFolderName(), "test.txt");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath);
        localAssignmentGit.add().addFilepattern(".").call();
        RevCommit commit = localAssignmentGit.commit().setMessage("Add test.txt").call();
        String commitHash = commit.getId().getName();

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the assignment repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/assignment-repository/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", commitHash));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", dummyCommitHash));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
        Path failedTestResultsPath = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "partly-successful");
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/build/test-results/test",
                localVCLocalCITestService.createMapFromTestResultsFolder(failedTestResultsPath));

        localAssignmentGit.push()
                .setRemote(localVCLocalCITestService.constructLocalVCUrl(TEST_PREFIX + "student1", port, programmingExercise.getProjectKey(), assignmentRepositoryName)).call();

        // Assert that the latest submission has the correct commit hash and the correct result.
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participation.getId()).orElseThrow();
        assertThat(programmingSubmission.getCommitHash()).isEqualTo(commitHash);
        Result result = programmingSubmission.getLatestResult();
        assertThat(result.getTestCaseCount()).isEqualTo(13);
        assertThat(result.getPassedTestCaseCount()).isEqualTo(1);
    }
}

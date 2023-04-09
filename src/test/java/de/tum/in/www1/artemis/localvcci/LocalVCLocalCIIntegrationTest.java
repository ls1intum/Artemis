package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.dockerjava.api.DockerClient;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains integration tests for the local VC system that should go through successfully to the local CI system.
 */
class LocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private GitUtilService gitUtilService;

    @Autowired
    private DockerClient mockDockerClient;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    // ---- Repository handles ----
    protected Git remoteTemplateGit;

    protected Path remoteTemplateRepositoryFolder;

    protected Path remoteTestsRepositoryFolder;

    protected Git remoteTestsGit;

    protected Path remoteAssignmentRepositoryFolder;

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

        final String testsRepoName = projectKey1.toLowerCase() + "-tests";
        remoteTestsRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, testsRepoName);
        LocalRepository testsRepository = new LocalRepository(defaultBranch);
        testsRepository.configureRepos("localTests", remoteTestsRepositoryFolder);
        remoteTestsGit = testsRepository.originGit;

        // Create remote assignment repository
        remoteAssignmentRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey1, assignmentRepositoryName);
        LocalRepository assignmentRepository = new LocalRepository(defaultBranch);
        assignmentRepository.configureRepos("localAssignment", remoteAssignmentRepositoryFolder);
        remoteAssignmentGit = assignmentRepository.originGit;
        localAssignmentRepositoryFolder = assignmentRepository.localRepoFile.toPath();
        localAssignmentGit = assignmentRepository.localGit;
    }

    @AfterEach
    void removeRepositories() throws IOException {
        if (remoteTemplateGit != null) {
            remoteTemplateGit.close();
        }
        if (remoteTestsGit != null) {
            remoteTestsGit.close();
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

        localAssignmentGit.push().setRemote(localVCLocalCITestService.constructLocalVCUrl(student1Login, projectKey1, assignmentRepositoryName)).call();

        // Assert that the latest submission has the correct commit hash and the correct result.
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(studentParticipation.getId())
                .orElseThrow();
        assertThat(programmingSubmission.getCommitHash()).isEqualTo(commitHash);
        Result result = programmingSubmission.getLatestResult();
        assertThat(result.getTestCaseCount()).isEqualTo(13);
        assertThat(result.getPassedTestCaseCount()).isEqualTo(1);
    }

    @Test
    void testPush_assignmentRepository_teachingAssistant() {
        // Teaching assistants and up should be able to push to the student's assignment repository.
    }

    @Test
    void testPush_assignmentRepository_student_tooManySubmissions() {
        // LockRepositoryPolicy is enforced
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setSubmissionLimit(1);
        lockRepositoryPolicy.setActive(true);
        database.addSubmissionPolicyToExercise(lockRepositoryPolicy, programmingExercise);

        // Push once successfully.
        // localVCLocalCITestService.testPush
        // Second push should fail.
        // localVCLocalCITestService.testPushThrowsException(localAssignmentGit, student1Login, projectKey1, assignmentRepositoryName, TransportException.class, forbidden);
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
    void testPush_testsRepository_teachingAssistant() {

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

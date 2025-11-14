package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.util.TestFileUtil;

class ProgrammingExerciseGitIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexgitintegration";

    private Path localRepoPath;

    private Git localGit;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        localRepoPath = Files.createTempDirectory(tempPath, "repo");
        localGit = LocalRepository.initialize(localRepoPath, defaultBranch, false);

        // create commits
        // the following 2 lines prepare the generation of the structural test oracle
        var testJsonFilePath = localRepoPath.resolve("test").resolve(programmingExercise.getPackageFolderName()).resolve("test.json");
        TestFileUtil.writeEmptyJsonFileToPath(testJsonFilePath);
        GitService.commit(localGit).setMessage("add test.json").setAuthor("test", "test@test.com").call();
        var testJsonFilePath2 = localRepoPath.resolve("test").resolve(programmingExercise.getPackageFolderName()).resolve("test2.json");
        TestFileUtil.writeEmptyJsonFileToPath(testJsonFilePath2);
        GitService.commit(localGit).setMessage("add test2.json").setAuthor("test", "test@test.com").call();
        var testJsonFilePath3 = localRepoPath.resolve("test").resolve(programmingExercise.getPackageFolderName()).resolve("test3.json");
        TestFileUtil.writeEmptyJsonFileToPath(testJsonFilePath3);
        GitService.commit(localGit).setMessage("add test3.json").setAuthor("test", "test@test.com").call();

        // No Mockito stubs; subsequent test uses real LocalVC-backed GitService interactions.
    }

    @AfterEach
    void tearDown() throws IOException {
        if (localGit != null) {
            localGit.close();
        }
        if (localRepoPath != null && localRepoPath.toFile().exists()) {
            RepositoryExportTestUtil.safeDeleteDirectory(localRepoPath);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3")
    void testRepositoryMethods() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> programmingExerciseRepository.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> programmingExerciseRepository.findByIdWithAuxiliaryRepositoriesElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> programmingExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> programmingExerciseRepository.findWithTemplateParticipationAndLatestSubmissionByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testGitOperationsWithLocalVC() throws Exception {
        // Create a LocalVC repository (acts as remote) and seed with an initial commit
        var projectKey = "PROGEXGIT";
        var repoSlug = projectKey.toLowerCase() + "-tests";

        LocalRepository remoteRepo = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repoSlug);

        // Write a file and commit on the remote working copy, then push to origin
        var readmePath = remoteRepo.workingCopyGitRepoFile.toPath().resolve("README.md");
        FileUtils.writeStringToFile(readmePath.toFile(), "Initial commit", java.nio.charset.StandardCharsets.UTF_8);
        remoteRepo.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(remoteRepo.workingCopyGitRepo).setMessage("Initial commit").call();
        remoteRepo.workingCopyGitRepo.push().setRemote("origin").call();

        // Build the LocalVC URI and checkout to a separate target path
        LocalVCRepositoryUri repoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repoSlug));
        Path targetPath = tempPath.resolve("lcvc-checkout").resolve("student-checkout");
        var checkedOut = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, targetPath, true, true);

        // Verify we can fetch and read last commit hash from the remote
        gitService.fetchAll(checkedOut);
        var lastHash = gitService.getLastCommitHash(repoUri);
        assertThat(lastHash).as("last commit hash should exist on remote").isNotNull().isInstanceOf(ObjectId.class);

        // Create a local change, commit and push via GitService
        var localFile = targetPath.resolve("hello.txt");
        Files.createDirectories(localFile.getParent());
        FileUtils.writeStringToFile(localFile.toFile(), "hello world", java.nio.charset.StandardCharsets.UTF_8);
        gitService.stageAllChanges(checkedOut);
        gitService.commitAndPush(checkedOut, "Add hello.txt", true, null);

        // Pull and reset operations should not throw
        gitService.pullIgnoreConflicts(checkedOut);
        gitService.resetToOriginHead(checkedOut);
    }
}

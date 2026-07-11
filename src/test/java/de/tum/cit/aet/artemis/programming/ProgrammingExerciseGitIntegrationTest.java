package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.util.TestFileUtil;

class ProgrammingExerciseGitIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "progexgitintegration";

    @Autowired
    private TempFileUtilService tempFileUtilService;

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

        localRepoPath = tempFileUtilService.createTempDirectory("repo");
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
        RepositoryExportTestUtil.cleanupTrackedRepositories();
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

    private void createRemoteWithInitialCommit(String projectKey, String repoSlug) throws Exception {
        LocalRepository remoteRepo = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repoSlug));
        Path readmePath = remoteRepo.workingCopyGitRepoFile.toPath().resolve("README.md");
        FileUtils.writeStringToFile(readmePath.toFile(), "Initial commit", java.nio.charset.StandardCharsets.UTF_8);
        remoteRepo.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(remoteRepo.workingCopyGitRepo).setMessage("Initial commit").call();
        remoteRepo.workingCopyGitRepo.push().setRemote("origin").call();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testGitOperationsWithLocalVC() throws Exception {
        // Create a LocalVC repository (acts as remote) and seed with an initial commit
        var projectKey = "PROGEXGIT";
        var repoSlug = projectKey.toLowerCase() + "-tests";

        createRemoteWithInitialCommit(projectKey, repoSlug);

        // Build the LocalVC URI and checkout to a separate target path
        LocalVCRepositoryUri repoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repoSlug));
        Path targetPath = tempPath.resolve("lcvc-checkout").resolve("student-checkout");
        var checkedOut = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, targetPath, true, true);

        try {
            // Verify we can fetch and read last commit hash from the remote
            gitService.fetchAll(checkedOut);
            var lastHash = gitService.getLastCommitHash(repoUri);
            assertThat(lastHash).as("last commit hash should exist on remote").isNotNull().isNotBlank();

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
        finally {
            // Ensure repository handle is closed and the local clone is deleted even on failures
            if (checkedOut != null) {
                checkedOut.close();
            }
            RepositoryExportTestUtil.safeDeleteDirectory(targetPath);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void pushCommitWithLease_pushesThePreviouslyCreatedCommitWhenTheLeaseMatches() throws Exception {
        String projectKey = "PROGEXGITLEASE";
        String repoSlug = projectKey.toLowerCase() + "-tests";
        createRemoteWithInitialCommit(projectKey, repoSlug);

        LocalVCRepositoryUri repoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repoSlug));
        Path targetPath = tempPath.resolve("lease-checkout");
        var checkedOut = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, targetPath, true, true);
        try {
            String preHead = gitService.getLocalHeadHash(checkedOut);
            FileUtils.writeStringToFile(targetPath.resolve("generated.txt").toFile(), "generated", StandardCharsets.UTF_8);
            gitService.stageAllChanges(checkedOut);

            String postHead = gitService.commitStagedChanges(checkedOut, "Generated exercise", null);
            assertThat(gitService.getLastCommitHash(repoUri)).isEqualTo(preHead);
            gitService.pushCommitWithLease(checkedOut, postHead, defaultBranch, preHead);

            assertThat(postHead).isNotEqualTo(preHead).isEqualTo(gitService.getLocalHeadHash(checkedOut)).isEqualTo(gitService.getLastCommitHash(repoUri));
        }
        finally {
            checkedOut.close();
            RepositoryExportTestUtil.safeDeleteDirectory(targetPath);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void pushCommitWithLease_preservesAConcurrentCommit() throws Exception {
        String projectKey = "PROGEXGITLEASEFAIL";
        String repoSlug = projectKey.toLowerCase() + "-tests";
        createRemoteWithInitialCommit(projectKey, repoSlug);

        LocalVCRepositoryUri repoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repoSlug));
        Path targetPath = tempPath.resolve("rejected-lease-checkout");
        Path concurrentPath = tempPath.resolve("concurrent-lease-checkout");
        var checkedOut = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, targetPath, true, true);
        var concurrent = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, concurrentPath, true, true);
        try {
            String remoteHead = gitService.getLastCommitHash(repoUri);
            FileUtils.writeStringToFile(targetPath.resolve("generated.txt").toFile(), "generated", StandardCharsets.UTF_8);
            gitService.stageAllChanges(checkedOut);
            String postHead = gitService.commitStagedChanges(checkedOut, "Generated exercise", null);

            FileUtils.writeStringToFile(concurrentPath.resolve("instructor.txt").toFile(), "newer instructor edit", StandardCharsets.UTF_8);
            gitService.stageAllChanges(concurrent);
            String concurrentHead = gitService.commitStagedChanges(concurrent, "Instructor edit", null);
            gitService.pushCommitWithLease(concurrent, concurrentHead, defaultBranch, remoteHead);

            assertThatExceptionOfType(TransportException.class).isThrownBy(() -> gitService.pushCommitWithLease(checkedOut, postHead, defaultBranch, remoteHead));
            assertThat(gitService.getLastCommitHash(repoUri)).isEqualTo(concurrentHead);
        }
        finally {
            checkedOut.close();
            concurrent.close();
            RepositoryExportTestUtil.safeDeleteDirectory(targetPath);
            RepositoryExportTestUtil.safeDeleteDirectory(concurrentPath);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void resetToCommitAndForcePush_preservesAConcurrentCommit() throws Exception {
        String projectKey = "PROGEXGITRESETLEASE";
        String repoSlug = projectKey.toLowerCase() + "-tests";
        createRemoteWithInitialCommit(projectKey, repoSlug);

        LocalVCRepositoryUri repoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repoSlug));
        Path recoveryPath = tempPath.resolve("reset-lease-checkout");
        Path concurrentPath = tempPath.resolve("reset-concurrent-checkout");
        var recovery = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, recoveryPath, true, true);
        var concurrent = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, concurrentPath, true, true);
        try {
            String preHead = gitService.getLastCommitHash(repoUri);
            FileUtils.writeStringToFile(recoveryPath.resolve("generated.txt").toFile(), "generated", StandardCharsets.UTF_8);
            gitService.stageAllChanges(recovery);
            String generatedHead = gitService.commitStagedChanges(recovery, "Generated exercise", null);
            gitService.pushCommitWithLease(recovery, generatedHead, defaultBranch, preHead);

            gitService.resetToOriginHead(concurrent);
            FileUtils.writeStringToFile(concurrentPath.resolve("instructor.txt").toFile(), "newer instructor edit", StandardCharsets.UTF_8);
            gitService.stageAllChanges(concurrent);
            String concurrentHead = gitService.commitStagedChanges(concurrent, "Instructor edit", null);
            gitService.pushCommitWithLease(concurrent, concurrentHead, defaultBranch, generatedHead);

            assertThatExceptionOfType(TransportException.class).isThrownBy(() -> gitService.resetToCommitAndForcePush(recovery, preHead, generatedHead, defaultBranch));
            assertThat(gitService.getLastCommitHash(repoUri)).isEqualTo(concurrentHead);
        }
        finally {
            recovery.close();
            concurrent.close();
            RepositoryExportTestUtil.safeDeleteDirectory(recoveryPath);
            RepositoryExportTestUtil.safeDeleteDirectory(concurrentPath);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void commitToIsolatedBranchAndPush_reportsARejectedUpdate() throws Exception {
        String projectKey = "PROGEXGITDRAFT";
        String repoSlug = projectKey.toLowerCase() + "-tests";
        String draftBranch = "hyperion-draft/job-1";
        createRemoteWithInitialCommit(projectKey, repoSlug);

        LocalVCRepositoryUri repoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repoSlug));
        Path firstPath = tempPath.resolve("first-draft-checkout");
        Path competingPath = tempPath.resolve("competing-draft-checkout");
        var first = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, firstPath, true, true);
        var competing = gitService.getOrCheckoutRepositoryWithTargetPath(repoUri, competingPath, true, true);
        try {
            FileUtils.writeStringToFile(first.getLocalPath().resolve("first.txt").toFile(), "first draft", StandardCharsets.UTF_8);
            gitService.stageAllChanges(first);
            String firstDraftHead = gitService.commitToIsolatedBranchAndPush(first, draftBranch, "First draft", null);

            FileUtils.writeStringToFile(competing.getLocalPath().resolve("competing.txt").toFile(), "competing draft", StandardCharsets.UTF_8);
            gitService.stageAllChanges(competing);
            assertThatExceptionOfType(TransportException.class).isThrownBy(() -> gitService.commitToIsolatedBranchAndPush(competing, draftBranch, "Competing draft", null));

            gitService.fetchAll(first);
            assertThat(first.resolve("refs/remotes/origin/" + draftBranch).getName()).isEqualTo(firstDraftHead);
        }
        finally {
            first.close();
            competing.close();
            RepositoryExportTestUtil.safeDeleteDirectory(firstPath);
            RepositoryExportTestUtil.safeDeleteDirectory(competingPath);
        }
    }
}

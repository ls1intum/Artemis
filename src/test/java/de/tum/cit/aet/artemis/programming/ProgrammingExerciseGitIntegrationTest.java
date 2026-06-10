package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.exception.GitException;
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testGitOperationsWithLocalVC() throws Exception {
        // Create a LocalVC repository (acts as remote) and seed with an initial commit
        var projectKey = "PROGEXGIT";
        var repoSlug = projectKey.toLowerCase() + "-tests";

        LocalRepository remoteRepo = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repoSlug));

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
    void testFailedPullClosesRepositoryBeforeCleanupAndRecovers() throws Exception {
        var projectKey = "PROGEXGITPULL";
        var repoSlug = projectKey.toLowerCase() + "-tests";

        LocalRepository remoteRepo = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repoSlug));

        var readmePath = remoteRepo.workingCopyGitRepoFile.toPath().resolve("README.md");
        FileUtils.writeStringToFile(readmePath.toFile(), "Initial commit", StandardCharsets.UTF_8);
        remoteRepo.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(remoteRepo.workingCopyGitRepo).setMessage("Initial commit").call();
        remoteRepo.workingCopyGitRepo.push().setRemote("origin").call();

        LocalVCRepositoryUri repoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repoSlug));
        Path targetPath = tempPath.resolve("lcvc-failed-pull").resolve("student-checkout");
        Path localPath = gitService.getLocalPathOfRepo(targetPath, repoUri);

        // Check the repository out once so a working copy exists, then release the handle
        var checkedOut = gitService.getOrCheckoutRepositoryWithLocalPath(repoUri, localPath, true, true);
        checkedOut.close();

        // Add a new commit on the remote so the next pull has to update the working copy
        FileUtils.writeStringToFile(readmePath.toFile(), "Updated content", StandardCharsets.UTF_8);
        remoteRepo.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(remoteRepo.workingCopyGitRepo).setMessage("Update README").call();
        remoteRepo.workingCopyGitRepo.push().setRemote("origin").call();

        // A leftover index.lock makes the merge step of the pull fail with a JGitInternalException (LockFailedException)
        Files.createFile(localPath.resolve(".git").resolve("index.lock"));

        // Wrap the repository handle the production code opens in a spy so we can verify it gets closed before the cleanup
        AtomicReference<Repository> openedRepository = new AtomicReference<>();
        doAnswer(invocation -> {
            Repository repository = (Repository) invocation.callRealMethod();
            if (repository == null) {
                return null;
            }
            Repository repositorySpy = spy(repository);
            openedRepository.set(repositorySpy);
            return repositorySpy;
        }).when(gitServiceSpy).getExistingCheckedOutRepositoryByLocalPath(any(Path.class), any(), anyString(), anyBoolean());

        assertThatExceptionOfType(GitException.class).isThrownBy(() -> gitService.getOrCheckoutRepositoryWithLocalPath(repoUri, localPath, true, true));

        assertThat(openedRepository.get()).as("the working copy should have been opened before the pull").isNotNull();
        // Open file handles prevent the directory deletion on network file systems, so the handle must be released first
        verify(openedRepository.get()).closeBeforeDelete();
        assertThat(localPath).as("the corrupt working copy should have been cleaned up").doesNotExist();

        // The next access must recover on its own by cloning a fresh working copy
        var recovered = gitService.getOrCheckoutRepositoryWithLocalPath(repoUri, localPath, true, true);
        try {
            assertThat(recovered).isNotNull();
            assertThat(localPath.resolve("README.md")).exists();
        }
        finally {
            if (recovered != null) {
                recovered.close();
            }
            RepositoryExportTestUtil.safeDeleteDirectory(targetPath);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void testFailedCloneOfMissingRepositoryDoesNotLogSpuriousDeletionError() {
        var projectKey = "PROGEXGITCLONE";
        var repoSlug = projectKey.toLowerCase() + "-doesnotexist";

        LocalVCRepositoryUri repoUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repoSlug));
        Path targetPath = tempPath.resolve("lcvc-failed-clone").resolve("missing-checkout");

        Logger gitServiceLogger = (Logger) LoggerFactory.getLogger(GitService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        gitServiceLogger.addAppender(listAppender);
        try {
            assertThatExceptionOfType(GitException.class).isThrownBy(() -> gitService.getOrCheckoutRepositoryWithLocalPath(repoUri, targetPath, true, true));

            // JGit's CloneCommand cleans up its own directory on failure; there is nothing left to delete, so no error must be logged
            assertThat(listAppender.list)
                    .noneMatch(event -> event.getLevel() == Level.ERROR && event.getFormattedMessage().startsWith("Could not delete directory after failed clone"));
        }
        finally {
            gitServiceLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }
}

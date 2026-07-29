package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.localvc.service.GitService;

/**
 * Verifies that a {@code waitForBareRepositoryToContainCommit} timeout reports enough context to be diagnosed.
 * <p>
 * The wait previously swallowed every poll failure at debug level, so a timeout surfaced only as Awaitility's
 * "condition was not fulfilled within 60 seconds". That made the recurring flake in
 * {@code ProgrammingExerciseLocalVCIntegrationTest#testGetParticipationFilesWithContentAtCommitShouldRedirect}
 * impossible to diagnose from CI logs alone: a genuinely absent commit and a bare repository that could not be opened
 * produced the same message.
 */
class RepositoryExportTestUtilDiagnosticsTest {

    private static final String ABSENT_COMMIT = "0".repeat(40);

    @TempDir
    Path tempDir;

    @Test
    void timeoutOnAnEmptyBareRepository_reportsTheRefsItActuallyHas() throws Exception {
        Path bareDir = tempDir.resolve("empty.git");
        try (Git created = Git.init().setBare(true).setDirectory(bareDir.toFile()).call()) {
            assertThat(created.getRepository().getDirectory()).exists();
        }

        LocalRepository repo = new LocalRepository("main");
        repo.remoteBareGitRepoFile = bareDir.toFile();

        assertThatThrownBy(() -> RepositoryExportTestUtil.waitForBareRepositoryToContainCommit(repo, ABSENT_COMMIT, Duration.ofMillis(300))).isInstanceOf(AssertionError.class)
                // names the commit and the repository, so the failure identifies which wait timed out
                .hasMessageContaining(ABSENT_COMMIT).hasMessageContaining("empty.git")
                // an empty bare repo opens cleanly, so the polls never threw: the commit really was absent
                .hasMessageContaining("Refs present: []").hasMessageContaining("Every poll completed without error");
    }

    @Test
    void timeoutOnAMissingBareRepository_saysTheDirectoryDoesNotExist() {
        LocalRepository repo = new LocalRepository("main");
        repo.remoteBareGitRepoFile = tempDir.resolve("never-created.git").toFile();

        // Distinguishing this from the case above is the whole point: the old message could not tell them apart.
        assertThatThrownBy(() -> RepositoryExportTestUtil.waitForBareRepositoryToContainCommit(repo, ABSENT_COMMIT, Duration.ofMillis(300))).isInstanceOf(AssertionError.class)
                .hasMessageContaining("The bare repository directory does not exist");
    }

    @Test
    void aPresentCommitReturnsWithoutWaiting() throws Exception {
        Path workingDir = tempDir.resolve("work");
        String commitHash;
        try (Git git = Git.init().setDirectory(workingDir.toFile()).setInitialBranch("main").call()) {
            // GitService.commit rather than Git.commit: an ArchUnit rule enforces it, so that commit signing is always disabled
            commitHash = GitService.commit(git).setMessage("initial").setAllowEmpty(true).call().getId().getName();
        }

        LocalRepository repo = new LocalRepository("main");
        // a non-bare repository is fine here: the wait only inspects the object database
        repo.remoteBareGitRepoFile = workingDir.resolve(".git").toFile();

        RepositoryExportTestUtil.waitForBareRepositoryToContainCommit(repo, commitHash, Duration.ofSeconds(5));
    }
}

package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.localvc.service.GitService;

/**
 * Tests for {@link LocalRepository}, in particular that configuring a working copy against an origin repository that already exists continues the existing
 * history instead of starting an unrelated one.
 */
class LocalRepositoryTest {

    private static final String DEFAULT_BRANCH = "main";

    @TempDir
    Path repositoryBasePath;

    private LocalRepository firstRepository;

    private LocalRepository secondRepository;

    @AfterEach
    void closeRepositories() {
        closeQuietly(firstRepository);
        closeQuietly(secondRepository);
    }

    /**
     * The LocalVC bare repository of a participation is created as soon as the participation is created (see
     * {@code ParticipationUtilService#ensureLocalVcRepositoryExists}). Test helpers that seed content into that repository afterwards configure a second working
     * copy for the very same bare repository. That working copy has to continue the existing history: a fresh unrelated root commit can only be pushed as a
     * non-fast-forward, which JGit reports in the push result instead of throwing, so the working copy would silently diverge from the bare repository.
     */
    @Test
    void shouldContinueHistoryWhenOriginRepositoryAlreadyExists() throws Exception {
        Path originRepositoryFolder = repositoryBasePath.resolve("TESTPROJECT").resolve("testproject-student1.git");

        firstRepository = new LocalRepository(DEFAULT_BRANCH);
        firstRepository.configureRepos(repositoryBasePath, "localRepo", originRepositoryFolder);
        // Mirrors LocalVCLocalCITestService#createAndConfigureLocalRepository, which pushes another commit on top of the initial one
        GitService.commit(firstRepository.workingCopyGitRepo).setMessage("Initial commit").setAllowEmpty(true).call();
        firstRepository.workingCopyGitRepo.push().call();

        secondRepository = new LocalRepository(DEFAULT_BRANCH);
        secondRepository.configureRepos(repositoryBasePath, "localRepo", originRepositoryFolder);

        assertThat(secondRepository.getAllLocalCommits()).as("the second working copy should start from the existing history")
                .containsExactlyElementsOf(firstRepository.getAllOriginCommits());

        Path seededFile = secondRepository.workingCopyGitRepoFile.toPath().resolve("Seed.java");
        FileUtils.writeStringToFile(seededFile.toFile(), "class Seed {}", StandardCharsets.UTF_8);
        secondRepository.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(secondRepository.workingCopyGitRepo).setMessage("seed").call();

        var pushResults = secondRepository.workingCopyGitRepo.push().setRemote("origin").call();

        assertThat(pushResults).isNotEmpty().allSatisfy(
                pushResult -> assertThat(pushResult.getRemoteUpdates()).isNotEmpty().allSatisfy(update -> assertThat(update.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK)));
    }

    @Test
    void shouldCreateInitialHistoryWhenOriginRepositoryDoesNotExist() throws Exception {
        Path originRepositoryFolder = repositoryBasePath.resolve("TESTPROJECT").resolve("testproject-student2.git");

        firstRepository = new LocalRepository(DEFAULT_BRANCH);
        firstRepository.configureRepos(repositoryBasePath, "localRepo", originRepositoryFolder);

        assertThat(firstRepository.getAllOriginCommits()).as("the bare repository should have the default branch with an initial commit").hasSize(1);
        assertThat(firstRepository.getAllLocalCommits()).containsExactlyElementsOf(firstRepository.getAllOriginCommits());
    }

    private static void closeQuietly(LocalRepository repository) {
        if (repository == null) {
            return;
        }
        if (repository.workingCopyGitRepo != null) {
            repository.workingCopyGitRepo.close();
        }
        if (repository.remoteBareGitRepo != null) {
            repository.remoteBareGitRepo.close();
        }
    }
}

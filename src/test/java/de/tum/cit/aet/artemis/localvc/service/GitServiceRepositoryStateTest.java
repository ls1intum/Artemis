package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit tests for the repository state inspection in {@link GitService}.
 * <p>
 * {@link GitService#isBareRepositoryHealthy} decides whether a repository on disk can be served or has to be repaired, so it has to tell three states apart: a usable
 * repository, an "unborn" one that was created but never received a branch, and a directory that is not a git repository at all. {@link GitService#setRemoteUrl} rewrites the
 * remote of a checked out repository, which has to happen only when the remote actually changed.
 */
class GitServiceRepositoryStateTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    @TempDir
    Path baseDir;

    private GitService gitService;

    @BeforeEach
    void setUp() {
        gitService = new GitService();
        ReflectionTestUtils.setField(gitService, "localVCBasePath", baseDir);
    }

    private LocalVCRepositoryUri uriFor(String repositorySlug) {
        return new LocalVCRepositoryUri(BASE_URI, "ABC", repositorySlug);
    }

    private Path pathFor(String repositorySlug) {
        return baseDir.resolve("ABC").resolve(repositorySlug + ".git");
    }

    @Test
    void isBareRepositoryHealthy_withABranchAndACommit_isHealthy() throws Exception {
        Path repositoryPath = pathFor("abc-exercise");
        Files.createDirectories(repositoryPath);
        try (Git bare = Git.init().setDirectory(repositoryPath.toFile()).setBare(true).setInitialBranch("main").call()) {
            // A bare repository only gets a branch once something is pushed to it, so push an initial commit from a working copy.
            Path workingCopy = Files.createDirectories(baseDir.resolve("workingcopy"));
            try (Git clone = Git.cloneRepository().setURI(repositoryPath.toUri().toString()).setDirectory(workingCopy.toFile()).call()) {
                Files.writeString(workingCopy.resolve("README.md"), "content");
                clone.add().addFilepattern(".").call();
                GitService.commit(clone).setMessage("Initial commit").call();
                clone.push().call();
            }
        }

        assertThat(gitService.isBareRepositoryHealthy(uriFor("abc-exercise"))).as("a repository with a branch is healthy").isTrue();
    }

    @Test
    void isBareRepositoryHealthy_withoutAnyBranch_isNotHealthy() throws Exception {
        // An unborn repository: created, but nothing was ever pushed, so it has no branch. Serving it fails with "Cannot check out from unborn branch".
        Path repositoryPath = pathFor("abc-unborn");
        Files.createDirectories(repositoryPath);
        Git.init().setDirectory(repositoryPath.toFile()).setBare(true).setInitialBranch("main").call().close();

        assertThat(gitService.isBareRepositoryHealthy(uriFor("abc-unborn"))).as("a repository without any branch is not healthy").isFalse();
    }

    @Test
    void isBareRepositoryHealthy_withADirectoryThatIsNotARepository_isNotHealthy() throws Exception {
        // The skeleton a partially failed deletion leaves behind: directories, but no HEAD and no config.
        Path repositoryPath = pathFor("abc-corrupt");
        Files.createDirectories(repositoryPath.resolve("refs").resolve("heads"));
        Files.createDirectories(repositoryPath.resolve("objects"));

        assertThat(gitService.isBareRepositoryHealthy(uriFor("abc-corrupt"))).as("a directory that is not a git repository is not healthy").isFalse();
    }

    @Test
    void isBareRepositoryHealthy_withAMissingDirectory_isNotHealthy() {
        assertThat(gitService.isBareRepositoryHealthy(uriFor("abc-missing"))).as("a repository that does not exist is not healthy").isFalse();
    }

    @Test
    void isBareRepositoryHealthy_withAFileWhereTheRepositoryShouldBe_isNotHealthy() throws Exception {
        Path repositoryPath = pathFor("abc-file");
        Files.createDirectories(repositoryPath.getParent());
        Files.writeString(repositoryPath, "not a directory");

        // A plain file cannot be opened as a repository, which JGit reports as "not found" rather than as an I/O error, so the health check reports it as unhealthy.
        assertThat(gitService.isBareRepositoryHealthy(uriFor("abc-file"))).as("a file where the repository should be is not healthy").isFalse();
    }

    @Test
    void setRemoteUrl_withAStaleRemote_rewritesItToTheRepositoryOnDisk() throws Exception {
        Path workingCopy = Files.createDirectories(baseDir.resolve("checkout"));
        Git.init().setDirectory(workingCopy.toFile()).setInitialBranch("main").call().close();
        String staleUrl = "https://stale.example.com/git/ABC/abc-exercise.git";

        try (Repository repository = new Repository(workingCopy.resolve(".git").toString(), uriFor("abc-exercise"))) {
            repository.getConfig().setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", "url", staleUrl);

            gitService.setRemoteUrl(repository);

            // LocalVC serves its repositories from disk, so the remote a checkout talks to is the bare repository's path rather than the public https URI.
            String rewritten = repository.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", "url");
            assertThat(rewritten).as("the stale remote is replaced").isNotEqualTo(staleUrl);
            assertThat(rewritten).as("the remote points at the repository on disk").startsWith("file:").contains("/ABC/abc-exercise.git");
        }
    }

    @Test
    void setRemoteUrl_calledTwice_isIdempotent() throws Exception {
        Path workingCopy = Files.createDirectories(baseDir.resolve("checkout"));
        Git.init().setDirectory(workingCopy.toFile()).setInitialBranch("main").call().close();

        try (Repository repository = new Repository(workingCopy.resolve(".git").toString(), uriFor("abc-exercise"))) {
            gitService.setRemoteUrl(repository);
            String afterFirstCall = repository.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", "url");

            gitService.setRemoteUrl(repository);

            assertThat(repository.getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", "url")).as("a remote that already matches is left as it is")
                    .isEqualTo(afterFirstCall);
        }
    }

    @Test
    void setRemoteUrl_withoutARepositoryOrRemote_doesNotThrow() throws Exception {
        assertThatCode(() -> gitService.setRemoteUrl(null)).as("a missing repository is reported, not thrown").doesNotThrowAnyException();

        Path workingCopy = Files.createDirectories(baseDir.resolve("checkout"));
        Git.init().setDirectory(workingCopy.toFile()).setInitialBranch("main").call().close();
        try (Repository withoutRemote = new Repository(workingCopy.resolve(".git").toString(), null)) {
            assertThatCode(() -> gitService.setRemoteUrl(withoutRemote)).as("a repository without a remote is reported, not thrown").doesNotThrowAnyException();
        }
    }
}

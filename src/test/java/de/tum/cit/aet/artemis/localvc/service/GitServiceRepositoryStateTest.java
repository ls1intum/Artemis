package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.exception.GitException;

/**
 * Unit tests for the repository state inspection in {@link GitService}.
 * <p>
 * {@link GitService#isBareRepositoryHealthy} decides whether a repository on disk can be served or has to be repaired, so it has to tell three states apart: a usable
 * repository, an "unborn" one that was created but never received a branch, and a directory that is not a git repository at all. {@link GitService#setRemoteUrl} rewrites the
 * remote of a checked out repository, which has to happen only when the remote actually changed.
 */
class GitServiceRepositoryStateTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    private static final String DEFAULT_BRANCH = "main";

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
        // A bare repository only gets a branch once something is pushed to it.
        checkoutOf("abc-exercise").close();

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
        FileUtils.write(repositoryPath.toFile(), "not a directory", StandardCharsets.UTF_8);

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

    /**
     * Creates a bare repository with one commit on {@value DEFAULT_BRANCH} and returns a working copy of it, so that tests have a checkout with a remote to work against.
     * <p>
     * The commit is pushed from a throwaway clone that is thrown away again before the working copy is cloned. Cloning an empty repository yields a checkout on JGit's own
     * default branch rather than on the branch the bare repository's HEAD names, so pushing from it would leave HEAD dangling: the repository would carry a branch that HEAD
     * does not point at, which no LocalVC repository ever does, and everything that resolves the remote HEAD would silently find nothing.
     */
    private Repository checkoutOf(String repositorySlug) throws Exception {
        Path bare = pathFor(repositorySlug);
        Files.createDirectories(bare);
        Git.init().setDirectory(bare.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call().close();
        Path seed = baseDir.resolve("seed-" + repositorySlug);
        try (Git clone = Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(seed.toFile()).call()) {
            FileUtils.write(seed.resolve("README.md").toFile(), "initial", StandardCharsets.UTF_8);
            clone.add().addFilepattern(".").call();
            GitService.commit(clone).setMessage("Initial commit").call();
            clone.push().setRefSpecs(new RefSpec("HEAD:" + Constants.R_HEADS + DEFAULT_BRANCH)).call();
        }
        FileUtils.deleteDirectory(seed.toFile());

        Path workingCopy = baseDir.resolve("checkout-" + repositorySlug);
        Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(workingCopy.toFile()).call().close();
        return new Repository(workingCopy.resolve(".git").toString(), uriFor(repositorySlug));
    }

    @Test
    void listFilesAndFolders_reportsFilesAndFoldersAndNeverTheGitDirectory() throws Exception {
        try (Repository repository = checkoutOf("abc-listing")) {
            ReflectionTestUtils.setField(repository, "localPath", repository.getWorkTree().toPath());
            Path source = repository.getWorkTree().toPath().resolve("src");
            FileUtils.write(source.resolve("Main.java").toFile(), "class Main {}", StandardCharsets.UTF_8);
            FileUtils.write(repository.getWorkTree().toPath().resolve("image.png").toFile(), "binary", StandardCharsets.UTF_8);

            var listed = gitService.listFilesAndFolders(repository, false);

            var names = listed.keySet().stream().map(java.io.File::getName).toList();
            assertThat(names).as("files and folders of the working tree are reported").contains("README.md", "Main.java", "image.png", "src");
            assertThat(names).as("the git directory is never part of the listing").doesNotContain(".git");
            assertThat(listed.values()).as("both files and folders are classified").contains(FileType.FILE, FileType.FOLDER);
        }
    }

    @Test
    void listFilesAndFolders_withBinariesOmitted_leavesTheBinaryOut() throws Exception {
        try (Repository repository = checkoutOf("abc-binaries")) {
            ReflectionTestUtils.setField(repository, "localPath", repository.getWorkTree().toPath());
            FileUtils.write(repository.getWorkTree().toPath().resolve("image.png").toFile(), "binary", StandardCharsets.UTF_8);

            var listed = gitService.listFilesAndFolders(repository, true);

            var names = listed.keySet().stream().map(java.io.File::getName).toList();
            assertThat(names).as("the binary file is omitted").doesNotContain("image.png");
            assertThat(names).as("text files are still reported").contains("README.md");
        }
    }

    @Test
    void listFilesAndFolders_withoutAWorkingTreeOnDisk_returnsNothing() throws Exception {
        try (Repository repository = checkoutOf("abc-gone")) {
            Path workingTree = repository.getWorkTree().toPath();
            ReflectionTestUtils.setField(repository, "localPath", workingTree.resolve("does-not-exist"));

            assertThat(gitService.listFilesAndFolders(repository, false)).as("a working tree that is not on disk yields no files").isEmpty();
        }
    }

    @Test
    void getOriginHead_resolvesTheBranchTheRemoteHeadPointsAt() throws Exception {
        // resetToOriginHead resets to "origin/" + this value and silently does nothing when it is missing, so the branch name has to come back unabbreviated.
        try (Repository repository = checkoutOf("abc-originhead")) {
            ReflectionTestUtils.setField(repository, "localPath", repository.getWorkTree().toPath());

            assertThat((String) ReflectionTestUtils.invokeMethod(gitService, "getOriginHead", repository)).as("the default branch of the remote is resolved")
                    .isEqualTo(DEFAULT_BRANCH);
        }
    }

    @Test
    void resetToOriginHead_discardsLocalCommitsAndRestoresTheRemoteState() throws Exception {
        try (Repository repository = checkoutOf("abc-reset")) {
            Path workingTree = repository.getWorkTree().toPath();
            ReflectionTestUtils.setField(repository, "localPath", workingTree);
            try (Git git = new Git(repository)) {
                FileUtils.write(workingTree.resolve("Local.java").toFile(), "class Local {}", StandardCharsets.UTF_8);
                git.add().addFilepattern(".").call();
                GitService.commit(git).setMessage("a commit that was never pushed").call();
                assertThat(workingTree.resolve("Local.java")).as("the local commit is there before the reset").isRegularFile();

                gitService.resetToOriginHead(repository);

                assertThat(workingTree.resolve("Local.java")).as("the unpushed file is gone after resetting to origin").doesNotExist();
                assertThat(workingTree.resolve("README.md")).as("what the remote has is restored").isRegularFile();
            }
        }
    }

    @Test
    void getOrCheckoutRepository_whileTheSamePathIsBeingCloned_givesUpInsteadOfCloningTwice() {
        // A second clone into a directory that a clone is already writing to would corrupt it, so the service waits and then refuses.
        LocalVCRepositoryUri repositoryUri = uriFor("abc-busy");
        Path localPath = gitService.getLocalPathOfRepo(baseDir, repositoryUri);
        Map<Path, Path> cloneInProgress = (Map<Path, Path>) ReflectionTestUtils.getField(gitService, "cloneInProgressOperations");
        cloneInProgress.put(localPath, localPath);
        try {
            assertThatExceptionOfType(GitException.class).isThrownBy(() -> gitService.getOrCheckoutRepositoryWithTargetPath(repositoryUri, baseDir, true, true))
                    .withMessageContaining("Cannot clone the same repository multiple times");
        }
        finally {
            cloneInProgress.remove(localPath);
        }
    }

    @Test
    void getOrCheckoutRepository_whenWaitingForABusyPathIsInterrupted_reportsTheCancellation() {
        LocalVCRepositoryUri repositoryUri = uriFor("abc-interrupted");
        Path localPath = gitService.getLocalPathOfRepo(baseDir, repositoryUri);
        Map<Path, Path> cloneInProgress = (Map<Path, Path>) ReflectionTestUtils.getField(gitService, "cloneInProgressOperations");
        cloneInProgress.put(localPath, localPath);
        try {
            Thread.currentThread().interrupt();
            assertThatExceptionOfType(CanceledException.class).isThrownBy(() -> gitService.getOrCheckoutRepositoryWithTargetPath(repositoryUri, baseDir, true, true))
                    .withMessageContaining("interrupted");
        }
        finally {
            Thread.interrupted();
            cloneInProgress.remove(localPath);
        }
    }

    /**
     * A symbolic link inside a repository points at whatever the pusher chose, which may be anywhere on the server.
     * Listing must therefore never hand one out, neither as a file nor as a directory to descend into.
     */
    @Test
    void listFilesAndFolders_neverExposesSymbolicLinks() throws Exception {
        try (Repository repository = checkoutOf("abc-symlinks")) {
            Path workingTree = repository.getWorkTree().toPath();
            ReflectionTestUtils.setField(repository, "localPath", workingTree);
            Path outsideDirectory = Files.createDirectories(baseDir.resolve("outside"));
            FileUtils.write(outsideDirectory.resolve("Secret.java").toFile(), "class Secret {}", StandardCharsets.UTF_8);
            Files.createSymbolicLink(workingTree.resolve("link-to-outside"), outsideDirectory);
            Files.createSymbolicLink(workingTree.resolve("link-to-readme"), workingTree.resolve("README.md"));

            var listed = gitService.listFilesAndFolders(repository, false);

            var names = listed.keySet().stream().map(java.io.File::getName).toList();
            assertThat(names).as("a symlinked directory is not listed").doesNotContain("link-to-outside");
            assertThat(names).as("the content behind a symlinked directory is not reachable either").doesNotContain("Secret.java");
            assertThat(names).as("a symlinked file is not listed").doesNotContain("link-to-readme");
            assertThat(names).as("the regular files of the repository are still listed").contains("README.md");
        }
    }

    @Test
    void listFilesAndFolders_withoutAWorkingTreePath_returnsNothing() throws Exception {
        // A repository that was never checked out has no working tree to list, which is reported as an empty listing rather than as a failure.
        try (Repository repository = checkoutOf("abc-nopath")) {
            ReflectionTestUtils.setField(repository, "localPath", null);

            assertThat(gitService.listFilesAndFolders(repository, false)).as("a repository without a working tree path yields no files").isEmpty();
        }
    }

    @Test
    void getExistingCheckedOutRepositoryByLocalPath_forAPathThatIsNotOnDisk_returnsNull() {
        // Callers use this to find out whether a repository is already checked out, so a missing checkout is an answer rather than an error.
        assertThat(gitService.getExistingCheckedOutRepositoryByLocalPath(baseDir.resolve("never-checked-out"), uriFor("abc-exercise"), DEFAULT_BRANCH, false))
                .as("a path that does not exist has no checked out repository").isNull();
    }

    @Test
    void deleteLocalProgrammingExerciseReposFolder_removesEveryCheckoutOfTheExercise() throws Exception {
        Path cloneBasePath = Files.createDirectories(baseDir.resolve("repos"));
        ReflectionTestUtils.setField(gitService, "repoClonePath", cloneBasePath);
        Path projectFolder = Files.createDirectories(cloneBasePath.resolve("ABC").resolve("abc-exercise"));
        FileUtils.write(projectFolder.resolve("Main.java").toFile(), "class Main {}", StandardCharsets.UTF_8);
        ProgrammingExercise exercise = new ProgrammingExercise();
        ReflectionTestUtils.setField(exercise, "projectKey", "ABC");

        gitService.deleteLocalProgrammingExerciseReposFolder(exercise);

        assertThat(cloneBasePath.resolve("ABC")).as("every checkout of the exercise is removed").doesNotExist();
        assertThat(cloneBasePath).as("the clone directory itself is kept").isDirectory();
    }
}

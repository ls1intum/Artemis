package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.dto.FileMove;

/**
 * Unit tests for the file operations the online code editor performs on a student's repository.
 * <p>
 * Every path in these calls comes from the request, so the first thing each of them has to do is refuse to leave the
 * repository. A path that escapes would let the editor read or write anywhere the server can reach, and the operations
 * themselves have to be exact as well: renaming onto an existing file would destroy work, and deleting has to reach a
 * directory without reaching past it.
 */
class RepositoryServiceFileOperationsTest {

    @TempDir
    Path baseDir;

    private Repository repository;

    private RepositoryService repositoryService;

    private Path workingTree;

    @BeforeEach
    void setUp() throws Exception {
        workingTree = Files.createDirectories(baseDir.resolve("checkout"));
        Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call().close();
        GitService gitService = new GitService();
        ReflectionTestUtils.setField(gitService, "localVCBasePath", baseDir);
        BareGitRepositoryService bareGitRepositoryService = new BareGitRepositoryService();
        ReflectionTestUtils.setField(bareGitRepositoryService, "localVCBasePath", baseDir);
        repositoryService = new RepositoryService(gitService, bareGitRepositoryService, Optional.empty());

        repository = new Repository(workingTree.resolve(".git").toString(), new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise"));
        ReflectionTestUtils.setField(repository, "localPath", workingTree);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    private static InputStream content(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void createFile_writesTheFileTheEditorAsksFor() throws Exception {
        repositoryService.createFile(repository, "src/Main.java", content("public class Main {}"));

        assertThat(workingTree.resolve("src/Main.java")).isRegularFile().content(StandardCharsets.UTF_8).isEqualTo("public class Main {}");
    }

    @Test
    void createFile_forAPathThatLeavesTheRepository_isRejected() {
        // The path comes from the request, so it must never be resolved against the file system as it is.
        for (String escapingPath : new String[] { "../outside.txt", "src/../../outside.txt", "../../../../etc/passwd" }) {
            assertThatExceptionOfType(IllegalArgumentException.class).as("the path '%s' must be rejected", escapingPath)
                    .isThrownBy(() -> repositoryService.createFile(repository, escapingPath, content("owned"))).withMessageContaining("path traversal");
        }
        assertThat(baseDir.resolve("outside.txt")).as("nothing is written outside the repository").doesNotExist();
    }

    @Test
    void createFile_forAPathThatIsAlreadyTaken_isRejected() throws Exception {
        // Creating over an existing file would destroy the student's work without them asking for it.
        FileUtils.write(workingTree.resolve("Main.java").toFile(), "the original", StandardCharsets.UTF_8);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.createFile(repository, "Main.java", content("the replacement")));
        assertThat(workingTree.resolve("Main.java")).content(StandardCharsets.UTF_8).isEqualTo("the original");
    }

    @Test
    void createFolder_createsTheDirectoryAndKeepsItInGit() throws Exception {
        repositoryService.createFolder(repository, "src", content(""));

        assertThat(workingTree.resolve("src")).isDirectory();
        // Git tracks files, not directories, so a new folder needs a file in it to survive a commit.
        assertThat(workingTree.resolve("src/.keep")).isRegularFile();
    }

    @Test
    void createFolder_forAPathThatLeavesTheRepository_isRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.createFolder(repository, "../outside", content("")))
                .withMessageContaining("path traversal");
        assertThat(baseDir.resolve("outside")).doesNotExist();
    }

    @Test
    void renameFile_movesTheFileAndLeavesNothingBehind() throws Exception {
        FileUtils.write(workingTree.resolve("src/Old.java").toFile(), "public class Old {}", StandardCharsets.UTF_8);

        repositoryService.renameFile(repository, new FileMove("src/Old.java", "New.java"));

        assertThat(workingTree.resolve("src/New.java")).isRegularFile().content(StandardCharsets.UTF_8).isEqualTo("public class Old {}");
        assertThat(workingTree.resolve("src/Old.java")).doesNotExist();
    }

    @Test
    void renameFile_ontoAFileThatAlreadyExists_isRejected() throws Exception {
        // Renaming onto an existing file would silently overwrite it, so the editor has to be told instead.
        FileUtils.write(workingTree.resolve("Old.java").toFile(), "public class Old {}", StandardCharsets.UTF_8);
        FileUtils.write(workingTree.resolve("New.java").toFile(), "public class New {}", StandardCharsets.UTF_8);

        assertThatExceptionOfType(FileAlreadyExistsException.class).isThrownBy(() -> repositoryService.renameFile(repository, new FileMove("Old.java", "New.java")));
        assertThat(workingTree.resolve("New.java")).content(StandardCharsets.UTF_8).isEqualTo("public class New {}");
        assertThat(workingTree.resolve("Old.java")).as("the file that was to be renamed is left alone").exists();
    }

    @Test
    void renameFile_forAFileThatDoesNotExist_isRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.renameFile(repository, new FileMove("Missing.java", "New.java")));
    }

    @ParameterizedTest
    @ValueSource(strings = { ".git", ".GIT", ".Git" })
    void renameFile_toAnInvalidName_isRejected(String newName) throws Exception {
        FileUtils.write(workingTree.resolve("nested/prepared/content.txt").toFile(), "content", StandardCharsets.UTF_8);

        assertThatExceptionOfType(IllegalArgumentException.class).as("renaming a directory to '%s' must be rejected", newName)
                .isThrownBy(() -> repositoryService.renameFile(repository, new FileMove("nested/prepared", newName)));

        assertThat(workingTree.resolve("nested/prepared/content.txt")).as("nothing is moved").isRegularFile();
        assertThat(workingTree.resolve("nested").resolve(newName)).as("nothing is created").doesNotExist();
    }

    @Test
    void renameFile_aFileToAnInvalidName_isRejected() throws Exception {
        FileUtils.write(workingTree.resolve("nested/prepared").toFile(), "content", StandardCharsets.UTF_8);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.renameFile(repository, new FileMove("nested/prepared", ".git")));

        assertThat(workingTree.resolve("nested/prepared")).isRegularFile();
        assertThat(workingTree.resolve("nested/.git")).doesNotExist();
    }

    @Test
    void createFolder_withAnInvalidName_isRejected() throws Exception {
        Files.createDirectories(workingTree.resolve("nested"));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.createFolder(repository, "nested/.git", content("")));

        assertThat(workingTree.resolve("nested/.git")).doesNotExist();
    }

    @Test
    void createFile_withAnInvalidName_isRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.createFile(repository, "nested/.git", content("content")));

        assertThat(workingTree.resolve("nested/.git")).doesNotExist();
    }

    @Test
    void createFile_insideAFolderWithAnInvalidName_isRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.createFile(repository, "nested/.git/config", content("content")));

        assertThat(workingTree.resolve("nested/.git")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = { ".gitignore", ".gitattributes", ".github/workflows/ci.yml", "src/.gitkeep", "git/info/exclude" })
    void createFile_withSimilarNames_isAllowed(String path) throws Exception {
        // Only the exact name is invalid, similar names are ordinary content of an exercise.
        repositoryService.createFile(repository, path, content("content"));

        assertThat(workingTree.resolve(path)).isRegularFile().content(StandardCharsets.UTF_8).isEqualTo("content");
    }

    @Test
    void deleteFile_removesTheFile() throws Exception {
        FileUtils.write(workingTree.resolve("src/Main.java").toFile(), "public class Main {}", StandardCharsets.UTF_8);

        repositoryService.deleteFile(repository, "src/Main.java");

        assertThat(workingTree.resolve("src/Main.java")).doesNotExist();
        assertThat(workingTree.resolve("src")).as("the directory it was in is kept").isDirectory();
    }

    @Test
    void deleteFile_forADirectory_removesItWithItsContent() throws Exception {
        FileUtils.write(workingTree.resolve("src/sorting/BubbleSort.java").toFile(), "public class BubbleSort {}", StandardCharsets.UTF_8);

        repositoryService.deleteFile(repository, "src/sorting");

        assertThat(workingTree.resolve("src/sorting")).doesNotExist();
        assertThat(workingTree.resolve("src")).as("only the directory that was asked for is removed").isDirectory();
    }

    @Test
    void deleteFile_forAPathThatLeavesTheRepository_isRejected() throws Exception {
        Path outside = baseDir.resolve("outside.txt");
        FileUtils.write(outside.toFile(), "not the editor's to delete", StandardCharsets.UTF_8);

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.deleteFile(repository, "../outside.txt"))
                .withMessageContaining("path traversal");
        assertThat(outside).as("a file outside the repository survives").exists();
    }

    @Test
    void deleteFile_forAFileThatDoesNotExist_isRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> repositoryService.deleteFile(repository, "Missing.java"));
    }

    @Test
    void getFile_readsWhatTheEditorShows() throws Exception {
        FileUtils.write(workingTree.resolve("src/Main.java").toFile(), "public class Main {}", StandardCharsets.UTF_8);

        byte[] content = repositoryService.getFile(repository, "src/Main.java");

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("public class Main {}");
    }

    @Test
    void getFile_forAFileThatDoesNotExist_isReported() {
        assertThatExceptionOfType(FileNotFoundException.class).isThrownBy(() -> repositoryService.getFile(repository, "Missing.java"));
    }

    @Test
    void createFile_forARepositoryWithoutLocalPath_isRejected() {
        ReflectionTestUtils.setField(repository, "localPath", null);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> repositoryService.createFile(repository, "src/Main.java", content("x")))
                .withMessageContaining("no local path");
    }

    @Test
    void getFilesContentFromBareRepository_forACommitThatCannotBeResolved_returnsNoFiles() throws Exception {
        assertThat(repositoryService.getFilesContentFromBareRepository(repository, "no-such-commit")).isEmpty();
    }

    @Test
    void getFileContentFromBareRepository_forACommitThatCannotBeResolved_returnsEmpty() throws Exception {
        assertThat(repositoryService.getFileContentFromBareRepository(repository, "no-such-commit", "src/Main.java")).isEmpty();
    }

    @Test
    void rawBlobLookupDistinguishesPresentAbsentAndBlankPaths() throws Exception {
        FileUtils.writeStringToFile((workingTree.resolve("message.txt")).toFile(), "exact committed contents", StandardCharsets.UTF_8);
        var git = org.eclipse.jgit.api.Git.wrap(repository);
        git.add().addFilepattern("message.txt").call();
        var commit = GitService.commit(git).setMessage("Commit raw text").setAuthor("Fixture", "fixture@example.com").call();
        assertThat(repositoryService.getFileContentFromBareRepository(repository, commit.getName(), "message.txt")).contains("exact committed contents");
        assertThat(repositoryService.getFileContentFromBareRepository(repository, commit.getName(), "absent.txt")).isEmpty();
        assertThat(repositoryService.getFileContentFromBareRepository(repository, commit.getName(), " ")).isEmpty();
    }

    @Test
    void emptyRepositoryHasNoSnapshotBeforeDeadlineOrAtHead() throws Exception {
        assertThat(repositoryService.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(repository, java.time.ZonedDateTime.now())).isEmpty();
        assertThat(repositoryService.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(repository, null)).isEmpty();
        assertThat(Files.isDirectory(workingTree.resolve(".git"))).isTrue();
    }

    @Test
    void clearingRepositoryPreservesGitHistoryAndRemovesNestedContent() throws Exception {
        Files.createDirectories(workingTree.resolve("nested"));
        FileUtils.writeStringToFile((workingTree.resolve("nested/source.txt")).toFile(), "tracked source", StandardCharsets.UTF_8);
        var git = org.eclipse.jgit.api.Git.wrap(repository);
        git.add().addFilepattern(".").call();
        var commit = GitService.commit(git).setMessage("Commit source").setAuthor("Fixture", "fixture@example.com").call();
        repositoryService.deleteAllContentInRepository(repository);
        assertThat(workingTree.resolve("nested")).doesNotExist();
        assertThat(workingTree.resolve(".git")).isDirectory();
        assertThat(repository.resolve("HEAD")).isEqualTo(commit.getId());
    }

    @Test
    void repositoryContentForLanguageModelsSkipsHugeFilesAndStaysWithinTheTotalLimit() throws Exception {
        FileUtils.writeStringToFile(workingTree.resolve("small.txt").toFile(), "small", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(workingTree.resolve("huge.txt").toFile(), "x".repeat((int) RepositoryService.MAX_SELECTED_FILE_SIZE_BYTES + 1), StandardCharsets.UTF_8);
        for (int i = 0; i < 8; i++) {
            FileUtils.writeStringToFile(workingTree.resolve("medium" + i + ".txt").toFile(), "y".repeat(900_000), StandardCharsets.UTF_8);
        }
        var git = org.eclipse.jgit.api.Git.wrap(repository);
        git.add().addFilepattern(".").call();
        GitService.commit(git).setMessage("Commit large files").setAuthor("Fixture", "fixture@example.com").call();
        var uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");
        var bare = org.mockito.Mockito.mock(BareGitRepositoryService.class);
        org.mockito.Mockito.when(bare.getBareRepository(uri, false)).thenAnswer(invocation -> new Repository(workingTree.resolve(".git").toString(), uri));
        var limited = new RepositoryService(org.mockito.Mockito.mock(GitService.class), bare, Optional.empty());

        var contents = limited.getContextFilesContentFromBareRepositoryForLastCommit(uri, null, Long.MAX_VALUE);

        // Only the context of language models is bounded: comparisons and exports keep every file, and the committed content of a path can be read on its own.
        assertThat(limited.getFilesContentFromBareRepositoryForLastCommit(uri)).containsKey("huge.txt");
        // The committed content of a path is read within the limits too. A path that exists but does not fit is reported as omitted, a missing path is in neither part.
        var committed = limited.getCommittedContentOfPaths(uri, java.util.Set.of("huge.txt", "small.txt", "missing.txt"));
        assertThat(committed.files()).containsOnlyKeys("small.txt");
        assertThat(committed.omitted()).containsExactly("huge.txt");
        assertThat(contents).doesNotContainKey("huge.txt");
        assertThat(contents.values().stream().mapToLong(String::length).sum()).isLessThanOrEqualTo(RepositoryService.MAX_SELECTED_FILES_TOTAL_SIZE_BYTES);
        assertThat(contents).containsKey("small.txt").hasSizeLessThan(9);
    }

    @Test
    void repositoryContentWithPathFilterDoesNotCountFilesThatTheFilterRejects() throws Exception {
        // The files of another language come first in the traversal and would use up the total limit, if they were counted before they are filtered.
        for (int i = 0; i < 8; i++) {
            FileUtils.writeStringToFile(workingTree.resolve("a-data" + i + ".txt").toFile(), "y".repeat(900_000), StandardCharsets.UTF_8);
        }
        FileUtils.writeStringToFile(workingTree.resolve("z-Main.java").toFile(), "//".repeat(400_000), StandardCharsets.UTF_8);
        var git = org.eclipse.jgit.api.Git.wrap(repository);
        git.add().addFilepattern(".").call();
        GitService.commit(git).setMessage("Commit data and source").setAuthor("Fixture", "fixture@example.com").call();
        var uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");
        var bare = org.mockito.Mockito.mock(BareGitRepositoryService.class);
        org.mockito.Mockito.when(bare.getBareRepository(uri, false)).thenAnswer(invocation -> new Repository(workingTree.resolve(".git").toString(), uri));
        var limited = new RepositoryService(org.mockito.Mockito.mock(GitService.class), bare, Optional.empty());

        assertThat(limited.getContextFilesContentFromBareRepositoryForLastCommit(uri, null, Long.MAX_VALUE)).doesNotContainKey("z-Main.java");
        assertThat(limited.getContextFilesContentFromBareRepositoryForLastCommit(uri, path -> path.endsWith(".java"), Long.MAX_VALUE)).containsOnlyKeys("z-Main.java");
    }

    @Test
    void repositoryContentWithAFileSizeLimitDoesNotCountFilesAboveIt() throws Exception {
        // Files above the size that the caller keeps would use up the limit of the total size and leave out the source file, which comes last in the traversal
        for (int i = 0; i < 8; i++) {
            FileUtils.writeStringToFile(workingTree.resolve("a-build" + i + ".txt").toFile(), "y".repeat(1_040_000), StandardCharsets.UTF_8);
        }
        // five of the files above fit the limit of the total size and leave less room than this source file needs
        FileUtils.writeStringToFile(workingTree.resolve("z-Main.java").toFile(), "//".repeat(25_000), StandardCharsets.UTF_8);
        var git = org.eclipse.jgit.api.Git.wrap(repository);
        git.add().addFilepattern(".").call();
        GitService.commit(git).setMessage("Commit build output and source").setAuthor("Fixture", "fixture@example.com").call();
        var uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");
        var bare = org.mockito.Mockito.mock(BareGitRepositoryService.class);
        org.mockito.Mockito.when(bare.getBareRepository(uri, false)).thenAnswer(invocation -> new Repository(workingTree.resolve(".git").toString(), uri));
        var limited = new RepositoryService(org.mockito.Mockito.mock(GitService.class), bare, Optional.empty());

        assertThat(limited.getContextFilesContentFromBareRepositoryForLastCommit(uri, null, Long.MAX_VALUE)).doesNotContainKey("z-Main.java");
        assertThat(limited.getContextFilesContentFromBareRepositoryForLastCommit(uri, null, 100 * 1024L)).containsOnlyKeys("z-Main.java");
    }

    @Test
    void unavailableBareRepositoryFallsBackToCheckoutAndReportsCheckoutFailure() throws Exception {
        FileUtils.writeStringToFile((workingTree.resolve("cached.txt")).toFile(), "cached committed contents", StandardCharsets.UTF_8);
        var git = org.eclipse.jgit.api.Git.wrap(repository);
        git.add().addFilepattern(".").call();
        GitService.commit(git).setMessage("Commit cached source").setAuthor("Fixture", "fixture@example.com").call();
        var uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");
        var bare = org.mockito.Mockito.mock(BareGitRepositoryService.class);
        var checkout = org.mockito.Mockito.mock(GitService.class);
        org.mockito.Mockito.when(bare.getBareRepository(uri, false)).thenThrow(new de.tum.cit.aet.artemis.programming.exception.GitException("Bare storage unavailable"));
        org.mockito.Mockito.when(checkout.getOrCheckoutRepository(uri, true, false)).thenAnswer(invocation -> new Repository(workingTree.resolve(".git").toString(), uri));
        var fallback = new RepositoryService(checkout, bare, Optional.empty());
        assertThat(fallback.getFilesContentFromBareRepositoryForLastCommit(uri)).containsEntry("cached.txt", "cached committed contents");
        assertThat(fallback.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(uri, java.time.ZonedDateTime.now().plusHours(1))).containsEntry("cached.txt",
                "cached committed contents");
        org.mockito.Mockito.doThrow(new org.eclipse.jgit.api.errors.TransportException("Checkout unavailable")).when(checkout).getOrCheckoutRepository(uri, true, false);
        assertThatIOException().isThrownBy(() -> fallback.getFilesContentFromBareRepositoryForLastCommit(uri)).withMessageContaining("Failed to retrieve repository content");
    }

    @Test
    void readingAllFilesLeavesOutATextFileAboveTheSizeLimitAndFailsForAReadOfThatFile() throws Exception {
        FileUtils.writeStringToFile(workingTree.resolve("small.txt").toFile(), "small", StandardCharsets.UTF_8);
        FileUtils.writeByteArrayToFile(workingTree.resolve("large.txt").toFile(), new byte[(int) (RepositoryService.MAX_FILE_CONTENT_BYTES + 1)]);
        try (Git git = Git.open(workingTree.toFile())) {
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("files").setAuthor("a", "a@example.com").call();
        }

        // The features that read a whole repository, such as the code of an exercise for an AI model, still work with the other files
        assertThat(repositoryService.getFilesContentFromBareRepositoryForLastCommit(repository)).containsOnlyKeys("small.txt");
        // A read of the file itself says that it is too large, and does not report the file as missing
        assertThatIOException().isThrownBy(() -> repositoryService.getFileContentFromBareRepository(repository, "HEAD", "large.txt")).withMessageContaining("large.txt");
    }

    @Test
    void aCompleteSnapshotOfACommitFailsInsteadOfOmittingAFileAboveTheSizeLimit() throws Exception {
        FileUtils.writeStringToFile(workingTree.resolve("small.txt").toFile(), "small", StandardCharsets.UTF_8);
        FileUtils.writeByteArrayToFile(workingTree.resolve("large.txt").toFile(), new byte[(int) (RepositoryService.MAX_FILE_CONTENT_BYTES + 1)]);
        try (Git git = Git.open(workingTree.toFile())) {
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("files").setAuthor("a", "a@example.com").call();
        }

        // A caller that compares two snapshots would read a missing file as a deletion
        assertThatIOException().isThrownBy(() -> repositoryService.getFilesContentFromBareRepository(repository, "HEAD")).withMessageContaining("large.txt");
    }

    @Test
    void readingAllFilesWithinTheLimitsReturnsThem() throws Exception {
        FileUtils.writeStringToFile(workingTree.resolve("small.txt").toFile(), "small", StandardCharsets.UTF_8);
        try (Git git = Git.open(workingTree.toFile())) {
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("files").setAuthor("a", "a@example.com").call();
        }

        assertThat(repositoryService.getFilesContentFromBareRepositoryForLastCommit(repository)).containsEntry("small.txt", "small");
    }
}

package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import org.springframework.test.util.ReflectionTestUtils;

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
        repositoryService = new RepositoryService(gitService, Optional.empty());

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
}

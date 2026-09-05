package de.tum.cit.aet.artemis.localvc.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link DirectoryRepositoryContentSink}.
 */
class DirectoryRepositoryContentSinkTest {

    @TempDir
    Path tempDir;

    /**
     * The paths the sink is given come from a git tree, and a git tree holds whatever a pushing client put there. A
     * name that walks out of the export directory must be refused rather than written, because the directory the export
     * is assembled in sits next to the other participations of the same export.
     */
    @Test
    void shouldRefuseToWriteOutsideTheTargetDirectory() throws IOException {
        Path root = tempDir.resolve("repository");
        try (DirectoryRepositoryContentSink sink = new DirectoryRepositoryContentSink(root)) {
            assertThatExceptionOfType(IOException.class).isThrownBy(() -> sink.openFile("../escaped.txt", 0644)).withMessageContaining("escapes the target directory");
            assertThatExceptionOfType(IOException.class).isThrownBy(() -> sink.openFile("nested/../../escaped.txt", 0644)).withMessageContaining("escapes the target directory");
            assertThatExceptionOfType(IOException.class).isThrownBy(() -> sink.createDirectory("../escaped")).withMessageContaining("escapes the target directory");
        }

        assertThat(tempDir.resolve("escaped.txt")).as("a path that escapes the target directory must not be written").doesNotExist();
        assertThat(tempDir.resolve("escaped")).as("a directory that escapes the target directory must not be created").doesNotExist();
    }

    /**
     * A repository holds its files in directories that do not exist yet when the file is written, so the sink has to
     * create them on the way.
     */
    @Test
    void shouldCreateTheParentDirectoriesOfANestedFile() throws IOException {
        Path root = tempDir.resolve("repository");
        try (DirectoryRepositoryContentSink sink = new DirectoryRepositoryContentSink(root)) {
            try (OutputStream outputStream = sink.openFile("src/main/java/Main.java", 0644)) {
                outputStream.write("public class Main {}".getBytes(StandardCharsets.UTF_8));
            }
        }

        assertThat(root.resolve("src/main/java/Main.java")).content(StandardCharsets.UTF_8).isEqualTo("public class Main {}");
    }

    /**
     * The synthetic {@code .git} directory is scaffolded before anything is written into it, and the builder asks for
     * the same directory more than once.
     */
    @Test
    void shouldTolerateADirectoryThatAlreadyExists() throws IOException {
        Path root = tempDir.resolve("repository");
        try (DirectoryRepositoryContentSink sink = new DirectoryRepositoryContentSink(root)) {
            sink.createDirectory(".git/objects/pack/");
            sink.createDirectory(".git/objects/pack/");
        }

        assertThat(Files.isDirectory(root.resolve(".git/objects/pack"))).isTrue();
    }
}

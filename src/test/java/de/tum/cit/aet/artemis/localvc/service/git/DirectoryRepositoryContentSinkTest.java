package de.tum.cit.aet.artemis.localvc.service.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

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
     * The directory this writes is archived from disk further up, so these permissions are the ones the student
     * extracts - {@code 0644} for a regular file and {@code 0755} for an executable one, exactly what git recorded.
     * Losing the executable bit would make git report a modification in a working tree nobody has touched, which is
     * the defect the export E2E tests guard.
     */
    @Test
    void shouldWriteFilesWithTheModeGitRecorded() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "POSIX permissions are not supported on this file system");

        Path root = tempDir.resolve("repository");
        try (DirectoryRepositoryContentSink sink = new DirectoryRepositoryContentSink(root)) {
            try (OutputStream outputStream = sink.openFile("README.md", 0100644)) {
                outputStream.write("readme".getBytes(StandardCharsets.UTF_8));
            }
            try (OutputStream outputStream = sink.openFile("gradlew", 0100755)) {
                outputStream.write("#!/bin/sh".getBytes(StandardCharsets.UTF_8));
            }
        }

        assertThat(Files.getPosixFilePermissions(root.resolve("README.md"))).as("a regular file").isEqualTo(PosixFilePermissions.fromString("rw-r--r--"));
        assertThat(Files.getPosixFilePermissions(root.resolve("gradlew"))).as("an executable file").isEqualTo(PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    /**
     * Git stores only {@code 100644} and {@code 100755}, so nothing can ask for a group- or world-writable file, and
     * the sink must not produce one whatever it is handed.
     */
    @Test
    void shouldNeverGrantWriteAccessBeyondTheOwner() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "POSIX permissions are not supported on this file system");

        Path root = tempDir.resolve("repository");
        try (DirectoryRepositoryContentSink sink = new DirectoryRepositoryContentSink(root)) {
            try (OutputStream outputStream = sink.openFile("wide.txt", 0100777)) {
                outputStream.write("content".getBytes(StandardCharsets.UTF_8));
            }
        }

        assertThat(Files.getPosixFilePermissions(root.resolve("wide.txt"))).doesNotContain(PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE);
    }

    /**
     * The final mode is applied on close, so between creation and close the file already holds real repository content
     * under whatever the umask happens to be. Creating it owner-only closes that window. Assert it while the stream is
     * still open, which is the only moment that can catch it.
     */
    @Test
    void shouldCreateFilesOwnerOnlyBeforeAnythingIsWrittenToThem() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "POSIX permissions are not supported on this file system");

        Path root = tempDir.resolve("repository");
        try (DirectoryRepositoryContentSink sink = new DirectoryRepositoryContentSink(root)) {
            try (OutputStream outputStream = sink.openFile("secret.txt", 0100644)) {
                outputStream.write("student code".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                assertThat(Files.getPosixFilePermissions(root.resolve("secret.txt"))).as("the file must never exist in a wider mode, not even mid-write")
                        .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            }
        }
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

package de.tum.cit.aet.artemis.core.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.spi.FileSystemProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TempFileUtilServiceTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void replaceFileAtomicallyCreatesAndReplacesTargetWithoutLeavingTemporaryFiles() throws Exception {
        TempFileUtilService tempFileUtilService = new TempFileUtilService(tempDirectory);
        Path targetPath = tempDirectory.resolve("student").resolve("student-version.pdf");

        tempFileUtilService.replaceFileAtomically(tempDirectory, targetPath, "first version".getBytes(UTF_8));
        assertThat(Files.readString(targetPath)).isEqualTo("first version");

        tempFileUtilService.replaceFileAtomically(tempDirectory, targetPath, "second version".getBytes(UTF_8));
        assertThat(Files.readString(targetPath)).isEqualTo("second version");
        try (var files = Files.list(targetPath.getParent())) {
            assertThat(files.map(Path::getFileName).map(Path::toString)).containsExactly("student-version.pdf");
        }
    }

    @Test
    void replaceFileAtomicallyRejectsTargetsOutsideTrustedRoot() {
        TempFileUtilService tempFileUtilService = new TempFileUtilService(tempDirectory);
        Path trustedRoot = tempDirectory.resolve("student");
        Path escapedTarget = trustedRoot.resolve("..").resolve("outside.pdf");

        assertThatThrownBy(() -> tempFileUtilService.replaceFileAtomically(trustedRoot, escapedTarget, "content".getBytes(UTF_8))).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("trusted root");
        assertThat(escapedTarget).doesNotExist();
    }

    @Test
    void replaceFileAtomicallySupportsLongTargetFilenames() throws Exception {
        TempFileUtilService tempFileUtilService = new TempFileUtilService(tempDirectory);
        Path targetPath = tempDirectory.resolve("a".repeat(240) + ".pdf");

        tempFileUtilService.replaceFileAtomically(tempDirectory, targetPath, "content".getBytes(UTF_8));

        assertThat(Files.readString(targetPath)).isEqualTo("content");
        try (var files = Files.list(tempDirectory)) {
            assertThat(files).containsExactly(targetPath);
        }
    }

    @Test
    void moveReplacingFallsBackWhenAtomicMoveCannotReplaceExistingTarget() throws Exception {
        TempFileUtilService tempFileUtilService = new TempFileUtilService(tempDirectory);
        Path source = mock(Path.class);
        Path target = mock(Path.class);
        FileSystem fileSystem = mock(FileSystem.class);
        FileSystemProvider provider = mock(FileSystemProvider.class);
        when(source.getFileSystem()).thenReturn(fileSystem);
        when(fileSystem.provider()).thenReturn(provider);
        doThrow(new FileAlreadyExistsException("target.pdf")).when(provider).move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        tempFileUtilService.moveReplacing(source, target);

        var inOrder = inOrder(provider);
        inOrder.verify(provider).move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        inOrder.verify(provider).move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}

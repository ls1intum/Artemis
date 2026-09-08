package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

class InteractiveSandboxCopyInValidationTest {

    @Test
    void rejectsDestinationOutsideWritableRoots() {
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> InteractiveSandboxService.validateCopyInDestination("/workspace/../../etc"))
                .withMessageContaining("outside a writable sandbox root");
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> InteractiveSandboxService.validateCopyInDestination("workspace"))
                .withMessageContaining("outside a writable sandbox root");
    }

    @Test
    void acceptsDestinationInsideWritableRoot() {
        assertThatCode(() -> InteractiveSandboxService.validateCopyInDestination("/workspace/project")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAbsoluteArchiveEntry() throws IOException {
        assertUnsafeArchive("/etc/owned", TarArchiveEntry.LF_NORMAL);
    }

    @Test
    void rejectsParentArchiveEntry() throws IOException {
        assertUnsafeArchive("project/../../owned", TarArchiveEntry.LF_NORMAL);
    }

    @Test
    void rejectsSymbolicLink() throws IOException {
        assertUnsafeArchive("project/link", TarArchiveEntry.LF_SYMLINK);
    }

    @Test
    void rejectsHardLink() throws IOException {
        assertUnsafeArchive("project/link", TarArchiveEntry.LF_LINK);
    }

    @Test
    void acceptsRegularFilesAndDirectories() throws IOException {
        byte[] archive;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            TarArchiveEntry directory = new TarArchiveEntry("project/");
            tar.putArchiveEntry(directory);
            tar.closeArchiveEntry();
            byte[] content = "safe".getBytes();
            TarArchiveEntry file = new TarArchiveEntry("project/Main.java");
            file.setSize(content.length);
            tar.putArchiveEntry(file);
            tar.write(content);
            tar.closeArchiveEntry();
            tar.finish();
            archive = bytes.toByteArray();
        }

        assertThatCode(() -> InteractiveSandboxService.validateCopyInArchive(archive)).doesNotThrowAnyException();
    }

    private static void assertUnsafeArchive(String name, byte linkFlag) throws IOException {
        byte[] archive;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            TarArchiveEntry entry = new TarArchiveEntry(name, linkFlag, true);
            if (entry.isSymbolicLink() || entry.isLink()) {
                entry.setLinkName("../../outside");
            }
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            tar.finish();
            archive = bytes.toByteArray();
        }

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> InteractiveSandboxService.validateCopyInArchive(archive)).withMessageContaining("unsafe entry");
    }
}

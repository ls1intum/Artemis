package de.tum.cit.aet.artemis.aiworker.service.sandbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.aiworker.api.SandboxUnavailableException;

class DockerSandboxCopyInValidationTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "/workspace-other", "/opt/aiworker-other", "/workspace/../tmp", "C:/workspace/project", "\\workspace\\project", "/workspace/./project" })
    void rejectsNonCanonicalOrNonPosixDestinations(String destination) {
        assertThatExceptionOfType(SandboxUnavailableException.class).isThrownBy(
                () -> DockerSandboxService.validateCopyInDestination(destination, java.util.Set.of("/workspace", "/tmp", "/opt/aiworker", "/opt/aiworker-readiness-fixture")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/workspace", "/workspace/project", "/tmp/nested", "/opt/aiworker/project", "/opt/aiworker-readiness-fixture" })
    void acceptsCanonicalContainerPathsOnAnyHost(String destination) {
        assertThatCode(
                () -> DockerSandboxService.validateCopyInDestination(destination, java.util.Set.of("/workspace", "/tmp", "/opt/aiworker", "/opt/aiworker-readiness-fixture")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDestinationOutsideWritableRoots() {
        assertThatExceptionOfType(SandboxUnavailableException.class).isThrownBy(() -> DockerSandboxService.validateCopyInDestination("/workspace/../../etc",
                java.util.Set.of("/workspace", "/tmp", "/opt/aiworker", "/opt/aiworker-readiness-fixture"))).withMessageContaining("outside a writable sandbox root");
        assertThatExceptionOfType(SandboxUnavailableException.class).isThrownBy(
                () -> DockerSandboxService.validateCopyInDestination("workspace", java.util.Set.of("/workspace", "/tmp", "/opt/aiworker", "/opt/aiworker-readiness-fixture")))
                .withMessageContaining("outside a writable sandbox root");
    }

    @Test
    void acceptsDestinationInsideWritableRoot() {
        assertThatCode(() -> DockerSandboxService.validateCopyInDestination("/workspace/project",
                java.util.Set.of("/workspace", "/tmp", "/opt/aiworker", "/opt/aiworker-readiness-fixture"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAbsoluteArchiveEntry() throws IOException {
        assertUnsafeArchive("/etc/owned", TarArchiveEntry.LF_NORMAL);
    }

    @Test
    void rejectsParentArchiveEntry() throws IOException {
        assertUnsafeArchive("project/../../owned", TarArchiveEntry.LF_NORMAL);
    }

    @ParameterizedTest
    @ValueSource(strings = { "project/../owned", "project\\..\\owned" })
    void rejectsAmbiguousArchivePaths(String name) throws IOException {
        assertUnsafeArchive(name, TarArchiveEntry.LF_NORMAL);
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

        assertThatCode(() -> DockerSandboxService.validateCopyInArchive(archive)).doesNotThrowAnyException();
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

        assertThatExceptionOfType(SandboxUnavailableException.class).isThrownBy(() -> DockerSandboxService.validateCopyInArchive(archive)).withMessageContaining("unsafe entry");
    }
}

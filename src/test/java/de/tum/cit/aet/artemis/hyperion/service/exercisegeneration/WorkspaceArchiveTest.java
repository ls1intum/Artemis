package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the tar pack/unpack used to move the workspace in and out of the sandbox, in particular that large files survive the round trip (the per-file shell read it replaced
 * truncated anything over the output-capture limit, silently corrupting committed repositories).
 */
class WorkspaceArchiveTest {

    @Test
    void roundTrip_preservesContentAndStripsPrefix() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("solution/src/Calculator.java", "public class Calculator {}\n");
        files.put("solution/build.gradle", "plugins {}\n");

        // Pack under a "solution/" tree, then read it back the way Docker presents a copied-out directory (prefixed with the directory name).
        try (TarArchiveInputStream tar = new TarArchiveInputStream(WorkspaceArchive.buildWorkspaceTarStream(files, Map.of()))) {
            Map<String, String> read = WorkspaceArchive.readTar(tar, "solution");
            assertThat(read).containsOnlyKeys("src/Calculator.java", "build.gradle");
            assertThat(read.get("src/Calculator.java")).isEqualTo("public class Calculator {}\n");
        }
    }

    @Test
    void roundTrip_preservesLargeFileWithoutTruncation() throws Exception {
        String large = "x".repeat(200_000);
        Map<String, String> files = Map.of("tests/Big.java", large);

        try (TarArchiveInputStream tar = new TarArchiveInputStream(WorkspaceArchive.buildWorkspaceTarStream(files, Map.of()))) {
            Map<String, String> read = WorkspaceArchive.readTar(tar, "");
            assertThat(read.get("tests/Big.java")).hasSize(200_000).isEqualTo(large);
        }
    }

    @Test
    void readTar_excludesBinaryFilesButRoundTripsText() throws Exception {
        // The read-back is the boundary where a binary would otherwise be decoded into a lossy UTF-8 String and later re-written mangled. A binary entry (gradle-wrapper.jar bytes:
        // a NUL + non-UTF-8 sequence) must be DROPPED from the produced text map (persist preserves the scaffolded original byte-exact); a text file (build.gradle) must still
        // round-trip exactly.
        byte[] wrapperJarBytes = { 0x50, 0x4B, 0x03, 0x04, 0, 1, 2, (byte) 0xFF, (byte) 0x89 };
        try (TarArchiveInputStream in = new TarArchiveInputStream(packTar(Map.of("gradle/wrapper/gradle-wrapper.jar", wrapperJarBytes, "build.gradle",
                "plugins { id 'java' }\n".getBytes(StandardCharsets.UTF_8), "src/Main.java", "class Main {}\n".getBytes(StandardCharsets.UTF_8))))) {
            Map<String, String> read = WorkspaceArchive.readTar(in, "");
            assertThat(read).as("binary wrapper jar is excluded; text files round-trip").containsOnlyKeys("build.gradle", "src/Main.java");
            assertThat(read.get("build.gradle")).isEqualTo("plugins { id 'java' }\n");
            assertThat(read.get("src/Main.java")).isEqualTo("class Main {}\n");
        }
    }

    /** Packs a {@code path -> bytes} map into a flat tar (no prefix), for read-back tests that need to control exact byte content per entry. */
    private static InputStream packTar(Map<String, byte[]> entries) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        try (var tar = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(e.getKey());
                entry.setSize(e.getValue().length);
                tar.putArchiveEntry(entry);
                tar.write(e.getValue());
                tar.closeArchiveEntry();
            }
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    @Test
    void readTar_skipsGitMetadata() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("A.java", "a");
        files.put(".git/config", "should be skipped");

        try (TarArchiveInputStream tar = new TarArchiveInputStream(WorkspaceArchive.buildWorkspaceTarStream(files, Map.of()))) {
            Map<String, String> read = WorkspaceArchive.readTar(tar, "");
            assertThat(read).containsOnlyKeys("A.java");
        }
    }

    @Test
    void buildWorkspaceTar_packsWorkingTreePreservingBinariesAndExecBit(@TempDir Path repo) throws Exception {
        // A Gradle-style repo: a binary wrapper jar, an executable gradlew, a text build file, and a .git directory that must be excluded.
        byte[] binary = { 0, 1, 2, (byte) 0xFF, (byte) 0x89, 0x50 };
        FileUtils.writeByteArrayToFile(repo.resolve("gradle-wrapper.jar").toFile(), binary);
        Path gradlew = repo.resolve("gradlew");
        FileUtils.writeStringToFile(gradlew.toFile(), "#!/bin/sh\necho hi\n", StandardCharsets.UTF_8);
        gradlew.toFile().setExecutable(true);
        FileUtils.writeStringToFile(repo.resolve("build.gradle").toFile(), "plugins {}\n", StandardCharsets.UTF_8);
        Files.createDirectory(repo.resolve(".git"));
        FileUtils.writeStringToFile(repo.resolve(".git").resolve("config").toFile(), "secret", StandardCharsets.UTF_8);

        Map<String, String> textFiles = Map.of("verify.sh", "echo verify\n");
        byte[] packed;
        try (var in = WorkspaceArchive.buildWorkspaceTarStream(textFiles, Map.of("solution", repo))) {
            packed = in.readAllBytes();
        }

        boolean sawBinary = false;
        boolean gradlewExecutable = false;
        boolean sawGitConfig = false;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(packed))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                byte[] content = tar.readAllBytes();
                switch (entry.getName()) {
                    case "solution/gradle-wrapper.jar" -> sawBinary = assertArrayEquals(binary, content);
                    case "solution/gradlew" -> gradlewExecutable = (entry.getMode() & 0100) != 0;
                    case "solution/.git/config" -> sawGitConfig = true;
                    case "verify.sh" -> assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("echo verify\n");
                    default -> {
                        // build.gradle and any directory entries are fine to ignore here
                    }
                }
            }
        }
        assertThat(sawBinary).as("binary jar bytes preserved").isTrue();
        assertThat(gradlewExecutable).as("gradlew keeps its executable bit").isTrue();
        assertThat(sawGitConfig).as(".git metadata excluded").isFalse();
    }

    private static boolean assertArrayEquals(byte[] expected, byte[] actual) {
        assertThat(actual).containsExactly(expected);
        return true;
    }
}

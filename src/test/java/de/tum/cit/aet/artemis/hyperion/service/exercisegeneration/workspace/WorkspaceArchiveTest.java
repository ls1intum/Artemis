package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests the tar pack/unpack used to move the workspace in and out of the sandbox, in particular that large files survive the round trip (the per-file shell read it replaced
 * truncated anything over the output-capture limit, silently corrupting committed repositories).
 */
class WorkspaceArchiveTest {

    private static final String GITHUB_SENTINEL = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

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
    void readTar_rejectsEntriesOutsideExpectedPrefix() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("solution/A.java", "class A {}\n");
        files.put("other/B.java", "class B {}\n");

        try (TarArchiveInputStream tar = new TarArchiveInputStream(WorkspaceArchive.buildWorkspaceTarStream(files, Map.of()))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, "solution"));
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
    void readTar_rejectsAnOversizedEntry_ratherThanMaterialisingItAndOoming() throws Exception {
        // The copyOut tar is agent-controlled: a runaway or hostile agent writing a multi-GB file must be REFUSED on read-back, not read into a String and OOM the node. The
        // header-declared size is honoured before the body is read, so the reject is cheap and never allocates the body.
        long oversize = WorkspaceArchive.MAX_FILE_BYTES + 1;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarArchiveEntry entry = new TarArchiveEntry("solution/Huge.java");
            entry.setSize(oversize);
            tarOut.putArchiveEntry(entry);
            byte[] chunk = new byte[1024 * 1024];
            long written = 0;
            while (written < oversize) {
                int n = (int) Math.min(chunk.length, oversize - written);
                tarOut.write(chunk, 0, n);
                written += n;
            }
            tarOut.closeArchiveEntry();
        }
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, ""));
        }
    }

    @Test
    void readTar_rejectsASymlinkEntry_soAReadCannotBeRedirectedOutsideTheWorkspace() throws Exception {
        // The copyOut tar is agent-controlled: a symlink entry could redirect a read to a file outside the workspace on extraction, so it is refused by its link flag on read-back.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out)) {
            TarArchiveEntry link = new TarArchiveEntry("solution/passwd", TarArchiveEntry.LF_SYMLINK);
            link.setLinkName("/etc/passwd");
            tarOut.putArchiveEntry(link);
            tarOut.closeArchiveEntry();
        }
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, ""));
        }
    }

    @Test
    void readTar_rejectsNonRegularEntries() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out)) {
            TarArchiveEntry fifo = new TarArchiveEntry("solution/pipe", TarArchiveEntry.LF_FIFO);
            tarOut.putArchiveEntry(fifo);
            tarOut.closeArchiveEntry();
        }
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, ""));
        }
    }

    @Test
    void readTar_rejectsAFileNamedGit() throws Exception {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(packTar(Map.of(".git", "not metadata".getBytes(StandardCharsets.UTF_8))))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, ""));
        }
    }

    @Test
    void readTar_rejectsAPathTraversingEntry_soNoAbsoluteOrDotDotPathReachesTheCommit() throws Exception {
        // The produced map is keyed by the entry path and later written into a git repo, so a ..-traversing path must never be accepted (it would escape the repository root).
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out)) {
            TarArchiveEntry entry = new TarArchiveEntry("solution/../../evil.java");
            byte[] body = "x".getBytes(StandardCharsets.UTF_8);
            entry.setSize(body.length);
            tarOut.putArchiveEntry(entry);
            tarOut.write(body);
            tarOut.closeArchiveEntry();
        }
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, ""));
        }
    }

    @Test
    void readTar_rejectsNonCanonicalPathAliases() throws Exception {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(packTar(Map.of("solution/src/./Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8))))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, "solution"));
        }
    }

    @Test
    void readTar_rejectsBackslashesThatPersistenceWouldNormalize() throws Exception {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(packTar(Map.of("solution/src\\Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8))))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, "solution"));
        }
    }

    @Test
    void readTar_rejectsGitMetadata() throws Exception {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                packTar(Map.of("A.java", "a".getBytes(StandardCharsets.UTF_8), ".git/config", "should be skipped".getBytes(StandardCharsets.UTF_8))))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, ""));
        }
    }

    @Test
    void buildWorkspaceTarRejectsASeedPathOutsideTheWorkspace() {
        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildWorkspaceTarStream(Map.of("../opt/hyperion/verify.sh", "tampered"), Map.of()));
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

    @Test
    void buildWorkspaceTar_skipsSymlinksInsteadOfFollowingThem(@TempDir Path repo, @TempDir Path outside) throws Exception {
        Path secret = outside.resolve("secret.txt");
        FileUtils.writeStringToFile(secret.toFile(), "host secret", StandardCharsets.UTF_8);
        Files.createSymbolicLink(repo.resolve("leak.txt"), secret);
        FileUtils.writeStringToFile(repo.resolve("A.java").toFile(), "class A {}\n", StandardCharsets.UTF_8);

        byte[] packed;
        try (var in = WorkspaceArchive.buildWorkspaceTarStream(Map.of(), Map.of("solution", repo))) {
            packed = in.readAllBytes();
        }

        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ByteArrayInputStream(packed))) {
            Map<String, String> read = WorkspaceArchive.readTar(tar, "");
            assertThat(read).containsEntry("solution/A.java", "class A {}\n").doesNotContainKey("solution/leak.txt");
            assertThat(read.values()).doesNotContain("host secret");
        }
    }

    @Test
    void buildWorkspaceTar_rejectsCredentialFilesBeforeProviderAccess(@TempDir Path repo) throws Exception {
        FileUtils.writeStringToFile(repo.resolve(".env").toFile(), "API_TOKEN=secret\n", StandardCharsets.UTF_8);

        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildWorkspaceTarStream(Map.of(), Map.of("solution", repo))).withMessageContaining("credential file")
                .withMessageContaining("solution/.env");
    }

    @Test
    void buildWorkspaceTar_rejectsPrivateKeysRegardlessOfFileName(@TempDir Path repo) throws Exception {
        FileUtils.writeStringToFile(repo.resolve("fixture.txt").toFile(), "-----BEGIN PRIVATE KEY-----\nsecret\n", StandardCharsets.UTF_8);

        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildWorkspaceTarStream(Map.of(), Map.of("tests", repo))).withMessageContaining("credential material")
                .withMessageContaining("tests/fixture.txt");
    }

    @Test
    void buildWorkspaceTar_rejectsSecretMaterialFromLiteralTextInputs() {
        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildWorkspaceTarStream(Map.of("solution/.env.production", "ordinary"), Map.of())).withMessageContaining("CREDENTIAL_FILE")
                .withMessageContaining("solution/.env.production");

        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildWorkspaceTarStream(Map.of("problem-statement.md", GITHUB_SENTINEL), Map.of())).withMessageContaining("GITHUB_TOKEN")
                .withMessageNotContaining(GITHUB_SENTINEL);
    }

    @Test
    void buildFilesTar_rejectsSecretMaterialFromTextAndBinaryInputs() {
        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildFilesTarStream(Map.of("solution/notes.txt", GITHUB_SENTINEL), Map.of(), java.util.Set.of()))
                .withMessageContaining("GITHUB_TOKEN").withMessageNotContaining(GITHUB_SENTINEL);

        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildFilesTarStream(Map.of(), Map.of("solution/keys/server.p12", new byte[] { 0, 1, 2 }), java.util.Set.of()))
                .withMessageContaining("PRIVATE_KEY_CONTAINER").withMessageContaining("solution/keys/server.p12");
    }

    @Test
    void readTar_rejectsGeneratedSecretMaterialBeforeReturningCandidate() throws Exception {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(packTar(Map.of("solution/.ENV.production", "ordinary".getBytes(StandardCharsets.UTF_8))))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, "solution"))
                    .withMessageContaining("CREDENTIAL_FILE").withMessageContaining(".ENV.production");
        }

        try (TarArchiveInputStream tar = new TarArchiveInputStream(packTar(Map.of("solution/fixture.txt", GITHUB_SENTINEL.getBytes(StandardCharsets.UTF_8))))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, "solution"))
                    .withMessageContaining("GITHUB_TOKEN").withMessageNotContaining(GITHUB_SENTINEL);
        }

        try (TarArchiveInputStream tar = new TarArchiveInputStream(packTar(Map.of("solution/keys/server.jks", new byte[] { 0, 1, 2 })))) {
            assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class).isThrownBy(() -> WorkspaceArchive.readTar(tar, "solution"))
                    .withMessageContaining("PRIVATE_KEY_CONTAINER");
        }
    }

    @Test
    void buildWorkspaceTar_scansTheWholeBoundedFileForPrivateKeys(@TempDir Path repo) throws Exception {
        FileUtils.writeStringToFile(repo.resolve("fixture.txt").toFile(), "x".repeat(300_000) + "\n-----BEGIN PRIVATE KEY-----\nsecret\n", StandardCharsets.UTF_8);

        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildWorkspaceTarStream(Map.of(), Map.of("tests", repo))).withMessageContaining("credential material");
    }

    @Test
    void buildWorkspaceTar_rejectsOversizedSeedFileBeforePacking(@TempDir Path repo) throws Exception {
        FileUtils.writeByteArrayToFile(repo.resolve("Huge.java").toFile(), new byte[(int) WorkspaceArchive.MAX_FILE_BYTES + 1]);

        assertThatExceptionOfType(WorkspaceArchive.RejectedWorkspaceEntryException.class)
                .isThrownBy(() -> WorkspaceArchive.buildWorkspaceTarStream(Map.of(), Map.of("solution", repo)));
    }

    @Test
    void readWorkingTreeTextFiles_skipsSymlinksInsteadOfSnapshottingTheirTargets(@TempDir Path repo, @TempDir Path outside) throws Exception {
        Path secret = outside.resolve("secret.txt");
        FileUtils.writeStringToFile(secret.toFile(), "host secret", StandardCharsets.UTF_8);
        Files.createSymbolicLink(repo.resolve("leak.txt"), secret);
        FileUtils.writeStringToFile(repo.resolve("Test.java").toFile(), "class Test {}\n", StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, String> files = (Map<String, String>) ReflectionTestUtils.invokeMethod(GenerationWorkspaceService.class, "readWorkingTreeTextFiles", repo);

        assertThat(files).containsEntry("Test.java", "class Test {}\n").doesNotContainKey("leak.txt");
        assertThat(files.values()).doesNotContain("host secret");
    }

    @Test
    void readWorkingTreeTextFiles_skipsOversizedTextFiles(@TempDir Path repo) throws Exception {
        FileUtils.writeByteArrayToFile(repo.resolve("Huge.java").toFile(), new byte[(int) WorkspaceArchive.MAX_FILE_BYTES + 1]);
        FileUtils.writeStringToFile(repo.resolve("Test.java").toFile(), "class Test {}\n", StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, String> files = (Map<String, String>) ReflectionTestUtils.invokeMethod(GenerationWorkspaceService.class, "readWorkingTreeTextFiles", repo);

        assertThat(files).containsEntry("Test.java", "class Test {}\n").doesNotContainKey("Huge.java");
    }

    private static boolean assertArrayEquals(byte[] expected, byte[] actual) {
        assertThat(actual).containsExactly(expected);
        return true;
    }
}

package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deterministic tests for the content-based binary detector that keeps binaries (e.g. the Gradle wrapper JAR) out of the UTF-8 String pipeline. Detection must be by CONTENT, not
 * extension: a real {@code build.sh}/{@code run.sh} harness script is text and must NOT be classified as binary (the coarse extension list treats {@code .sh} as binary), while an
 * extensionless or wrongly-named binary must still be caught.
 */
class BinaryContentTest {

    @Test
    void detectsBinary_whenContentHasNulByte() {
        byte[] withNul = { 'h', 'i', 0, 'x' };
        assertThat(BinaryContent.isBinary(withNul)).isTrue();
    }

    @Test
    void detectsBinary_whenContentIsNotValidUtf8() {
        // A lone 0xFF / 0x89 is invalid UTF-8 (the PNG/JAR signature bytes), so this is binary even without a NUL.
        byte[] invalidUtf8 = { (byte) 0xFF, (byte) 0x89, 0x50, 0x4E };
        assertThat(BinaryContent.isBinary(invalidUtf8)).isTrue();
    }

    @Test
    void treatsValidUtf8TextAsNonBinary_includingShellScripts() {
        assertThat(BinaryContent.isBinary("#!/bin/sh\necho build\n".getBytes(StandardCharsets.UTF_8))).as("a .sh script is text").isFalse();
        assertThat(BinaryContent.isBinary("public class A {}\n".getBytes(StandardCharsets.UTF_8))).isFalse();
        // Multi-byte UTF-8 (emoji, accented characters) is valid text, not binary.
        assertThat(BinaryContent.isBinary("café — déjà vu ✅\n".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(BinaryContent.isBinary(new byte[0])).isFalse();
    }

    @Test
    void isBinaryFile_sniffsTheFileOnDisk(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("gradle-wrapper.jar");
        FileUtils.writeByteArrayToFile(jar.toFile(), new byte[] { 0x50, 0x4B, 0x03, 0x04, 0, 1, (byte) 0xFF });
        Path script = dir.resolve("build.sh");
        FileUtils.writeStringToFile(script.toFile(), "#!/bin/sh\necho ok\n", StandardCharsets.UTF_8);

        assertThat(BinaryContent.isBinaryFile(jar)).isTrue();
        assertThat(BinaryContent.isBinaryFile(script)).isFalse();
        // A missing file is treated as non-binary (so the caller's normal handling applies), never silently protected.
        assertThat(BinaryContent.isBinaryFile(dir.resolve("does-not-exist"))).isFalse();
    }
}

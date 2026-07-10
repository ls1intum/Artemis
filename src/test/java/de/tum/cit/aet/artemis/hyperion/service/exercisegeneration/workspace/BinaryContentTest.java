package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

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
    void classifiesLargeUtf8TextAsText_whenAMultibyteCharCrossesTheFileSniffBoundary(@TempDir Path dir) throws Exception {
        byte[] emoji = "😀".getBytes(StandardCharsets.UTF_8); // F0 9F 98 80
        Path text = dir.resolve("large.txt");
        FileUtils.writeByteArrayToFile(text.toFile(), textWithMultibyteAt(emoji, 8190, 9000));

        assertThat(BinaryContent.isBinaryFile(text)).isFalse();
    }

    @Test
    void detectsBinaryMarkersAfterTheFormerSniffWindow(@TempDir Path dir) throws Exception {
        byte[] content = new byte[9000];
        Arrays.fill(content, (byte) 'a');
        content[8500] = 0;
        Path file = dir.resolve("late-marker.bin");
        FileUtils.writeByteArrayToFile(file.toFile(), content);

        assertThat(BinaryContent.isBinary(content)).isTrue();
        assertThat(BinaryContent.isBinaryFile(file)).isTrue();
    }

    @Test
    void stillDetectsBinary_whenAnInvalidSequenceSitsInsideTheWindow_notOnlyAtTheBoundary() {
        // The boundary back-off must trim ONLY a truncated tail; an invalid UTF-8 sequence earlier in a >8 KiB file must still mark it binary (no masking of a real defect).
        byte[] content = new byte[9000];
        Arrays.fill(content, (byte) 'a');
        content[100] = (byte) 0xC0;
        content[101] = (byte) 0xC0;
        assertThat(BinaryContent.isBinary(content)).isTrue();
    }

    /** Builds a NUL-free ASCII byte array of {@code totalLength} with {@code multibyte} spliced in at {@code offset}, for sniff-boundary tests. */
    private static byte[] textWithMultibyteAt(byte[] multibyte, int offset, int totalLength) {
        byte[] content = new byte[totalLength];
        Arrays.fill(content, (byte) 'a');
        System.arraycopy(multibyte, 0, content, offset, multibyte.length);
        return content;
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

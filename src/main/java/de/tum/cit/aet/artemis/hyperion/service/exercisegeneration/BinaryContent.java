package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Content-based binary-file detection for the generation extract/persist pipeline.
 * <p>
 * The generation workspace moves repository files as UTF-8 {@code String}s ({@link WorkspaceArchive#readTar} on read-back, {@link GenerationPersistenceService} on commit). That
 * round-trip is LOSSLESS for text but CORRUPTS binaries: decoding arbitrary bytes as UTF-8 substitutes the replacement character {@code U+FFFD} for every invalid sequence, and
 * re-encoding the decoded {@code String} back to UTF-8 then writes those replacement bytes — so a {@code gradle/wrapper/gradle-wrapper.jar} (shipped by Java PLAIN_GRADLE /
 * GRADLE_GRADLE) would be written back mangled and the Gradle build would fail in production. The agent never edits these binaries, so the fix is to keep them out of the String
 * pipeline entirely (excluded on read-back, preserved-from-scaffold on persist) rather than to carry bytes through the whole {@code Map<String, String>} contract.
 * <p>
 * Detection is by CONTENT, not by file extension: an extension allowlist would both miss an extensionless binary and — more dangerously — misclassify a genuinely-textual
 * {@code run.sh}/{@code build.sh} test-harness script (a {@code .sh} is "binary" in Artemis's coarse extension list) as binary and wrongly drop it from the produced tree. The
 * content test is the precise signal: a NUL byte in the leading window (no text encoding Artemis uses embeds NUL) or a byte sequence that is not valid UTF-8.
 */
final class BinaryContent {

    /**
     * The leading window inspected for binary markers. A jar/zip/png reveals itself (NUL bytes, invalid UTF-8) within the first bytes; reading more would not change the verdict.
     */
    private static final int SNIFF_LIMIT = 8192;

    private BinaryContent() {
    }

    /**
     * Whether the given bytes are binary (must not be round-tripped through a UTF-8 {@code String}). True if a NUL byte appears in the leading window, or the leading window is not
     * valid UTF-8.
     *
     * @param bytes the file content (may be the whole file or a leading prefix)
     * @return {@code true} if the content is binary
     */
    static boolean isBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int limit = Math.min(bytes.length, SNIFF_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        // A strict UTF-8 decode of the leading window: a malformed/unmappable sequence throws, which marks the content binary. We decode only the window (not the whole file) so a
        // large text file is cheap to classify; a multi-byte sequence straddling the window boundary is the only edge, and a NUL-free, otherwise-UTF-8 file is text regardless.
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes, 0, limit));
            return false;
        }
        catch (java.nio.charset.CharacterCodingException e) {
            return true;
        }
    }

    /**
     * Whether the file at {@code path} is binary, sniffing only its leading window. Used by the persist orphan-sweep to protect a scaffolded binary (e.g. the Gradle wrapper JAR)
     * that the agent never produced from being deleted as an "orphan". An unreadable file is treated as NON-binary (so the caller's normal handling applies) rather than silently
     * protected.
     *
     * @param path the working-tree file to inspect
     * @return {@code true} if the file's leading window is binary; {@code false} if it is text or could not be read
     */
    static boolean isBinaryFile(Path path) {
        try (var stream = Files.newInputStream(path)) {
            byte[] window = stream.readNBytes(SNIFF_LIMIT);
            return isBinary(window);
        }
        catch (IOException | RuntimeException e) {
            return false;
        }
    }
}

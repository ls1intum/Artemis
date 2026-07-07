package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Content-based binary-file detection for the generation extract/persist pipeline.
 * <p>
 * The generation workspace moves repository files as UTF-8 {@code String}s ({@link WorkspaceArchive#readTar} on read-back, {@code GenerationPersistenceService} on commit). That
 * round-trip is lossless for text but corrupts binaries: decoding arbitrary bytes as UTF-8 substitutes the replacement character {@code U+FFFD} for every invalid sequence, and
 * re-encoding the decoded {@code String} back to UTF-8 writes those replacement bytes — so a {@code gradle/wrapper/gradle-wrapper.jar} (shipped by Java PLAIN_GRADLE /
 * GRADLE_GRADLE) would be written back mangled and the Gradle build would fail. The agent never edits these binaries, so they are kept out of the String pipeline entirely
 * (excluded on read-back, preserved-from-scaffold on persist) rather than carried as bytes through the whole {@code Map<String, String>} contract.
 * <p>
 * Detection is by content, not by file extension: an extension allowlist would both miss an extensionless binary and misclassify a genuinely-textual
 * {@code run.sh}/{@code build.sh}
 * test-harness script (a {@code .sh} is "binary" in Artemis's coarse extension list) as binary and wrongly drop it from the produced tree. The content test is the precise signal:
 * a NUL byte in the leading window (no text encoding Artemis uses embeds NUL) or a byte sequence that is not valid UTF-8.
 */
public final class BinaryContent {

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
        // large text file is cheap to classify. When the window is full the file continues past it, so a multi-byte code point can straddle the SNIFF_LIMIT boundary and leave a
        // TRUNCATED (not malformed) trailing sequence; decoding that as final input would falsely REPORT it as malformed and drop a genuine text file. Trim any such truncated tail
        // to the last complete code-point boundary before the strict decode so only a genuinely-invalid sequence marks the content binary.
        int decodeLength = completeCodePointLength(bytes, limit);
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes, 0, decodeLength));
            return false;
        }
        catch (CharacterCodingException e) {
            return true;
        }
    }

    /**
     * The length of the leading window to decode strictly, backing off a multi-byte UTF-8 sequence truncated by the window boundary. Only relevant when the window is FULL
     * ({@code limit == SNIFF_LIMIT}) because then the file continues past it and the final bytes may be an incomplete-but-valid code point; a partial window from a small whole
     * file
     * is decoded as-is so genuine trailing garbage is still caught. At most three trailing continuation bytes are walked back (a UTF-8 sequence is 4 bytes); a run of continuation
     * bytes with no lead, or an already-complete sequence, is left in place for the strict decoder to judge.
     */
    private static int completeCodePointLength(byte[] bytes, int limit) {
        if (limit < SNIFF_LIMIT) {
            return limit;
        }
        int i = limit - 1;
        int walked = 0;
        while (i >= 0 && (bytes[i] & 0xC0) == 0x80 && walked < 3) {
            i--;
            walked++;
        }
        if (i < 0) {
            return limit;
        }
        int lead = bytes[i] & 0xFF;
        int expected;
        if (lead < 0x80) {
            expected = 1;
        }
        else if ((lead & 0xE0) == 0xC0) {
            expected = 2;
        }
        else if ((lead & 0xF0) == 0xE0) {
            expected = 3;
        }
        else if ((lead & 0xF8) == 0xF0) {
            expected = 4;
        }
        else {
            // Not a valid lead byte (e.g. a stray continuation run) — let the strict decode judge the full window rather than hiding a real defect.
            return limit;
        }
        int have = limit - i;
        // Only a genuinely truncated tail is trimmed; a complete (or over-long) sequence stays so the strict decode validates it.
        return have < expected ? i : limit;
    }

    /**
     * Whether the file at {@code path} is binary, sniffing only its leading window. Used by the persist orphan-sweep to protect a scaffolded binary (e.g. the Gradle wrapper JAR)
     * that the agent never produced from being deleted as an "orphan". An unreadable file is treated as non-binary (so the caller's normal handling applies) rather than silently
     * protected.
     *
     * @param path the working-tree file to inspect
     * @return {@code true} if the file's leading window is binary; {@code false} if it is text or could not be read
     */
    public static boolean isBinaryFile(Path path) {
        try (var stream = Files.newInputStream(path)) {
            byte[] window = stream.readNBytes(SNIFF_LIMIT);
            return isBinary(window);
        }
        catch (IOException | RuntimeException e) {
            return false;
        }
    }
}

package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A whole-file snapshot streamed to the triggering instructor while the agent writes the exercise repositories, so the editor can render a live, auto-following preview without
 * polling the sandbox. The full content is already in memory on the loop node when {@code write_file}/{@code edit_file} run, so no read-back is needed. It carries a fixed
 * {@link #TYPE} discriminator so it can share the per-user websocket topic with the progress events, and is {@link Serializable} because the latest snapshot per file is retained
 * in a distributed Hazelcast map so a reloading client can rehydrate the preview and resume the stream.
 *
 * @param type      the constant {@link #TYPE} discriminator (distinguishes a snapshot from a progress event on the shared topic)
 * @param path      the workspace-relative file path
 * @param repo      the owning repository bucket ({@code solution}, {@code template}, {@code tests} or {@code other})
 * @param action    whether the file was newly created or edited in place ({@code create} or {@code edit})
 * @param content   the whole current file content, capped at {@link #MAX_CONTENT_BYTES} (see {@link #truncated})
 * @param sha256    the SHA-256 hex digest of the full (untruncated) content, so a client can detect change even when the content is truncated
 * @param bytes     the full (untruncated) content size in bytes
 * @param truncated whether {@link #content} was truncated because the file exceeded {@link #MAX_CONTENT_BYTES}
 * @param turn      the agent turn on which the write happened (best-effort telemetry; {@code 0} if unknown)
 * @param timestamp the moment the snapshot was produced
 */
// NON_NULL, not NON_EMPTY: an empty file must still serialize content:"" so a reconnecting client distinguishes an empty file from an absent field and rehydrates the preview.
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A whole-file snapshot streamed to the instructor while the agent writes the exercise repositories, for a live editor preview")
public record ExerciseGenerationFileSnapshotDTO(@Schema(description = "Constant discriminator identifying a file snapshot on the shared topic") String type,
        @Schema(description = "Workspace-relative file path") String path, @Schema(description = "Owning repository bucket: solution, template, tests or other") String repo,
        @Schema(description = "Whether the file was created or edited: create or edit") String action,
        @Schema(description = "The whole current file content (capped)") String content, @Schema(description = "SHA-256 hex digest of the full content") String sha256,
        @Schema(description = "Full content size in bytes") long bytes, @Schema(description = "Whether the content was truncated because it exceeded the cap") boolean truncated,
        @Schema(description = "The agent turn the write happened on") int turn, @Schema(description = "The moment the snapshot was produced") Instant timestamp)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The fixed {@code type} value, so the client can tell a snapshot from a progress event delivered on the same per-user topic. */
    public static final String TYPE = "FILE_SNAPSHOT";

    /** Hard cap on streamed content: files above this are truncated (with {@link #truncated} set) so one huge generated file cannot flood the websocket or the retained map. */
    public static final int MAX_CONTENT_BYTES = 256 * 1024;

    /** The file was created (or fully rewritten via {@code write_file}). */
    public static final String ACTION_CREATE = "create";

    /** The file was edited in place. */
    public static final String ACTION_EDIT = "edit";

    /**
     * Builds a snapshot for a write, classifying the repository bucket from the path, hashing the full content, and truncating the streamed content to {@link #MAX_CONTENT_BYTES}.
     *
     * @param path        the workspace-relative path just written
     * @param action      {@link #ACTION_CREATE} or {@link #ACTION_EDIT}
     * @param fullContent the whole current file content (untruncated)
     * @param turn        the agent turn the write happened on ({@code 0} if unknown)
     * @return the snapshot ready to stream and retain
     */
    public static ExerciseGenerationFileSnapshotDTO of(String path, String action, String fullContent, int turn) {
        byte[] fullBytes = fullContent.getBytes(StandardCharsets.UTF_8);
        boolean truncated = fullBytes.length > MAX_CONTENT_BYTES;
        // Cut on a UTF-8 CODE-POINT boundary at or below the byte cap. Slicing at a fixed byte offset can split a multi-byte code point; decoding that head with the default
        // REPLACE
        // action would substitute a 3-byte U+FFFD for the 1-2 lost bytes, pushing the re-encoded content BACK OVER the byte cap and injecting a character absent from the original.
        // Backing off to the last complete code point keeps the streamed content a valid, faithful prefix strictly within MAX_CONTENT_BYTES.
        String content = truncated ? new String(fullBytes, 0, codePointBoundaryAtOrBelow(fullBytes, MAX_CONTENT_BYTES), StandardCharsets.UTF_8) : fullContent;
        return new ExerciseGenerationFileSnapshotDTO(TYPE, path, repositoryBucket(path), action, content, sha256Hex(fullBytes), fullBytes.length, truncated, turn, Instant.now());
    }

    /**
     * Classifies a workspace-relative path into its repository bucket by its top-level directory.
     *
     * @param path the workspace-relative path
     * @return {@code solution}, {@code template}, {@code tests} or {@code other}
     */
    static String repositoryBucket(String path) {
        if (path.startsWith("solution/")) {
            return "solution";
        }
        if (path.startsWith("template/")) {
            return "template";
        }
        if (path.startsWith("tests/")) {
            return "tests";
        }
        return "other";
    }

    /**
     * The largest offset {@code <= cap} that lands on a UTF-8 code-point boundary in {@code bytes}. Walks back over any trailing continuation bytes ({@code 10xxxxxx}) so the head
     * {@code bytes[0, offset)} is always a complete, self-contained UTF-8 prefix (a code point is at most four bytes, so this backs off at most three).
     *
     * @param bytes the full UTF-8 content
     * @param cap   the byte cap (assumed {@code < bytes.length}; the caller only truncates when the content exceeds the cap)
     * @return the code-point-aligned offset at or below {@code cap}
     */
    private static int codePointBoundaryAtOrBelow(byte[] bytes, int cap) {
        int offset = cap;
        while (offset > 0 && (bytes[offset] & 0xC0) == 0x80) {
            offset--;
        }
        return offset;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        }
        catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated JCA algorithm; unreachable on any supported JVM.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

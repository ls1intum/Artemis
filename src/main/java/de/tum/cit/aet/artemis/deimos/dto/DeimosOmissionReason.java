package de.tum.cit.aet.artemis.deimos.dto;

/**
 * Which limit caused part of a participation's evidence to be left out of the model's payload.
 */
public enum DeimosOmissionReason {

    /**
     * The file exceeds {@code artemis.deimos.payload.max-file-input-bytes} and was not diffed at all.
     */
    FILE_INPUT_LIMIT,

    /**
     * The diff of the file exceeds {@code artemis.deimos.payload.max-file-diff-bytes} and was truncated.
     */
    FILE_DIFF_LIMIT,

    /**
     * The total payload budget ({@code artemis.deimos.payload.max-payload-bytes}, including the part reserved for the
     * final state) was spent, so a file diff was truncated or dropped, or a whole snapshot was dropped.
     */
    PAYLOAD_LIMIT,

    /**
     * The submission carries a blank commit hash, so its snapshot could not be read at all.
     */
    MISSING_COMMIT_HASH
}

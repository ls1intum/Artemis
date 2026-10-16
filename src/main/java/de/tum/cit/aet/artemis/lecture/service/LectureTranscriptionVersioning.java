package de.tum.cit.aet.artemis.lecture.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;

/**
 * Keeps the transcription version on a lecture unit's processing state in step with the transcription content, so a citation pinned to an older
 * version can tell that the video's transcript has changed since.
 */
final class LectureTranscriptionVersioning {

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionVersioning.class);

    private LectureTranscriptionVersioning() {
    }

    /**
     * Increments the unit's transcription version when the completed segments differ from the ones written last.
     * <p>
     * The version is what Iris citations pin a video timestamp to, so it must change exactly when the timestamps do. Comparing the content hash first is what keeps the
     * repeated checkpoint writes of a single transcription run from inflating the version, and keeps a re-run that produces identical segments from invalidating citations
     * that are still perfectly accurate.
     * <p>
     * Only the enriched checkpoint gets here, because only it is the transcription a citation can point at: it is the one saved as COMPLETED. A run first sends the raw
     * segments, which carry the same speech but no slide numbers and are therefore a different content hash — hashing them too would advance the version on the way to a
     * result that may well be identical to the previous one, and mark every citation of that video stale for nothing.
     * <p>
     * The counter deliberately lives on the processing state and not on the transcription: the transcription row is deleted and recreated when the video changes, which
     * would restart the count at 1 and make a citation pinned to version 1 look unchanged.
     *
     * @param state    the processing state of the unit, updated in place; the caller persists it with the guarded transition
     * @param segments the segments about to be stored
     */
    static void bumpTranscriptionVersionIfContentChanged(LectureUnitProcessingState state, List<LectureTranscriptionSegment> segments) {
        String contentHash = hashTranscriptionSegments(segments);
        if (contentHash.equals(state.getTranscriptionContentHash())) {
            return;
        }
        Integer previousVersion = state.getTranscriptionVersion();
        state.setTranscriptionVersion(previousVersion == null ? 1 : previousVersion + 1);
        state.setTranscriptionContentHash(contentHash);
        log.info("Transcription content changed for unit {}, transcription version is now {}", state.getLectureUnit().getId(), state.getTranscriptionVersion());
    }

    /**
     * Fingerprints the content of a transcription, the counterpart of {@code Attachment#sha256Hash} for a PDF.
     * <p>
     * The segments are serialized into a canonical string rather than reusing their JSON representation, so that the hash depends only on the transcribed content and not
     * on how the JSON converter happens to format it.
     * <p>
     * The transcript is the one field that can hold anything a speaker said, delimiters and line breaks included, so it is written with its length in front of it instead of
     * being separated by a character it may itself contain. Without that, two different transcriptions could serialize to the same string — for instance one segment whose
     * text spells out the delimiters of a second one — and an identical hash would leave the version untouched for material that did change, which is precisely the case
     * this version exists to catch.
     *
     * @param segments the transcription segments; may be {@code null} or empty
     * @return the hex-encoded SHA-256 hash, or an empty string when there are no segments
     */
    private static String hashTranscriptionSegments(@Nullable List<LectureTranscriptionSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        var canonical = new StringBuilder();
        for (LectureTranscriptionSegment segment : segments) {
            String text = segment.text() == null ? "" : segment.text();
            canonical.append(segment.startTime()).append('|').append(segment.endTime()).append('|').append(segment.slideNumber()).append('|').append(text.length()).append('|')
                    .append(text).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

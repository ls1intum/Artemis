package de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a single transcription segment as received from Pyris.
 * Mapped to {@link de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment} inside the status-update service.
 *
 * @param startTime   segment start time in seconds
 * @param endTime     segment end time in seconds
 * @param text        transcribed text for this segment
 * @param slideNumber slide number this segment is aligned to (-1 if unknown)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTranscriptionSegmentDTO(Double startTime, Double endTime, String text, int slideNumber) {
}

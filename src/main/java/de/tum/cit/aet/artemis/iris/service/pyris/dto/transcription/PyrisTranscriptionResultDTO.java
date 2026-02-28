package de.tum.cit.aet.artemis.iris.service.pyris.dto.transcription;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;

/**
 * DTO for parsing the transcription result JSON from Pyris.
 * This represents the final output when transcription is complete.
 *
 * @param lectureUnitId The lecture unit ID that was transcribed
 * @param language      The detected language code (e.g., "en", "de")
 * @param segments      List of transcription segments with timing and slide info
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTranscriptionResultDTO(Long lectureUnitId, String language, List<LectureTranscriptionSegment> segments) {
}

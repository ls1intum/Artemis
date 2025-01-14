package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TranscriptionSegmentDTO(Double startTime, Double endTime, String text, int slideNumber, Long lectureUnitId) {
}

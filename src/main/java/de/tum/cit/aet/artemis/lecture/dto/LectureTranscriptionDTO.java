package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureTranscriptionDTO(Long lectureUnitId, String language, List<LectureTranscriptionSegmentDTO> segments) {
}

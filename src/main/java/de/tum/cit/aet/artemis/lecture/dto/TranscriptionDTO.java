package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TranscriptionDTO(Long lectureId, String language, List<TranscriptionSegmentDTO> segments) {
}

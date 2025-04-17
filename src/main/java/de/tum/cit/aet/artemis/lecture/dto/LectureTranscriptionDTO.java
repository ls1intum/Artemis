package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureTranscriptionDTO(Long lectureUnitId, String language, List<LectureTranscriptionSegment> segments) {
}

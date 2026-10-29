package de.tum.cit.aet.artemis.lecture.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Released non-exercise units and the requesting user's completions, grouped without loading material payloads. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureLearningStatisticsDTO(long lectureId, String lectureTitle, String type, long total, long completed) {
}

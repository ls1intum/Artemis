package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitStudentMetricsDTO(Map<Long, LectureUnitInformationDTO> lectureUnitInformation) {
}

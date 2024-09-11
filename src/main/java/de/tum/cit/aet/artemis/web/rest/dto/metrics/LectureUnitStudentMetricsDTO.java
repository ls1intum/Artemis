package de.tum.cit.aet.artemis.web.rest.dto.metrics;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for lecture unit student metrics.
 *
 * @param lectureUnitInformation the information about the lecture units
 * @param completed              the ids of the completed lecture units
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitStudentMetricsDTO(Map<Long, LectureUnitInformationDTO> lectureUnitInformation, Set<Long> completed) {
}

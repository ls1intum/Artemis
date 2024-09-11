package de.tum.cit.aet.artemis.web.rest.dto.metrics;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyJolDTO;

/**
 * A DTO representing the metrics for a student regarding competencies.
 *
 * @param competencyInformation a map of competency ids to the information about the competency
 * @param exercises             a map of competency ids to the ids of the exercises related to the competency
 * @param lectureUnits          a map of competency ids to the ids of the lecture units related to the competency
 * @param progress              a map of competency ids to the progress of the student regarding the competency
 * @param confidence            a map of competency ids to the confidence of the student regarding the competency
 * @param currentJolValues      a map of competency ids to the most recent judgement of learning values of the student regarding the competency
 * @param priorJolValues        a map of competency ids to the judgement of learning values prior to the most recent one of the student regarding the competency
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyStudentMetricsDTO(Map<Long, CompetencyInformationDTO> competencyInformation, Map<Long, Set<Long>> exercises, Map<Long, Set<Long>> lectureUnits,
        Map<Long, Double> progress, Map<Long, Double> confidence, Map<Long, CompetencyJolDTO> currentJolValues, Map<Long, CompetencyJolDTO> priorJolValues) {
}

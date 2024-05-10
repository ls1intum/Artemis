package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exercise student metrics.
 *
 * @param exerciseInformation     the information about the exercises
 * @param averageScore            the average score of the students in the exercises
 * @param averageLatestSubmission the average relative time of the latest submissions in the exercises
 * @param latestSubmission        the relative time of the latest submission of the students in the exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseStudentMetricsDTO(Map<Long, ExerciseInformationDTO> exerciseInformation, Map<Long, Double> averageScore, Map<Long, Double> averageLatestSubmission,
        Map<Long, Double> latestSubmission) {
}

package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exercise student metrics.
 *
 * @param exerciseInformation the exercise information
 * @param latestSubmission    the latest submission per exercise
 * @param exerciseStart       the time when the student started the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseStudentMetricsDTO(Map<Long, ExerciseInformationDTO> exerciseInformation, Map<Long, Double> averageScore, Map<Long, Double> averageLatestSubmission,
        Map<Long, ZonedDateTime> latestSubmission, Map<Long, ZonedDateTime> exerciseStart, Map<Long, Set<SubmissionTimestampDTO>> submissionTimestamps) {
}

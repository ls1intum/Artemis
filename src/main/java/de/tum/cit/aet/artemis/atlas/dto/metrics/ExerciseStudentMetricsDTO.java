package de.tum.cit.aet.artemis.atlas.dto.metrics;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseInformationDTO;

/**
 * DTO for exercise student metrics.
 * <p>
 * Note: When updating this class, make sure to also update Pyris.
 *
 * @param exerciseInformation     the information about the exercises
 * @param categories              the categories of the exercises
 * @param averageScore            the average score of the students in the exercises
 * @param score                   the score of the student in the exercises
 * @param averageLatestSubmission the average relative time of the latest submissions in the exercises
 * @param latestSubmission        the relative time of the latest submission of the students in the exercises
 * @param completed               the ids of the completed exercises
 * @param teamId                  the id of the team the student is in
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseStudentMetricsDTO(Map<Long, ExerciseInformationDTO> exerciseInformation, Map<Long, Set<String>> categories, Map<Long, Double> averageScore,
                                        Map<Long, Double> score, Map<Long, Double> averageLatestSubmission, Map<Long, Double> latestSubmission, Set<Long> completed, Map<Long, Long> teamId) {
}

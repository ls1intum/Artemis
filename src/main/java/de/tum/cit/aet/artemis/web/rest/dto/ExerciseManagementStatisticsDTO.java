package de.tum.cit.aet.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseManagementStatisticsDTO(double averageScoreOfExercise, double maxPointsOfExercise, int[] scoreDistribution, int numberOfExerciseScores,
        long numberOfParticipations, long numberOfStudentsOrTeamsInCourse, long numberOfPosts, long numberOfResolvedPosts) {
}

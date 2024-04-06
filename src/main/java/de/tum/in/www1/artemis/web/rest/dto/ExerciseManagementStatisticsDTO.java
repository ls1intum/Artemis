package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing the management statistics of an exercise.
 *
 * @param averageScoreOfExercise          The average score of the exercise
 * @param maxPointsOfExercise             The max points of the exercise
 * @param scoreDistribution               The distribution of scores
 * @param numberOfExerciseScores          The number of exercise scores
 * @param numberOfParticipations          The number of participations
 * @param numberOfStudentsOrTeamsInCourse The number of students or teams in the course
 * @param numberOfPosts                   The number of posts
 * @param numberOfResolvedPosts           The number of resolved posts
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseManagementStatisticsDTO(double averageScoreOfExercise, double maxPointsOfExercise, int[] scoreDistribution, int numberOfExerciseScores,
        long numberOfParticipations, long numberOfStudentsOrTeamsInCourse, long numberOfPosts, long numberOfResolvedPosts) {
}

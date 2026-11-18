package de.tum.cit.aet.artemis.exercise.dto;

import java.util.Arrays;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseManagementStatisticsDTO(double averageScoreOfExercise, double maxPointsOfExercise, int[] scoreDistribution, int numberOfExerciseScores,
        long numberOfParticipations, long numberOfStudentsOrTeamsInCourse, long numberOfPosts, long numberOfResolvedPosts) {

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExerciseManagementStatisticsDTO that)) {
            return false;
        }
        return Double.compare(averageScoreOfExercise, that.averageScoreOfExercise) == 0 && Double.compare(maxPointsOfExercise, that.maxPointsOfExercise) == 0
                && Arrays.equals(scoreDistribution, that.scoreDistribution) && numberOfExerciseScores == that.numberOfExerciseScores
                && numberOfParticipations == that.numberOfParticipations && numberOfStudentsOrTeamsInCourse == that.numberOfStudentsOrTeamsInCourse
                && numberOfPosts == that.numberOfPosts && numberOfResolvedPosts == that.numberOfResolvedPosts;
    }

    @Override
    public int hashCode() {
        return Objects.hash(averageScoreOfExercise, maxPointsOfExercise, Arrays.hashCode(scoreDistribution), numberOfExerciseScores, numberOfParticipations,
                numberOfStudentsOrTeamsInCourse, numberOfPosts, numberOfResolvedPosts);
    }

    @Override
    public String toString() {
        return "ExerciseManagementStatisticsDTO[averageScoreOfExercise=" + averageScoreOfExercise + ", maxPointsOfExercise=" + maxPointsOfExercise + ", scoreDistribution="
                + Arrays.toString(scoreDistribution) + ", numberOfExerciseScores=" + numberOfExerciseScores + ", numberOfParticipations=" + numberOfParticipations
                + ", numberOfStudentsOrTeamsInCourse=" + numberOfStudentsOrTeamsInCourse + ", numberOfPosts=" + numberOfPosts + ", numberOfResolvedPosts=" + numberOfResolvedPosts
                + "]";
    }
}

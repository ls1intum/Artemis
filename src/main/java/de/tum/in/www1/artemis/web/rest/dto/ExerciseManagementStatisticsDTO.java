package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseManagementStatisticsDTO {

    private double averageScoreOfExercise;

    private double maxPointsOfExercise;

    private int[] scoreDistribution;

    private int numberOfExerciseScores;

    private long numberOfParticipations;

    private long numberOfStudentsOrTeamsInCourse;

    private long numberOfPosts;

    private long numberOfResolvedPosts;

    public double getAverageScoreOfExercise() {
        return averageScoreOfExercise;
    }

    public void setAverageScoreOfExercise(double averageScoreOfExercise) {
        this.averageScoreOfExercise = averageScoreOfExercise;
    }

    public int[] getScoreDistribution() {
        return scoreDistribution;
    }

    public void setScoreDistribution(int[] scoreDistribution) {
        this.scoreDistribution = scoreDistribution;
    }

    public int getNumberOfExerciseScores() {
        return numberOfExerciseScores;
    }

    public void setNumberOfExerciseScores(int numberOfExerciseScores) {
        this.numberOfExerciseScores = numberOfExerciseScores;
    }

    public long getNumberOfParticipations() {
        return numberOfParticipations;
    }

    public void setNumberOfParticipations(long numberOfParticipations) {
        this.numberOfParticipations = numberOfParticipations;
    }

    public long getNumberOfPosts() {
        return numberOfPosts;
    }

    public void setNumberOfPosts(long numberOfPosts) {
        this.numberOfPosts = numberOfPosts;
    }

    public long getNumberOfResolvedPosts() {
        return numberOfResolvedPosts;
    }

    public void setNumberOfResolvedPosts(long numberOfResolvedPosts) {
        this.numberOfResolvedPosts = numberOfResolvedPosts;
    }

    public double getMaxPointsOfExercise() {
        return maxPointsOfExercise;
    }

    public void setMaxPointsOfExercise(double maxPointsOfExercise) {
        this.maxPointsOfExercise = maxPointsOfExercise;
    }

    public long getNumberOfStudentsOrTeamsInCourse() {
        return numberOfStudentsOrTeamsInCourse;
    }

    public void setNumberOfStudentsOrTeamsInCourse(long numberOfStudentsOrTeamsInCourse) {
        this.numberOfStudentsOrTeamsInCourse = numberOfStudentsOrTeamsInCourse;
    }
}

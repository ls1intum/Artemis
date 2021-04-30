package de.tum.in.www1.artemis.web.rest.dto;

public class ExerciseManagementStatisticsDTO {

    private Double averageScoreOfExercise;

    private int[] scoreDistribution;

    public Double getAverageScoreOfExercise() {
        return averageScoreOfExercise;
    }

    public void setAverageScoreOfExercise(Double averageScoreOfExercise) {
        this.averageScoreOfExercise = averageScoreOfExercise;
    }

    public int[] getScoreDistribution() {
        return scoreDistribution;
    }

    public void setScoreDistribution(int[] scoreDistribution) {
        this.scoreDistribution = scoreDistribution;
    }
}

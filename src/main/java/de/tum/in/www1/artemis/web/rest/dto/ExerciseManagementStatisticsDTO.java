package de.tum.in.www1.artemis.web.rest.dto;

public class ExerciseManagementStatisticsDTO {

    private double averageScoreOfExercise;

    private int[] scoreDistribution;

    private int numberOfExerciseScores;

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
}

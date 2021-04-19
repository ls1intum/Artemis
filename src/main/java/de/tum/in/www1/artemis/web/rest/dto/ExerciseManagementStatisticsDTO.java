package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

public class ExerciseManagementStatisticsDTO {

    private Double averageScoreOfExercise;

    private List<Double> scoreDistribution;

    public Double getAverageScoreOfExercise() {
        return averageScoreOfExercise;
    }

    public void setAverageScoreOfExercise(Double averageScoreOfExercise) {
        this.averageScoreOfExercise = averageScoreOfExercise;
    }

    public List<Double> getScoreDistribution() {
        return scoreDistribution;
    }

    public void setScoreDistribution(List<Double> scoreDistribution) {
        this.scoreDistribution = scoreDistribution;
    }
}

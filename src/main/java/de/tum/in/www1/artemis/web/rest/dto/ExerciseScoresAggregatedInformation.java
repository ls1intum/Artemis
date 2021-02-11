package de.tum.in.www1.artemis.web.rest.dto;

public class ExerciseScoresAggregatedInformation {

    public Long exerciseId;

    public Double averageScoreAchieved;

    public Long maxScoreAchieved;

    public ExerciseScoresAggregatedInformation(Long exerciseId, Double averageScoreAchieved, Long maxScoreAchieved) {
        this.exerciseId = exerciseId;
        this.averageScoreAchieved = averageScoreAchieved;
        this.maxScoreAchieved = maxScoreAchieved;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public Double getAverageScoreAchieved() {
        return averageScoreAchieved;
    }

    public Long getMaxScoreAchieved() {
        return maxScoreAchieved;
    }
}

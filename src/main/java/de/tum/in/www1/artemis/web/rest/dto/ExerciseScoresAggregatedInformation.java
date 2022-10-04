package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This class ist a container for aggregate information about the achieved scores in exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseScoresAggregatedInformation {

    private Long exerciseId;

    private Double averageScoreAchieved;

    private Double maxScoreAchieved;

    public ExerciseScoresAggregatedInformation(Long exerciseId, Double averageScoreAchieved, Double maxScoreAchieved) {
        this.exerciseId = exerciseId;
        this.averageScoreAchieved = averageScoreAchieved;
        this.maxScoreAchieved = maxScoreAchieved;
    }

    public ExerciseScoresAggregatedInformation() {
        // empty constructor for jackson
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public Double getAverageScoreAchieved() {
        return averageScoreAchieved;
    }

    public Double getMaxScoreAchieved() {
        return maxScoreAchieved;
    }
}

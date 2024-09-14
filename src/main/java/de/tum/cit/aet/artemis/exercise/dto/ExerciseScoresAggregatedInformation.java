package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This class ist a container for aggregate information about the achieved scores in exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseScoresAggregatedInformation(Long exerciseId, Double averageScoreAchieved, Double maxScoreAchieved) {

}

package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This class ist a container for aggregate information about the achieved scores in exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseScoresAggregatedInformation(Long exerciseId, Double averageScoreAchieved, Double maxScoreAchieved) {

}

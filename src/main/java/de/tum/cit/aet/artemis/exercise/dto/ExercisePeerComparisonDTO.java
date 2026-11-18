package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** No peer identifiers or individual peer results leave the application server. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExercisePeerComparisonDTO(List<ExercisePeerScoreDTO> exercises, int groupCount, int peerCount) {
}

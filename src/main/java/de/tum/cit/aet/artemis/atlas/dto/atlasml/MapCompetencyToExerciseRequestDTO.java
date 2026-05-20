package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for mapping a competency to an exercise in AtlasML.
 * Updates the exercise's competency list in the vector store.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MapCompetencyToExerciseRequestDTO(@JsonProperty("exercise_id") @NotNull Long exerciseId, @JsonProperty("competency_id") @NotNull Long competencyId) {
}

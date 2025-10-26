package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * DTO for Atlas Agent tool responses representing exercise information.
 * All fields are always included in JSON to ensure consistent LLM responses.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AtlasAgentExerciseDTO(@NotNull Long id, @NotNull String title, @NotNull String type, @NotNull Double maxPoints, @NotNull String releaseDate,
        @NotNull String dueDate) {

    /**
     * Creates an AtlasAgentExerciseDTO from an Exercise entity.
     *
     * @param exercise the exercise to convert
     * @return the DTO representation
     */
    public static AtlasAgentExerciseDTO of(@NotNull Exercise exercise) {
        return new AtlasAgentExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getClass().getSimpleName(),
                exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0.0, exercise.getReleaseDate() != null ? exercise.getReleaseDate().toString() : "",
                exercise.getDueDate() != null ? exercise.getDueDate().toString() : "");
    }
}

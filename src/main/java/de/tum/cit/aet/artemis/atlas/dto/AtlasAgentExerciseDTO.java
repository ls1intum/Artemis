package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * DTO for Atlas Agent tool responses representing exercise information.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentExerciseDTO(Long id, String title, String type, Double maxPoints, String releaseDate, String dueDate) {

    /**
     * Creates an AtlasAgentExerciseDTO from an Exercise entity.
     *
     * @param exercise the exercise to convert
     * @return the DTO representation
     */
    public static AtlasAgentExerciseDTO of(Exercise exercise) {
        return new AtlasAgentExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getClass().getSimpleName(),
                exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0.0, exercise.getReleaseDate() != null ? exercise.getReleaseDate().toString() : "",
                exercise.getDueDate() != null ? exercise.getDueDate().toString() : "");
    }
}

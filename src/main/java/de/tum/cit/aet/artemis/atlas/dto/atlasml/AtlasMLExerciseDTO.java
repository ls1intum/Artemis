package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for AtlasML API communication representing an exercise with competencies.
 * This matches the Python ExerciseWithCompetencies model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasMLExerciseDTO(@NotNull Long id, @NotNull String title, @Nullable String description, @Nullable List<Long> competencies,
        @JsonProperty("course_id") @NotNull Long courseId) {
}

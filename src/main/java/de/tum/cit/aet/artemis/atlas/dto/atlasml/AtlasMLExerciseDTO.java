package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for AtlasML API communication representing an exercise with competencies.
 * This matches the Python ExerciseWithCompetencies model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasMLExerciseDTO(Long id, String title, String description, List<Long> competencies, @JsonProperty("course_id") Long courseId) {
}

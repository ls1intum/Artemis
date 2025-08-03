package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for AtlasML API communication representing an exercise with competencies.
 * This matches the Python ExerciseWithCompetencies model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AtlasMLExerciseDTO(@JsonProperty("id") String id, @JsonProperty("title") String title, @JsonProperty("description") String description,
        @JsonProperty("competencies") List<String> competencies, @JsonProperty("course_id") String courseId) {
}

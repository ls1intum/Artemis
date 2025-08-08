package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;

/**
 * DTO for saving competencies request to AtlasML.
 * Maps to the Python SaveCompetencyRequest model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaveCompetencyRequestDTO(@JsonProperty("competency") AtlasMLCompetencyDTO competency, @JsonProperty("exercise") AtlasMLExerciseDTO exercise,
        @JsonProperty("operation_type") OperationType operationType) {

    /**
     * Operation type enum for AtlasML save operations.
     */
    public enum OperationType {
        UPDATE, DELETE
    }

    /**
     * Create a SaveCompetencyRequestDTO from domain objects for competency saving.
     */
    public static SaveCompetencyRequestDTO fromCompetency(Competency competency, OperationType operationType) {
        AtlasMLCompetencyDTO atlasMLCompetency = competency != null ? AtlasMLCompetencyDTO.fromDomain(competency) : null;
        return new SaveCompetencyRequestDTO(atlasMLCompetency, null, operationType);
    }

    /**
     * Create a SaveCompetencyRequestDTO from domain objects for exercise saving.
     */
    public static SaveCompetencyRequestDTO fromExercise(String exerciseId, String title, String description, List<String> competencyIds, String courseId,
            OperationType operationType) {
        AtlasMLExerciseDTO atlasMLExercise = new AtlasMLExerciseDTO(exerciseId, title, description, competencyIds, courseId);
        return new SaveCompetencyRequestDTO(null, atlasMLExercise, operationType);
    }
}

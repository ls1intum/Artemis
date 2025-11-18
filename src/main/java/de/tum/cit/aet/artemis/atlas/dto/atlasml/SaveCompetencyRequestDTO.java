package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;

/**
 * DTO for saving competencies request to AtlasML.
 * Maps to the Python SaveCompetencyRequest model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SaveCompetencyRequestDTO(@Nullable List<AtlasMLCompetencyDTO> competencies, @Nullable AtlasMLExerciseDTO exercise,
        @JsonProperty("operation_type") @NotEmpty String operationType) {

    /**
     * Operation type DTO for AtlasML save operations.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record OperationTypeDTO(@NotEmpty String value) {

        public static final OperationTypeDTO UPDATE = new OperationTypeDTO("UPDATE");

        public static final OperationTypeDTO DELETE = new OperationTypeDTO("DELETE");
    }

    /**
     * Create a SaveCompetencyRequestDTO from domain objects for competency saving.
     */
    public static SaveCompetencyRequestDTO fromCompetency(@Nullable Competency competency, @NotNull OperationTypeDTO operationType) {
        List<AtlasMLCompetencyDTO> competencies = competency != null ? List.of(AtlasMLCompetencyDTO.fromDomain(competency)) : null;
        return new SaveCompetencyRequestDTO(competencies, null, operationType.value());
    }

    /**
     * Create a SaveCompetencyRequestDTO from multiple competencies for batch saving.
     */
    public static SaveCompetencyRequestDTO fromCompetencies(@Nullable List<Competency> competencies, @NotNull OperationTypeDTO operationType) {
        List<AtlasMLCompetencyDTO> atlasMLCompetencies = competencies != null && !competencies.isEmpty() ? competencies.stream().map(AtlasMLCompetencyDTO::fromDomain).toList()
                : null;
        return new SaveCompetencyRequestDTO(atlasMLCompetencies, null, operationType.value());
    }

    /**
     * Create a SaveCompetencyRequestDTO from domain objects for exercise saving.
     */
    public static SaveCompetencyRequestDTO fromExercise(@Nullable Long exerciseId, @Nullable String title, @Nullable String description, @Nullable List<Long> competencyIds,
            @Nullable Long courseId, @NotNull OperationTypeDTO operationType) {
        AtlasMLExerciseDTO atlasMLExercise = new AtlasMLExerciseDTO(exerciseId, title, description, competencyIds, courseId);
        return new SaveCompetencyRequestDTO(null, atlasMLExercise, operationType.value());
    }
}

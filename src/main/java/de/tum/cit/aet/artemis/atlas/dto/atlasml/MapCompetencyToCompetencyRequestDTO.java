package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for mapping a competency to another competency in AtlasML.
 * This creates a bidirectional relationship between two competencies.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MapCompetencyToCompetencyRequestDTO(@JsonProperty("source_competency_id") @NotNull Long sourceCompetencyId,
        @JsonProperty("target_competency_id") @NotNull Long targetCompetencyId) {
}

package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;

/**
 * DTO for competency suggestions response from AtlasML.
 * Maps to the Python SuggestCompetencyResponse model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuggestCompetencyResponseDTO(@JsonProperty("competencies") List<AtlasMLCompetencyDTO> competencies) {

    /**
     * Convert the AtlasML competencies to domain objects.
     * Note: This creates basic competencies without all the domain-specific fields.
     */
    public List<Competency> toDomainCompetencies() {
        if (competencies == null) {
            return List.of();
        }
        return competencies.stream().map(AtlasMLCompetencyDTO::toDomain).toList();
    }
}

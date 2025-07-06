package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;

/**
 * DTO for competency suggestions response from AtlasML.
 * Maps to the Python SuggestCompetencyResponse model.
 * Note: competencies is now a list of ids
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SuggestCompetencyResponseDTO(@JsonProperty("competencies") List<String> competencies,
        @JsonProperty("competency_relations") List<AtlasMLCompetencyRelationDTO> competencyRelations) {

    /**
     * Convert the AtlasML response to domain relation objects.
     * Note: This creates basic relations without full competency objects.
     */
    public List<CompetencyRelation> toDomainCompetencyRelations() {
        if (competencyRelations == null) {
            return List.of();
        }
        return competencyRelations.stream().map(AtlasMLCompetencyRelationDTO::toDomain).toList();
    }
}

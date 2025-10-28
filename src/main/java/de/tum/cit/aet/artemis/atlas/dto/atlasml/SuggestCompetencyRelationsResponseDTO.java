package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;

/**
 * DTO for competency relation suggestions response from AtlasML.
 * Maps to the Python CompetencyRelationSuggestionResponse model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SuggestCompetencyRelationsResponseDTO(List<AtlasMLCompetencyRelationDTO> relations) {

    /**
     * Convert the AtlasML competency relations to domain objects.
     * Note: Returned relations will only have type set, tail/head must be resolved by caller if needed.
     */
    public List<CompetencyRelation> toDomainRelations() {
        if (relations == null) {
            return List.of();
        }
        return relations.stream().map(AtlasMLCompetencyRelationDTO::toDomain).toList();
    }
}

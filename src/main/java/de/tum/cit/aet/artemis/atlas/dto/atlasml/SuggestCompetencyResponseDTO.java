package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;

/**
 * DTO for competency suggestions response from AtlasML.
 * Maps to the Python SuggestCompetencyResponse model.
 * Note: competencies is now a list of ids
 */
public class SuggestCompetencyResponseDTO {

    @JsonProperty("competencies")
    private List<String> competencies;

    @JsonProperty("competency_relations")
    private List<AtlasMLCompetencyRelationDTO> competencyRelations;

    // Default constructor for Jackson
    public SuggestCompetencyResponseDTO() {
    }

    public SuggestCompetencyResponseDTO(List<String> competencies, List<AtlasMLCompetencyRelationDTO> competencyRelations) {
        this.competencies = competencies;
        this.competencyRelations = competencyRelations;
    }

    public List<String> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(List<String> competencies) {
        this.competencies = competencies;
    }

    public List<AtlasMLCompetencyRelationDTO> getCompetencyRelations() {
        return competencyRelations;
    }

    public void setCompetencyRelations(List<AtlasMLCompetencyRelationDTO> competencyRelations) {
        this.competencyRelations = competencyRelations;
    }

    /**
     * Convert the AtlasML response to domain relation objects.
     * Note: This creates basic relations without full competency objects.
     */
    public List<CompetencyRelation> toDomainCompetencyRelations() {
        if (competencyRelations == null) {
            return List.of();
        }
        return competencyRelations.stream().map(AtlasMLCompetencyRelationDTO::toDomain).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "SuggestCompetencyResponseDTO{" + "competencies=" + competencies + ", competencyRelations=" + competencyRelations + '}';
    }
}

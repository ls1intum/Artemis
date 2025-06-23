package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;

/**
 * DTO for saving competencies request to AtlasML.
 * Maps to the Python SaveCompetencyRequest model.
 */
public class SaveCompetencyRequestDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("description")
    private String description;

    @JsonProperty("competencies")
    private List<AtlasMLCompetencyDTO> competencies;

    @JsonProperty("competency_relations")
    private List<AtlasMLCompetencyRelationDTO> competencyRelations;

    // Default constructor for Jackson
    public SaveCompetencyRequestDTO() {
    }

    public SaveCompetencyRequestDTO(String id, String description, List<AtlasMLCompetencyDTO> competencies, List<AtlasMLCompetencyRelationDTO> competencyRelations) {
        this.id = id;
        this.description = description;
        this.competencies = competencies;
        this.competencyRelations = competencyRelations;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<AtlasMLCompetencyDTO> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(List<AtlasMLCompetencyDTO> competencies) {
        this.competencies = competencies;
    }

    public List<AtlasMLCompetencyRelationDTO> getCompetencyRelations() {
        return competencyRelations;
    }

    public void setCompetencyRelations(List<AtlasMLCompetencyRelationDTO> competencyRelations) {
        this.competencyRelations = competencyRelations;
    }

    /**
     * Create a SaveCompetencyRequestDTO from domain objects.
     */
    public static SaveCompetencyRequestDTO fromDomain(String id, String description, List<Competency> competencies, List<CompetencyRelation> competencyRelations) {
        List<AtlasMLCompetencyDTO> atlasMLCompetencies = competencies != null ? competencies.stream().map(AtlasMLCompetencyDTO::fromDomain).collect(Collectors.toList())
                : List.of();

        List<AtlasMLCompetencyRelationDTO> atlasMLRelations = competencyRelations != null
                ? competencyRelations.stream().map(AtlasMLCompetencyRelationDTO::fromDomain).collect(Collectors.toList())
                : List.of();

        return new SaveCompetencyRequestDTO(id, description, atlasMLCompetencies, atlasMLRelations);
    }

    @Override
    public String toString() {
        return "SaveCompetencyRequestDTO{" + "id='" + id + '\'' + ", description='" + description + '\'' + ", competencies=" + competencies + ", competencyRelations="
                + competencyRelations + '}';
    }
}

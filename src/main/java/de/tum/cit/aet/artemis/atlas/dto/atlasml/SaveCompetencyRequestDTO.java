package de.tum.cit.aet.artemis.atlas.dto.atlasml;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;

/**
 * DTO for saving competencies request to AtlasML.
 * Maps to the Python SaveCompetencyRequest model.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SaveCompetencyRequestDTO(@JsonProperty("id") String id, @JsonProperty("description") String description,
        @JsonProperty("competencies") List<AtlasMLCompetencyDTO> competencies, @JsonProperty("competency_relations") List<AtlasMLCompetencyRelationDTO> competencyRelations) {

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
}

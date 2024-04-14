package de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;

/**
 * DTO containing {@link StandardizedCompetency} data. It only contains the id of the knowledge area and source.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record StandardizedCompetencyDTO(@JsonProperty(access = JsonProperty.Access.READ_ONLY) Long id,
        @NotNull @Size(min = 1, max = StandardizedCompetency.MAX_TITLE_LENGTH) String title, @Size(max = StandardizedCompetency.MAX_DESCRIPTION_LENGTH) String description,
        CompetencyTaxonomy taxonomy, String version, @NotNull Long knowledgeAreaId, Long sourceId) {

    /**
     * Creates a StandardizedCompetencyDTO from the given StandardizedCompetency
     *
     * @param competency the StandardizedCompetency
     * @return the created StandardizedCompetencyDTO
     */
    public static StandardizedCompetencyDTO of(StandardizedCompetency competency) {
        Long sourceId = competency.getSource() == null ? null : competency.getSource().getId();
        Long knowledgeAreaId = competency.getKnowledgeArea() == null ? null : competency.getKnowledgeArea().getId();

        return new StandardizedCompetencyDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getVersion(),
                knowledgeAreaId, sourceId);
    }
}

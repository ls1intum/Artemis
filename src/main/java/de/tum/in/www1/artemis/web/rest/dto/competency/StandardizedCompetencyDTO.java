package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;

/**
 * DTO containing {@link StandardizedCompetency} data. It only contains the id of the knowledge area and source.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StandardizedCompetencyDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, String version, Long knowledgeAreaId, Long sourceId) {

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

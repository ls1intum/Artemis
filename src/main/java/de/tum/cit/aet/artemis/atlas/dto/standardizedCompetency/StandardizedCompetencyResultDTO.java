package de.tum.cit.aet.artemis.atlas.dto.standardizedCompetency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;

/**
 * DTO containing {@link StandardizedCompetency} data. It only contains the id of the knowledge area and source.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StandardizedCompetencyResultDTO(Long id, String title, String description, CompetencyTaxonomy taxonomy, String version, Long knowledgeAreaId, Long sourceId) {

    /**
     * Creates a StandardizedCompetencyResultDTO from the given StandardizedCompetency
     *
     * @param competency the StandardizedCompetency
     * @return the created StandardizedCompetencyResultDTO
     */
    public static StandardizedCompetencyResultDTO of(StandardizedCompetency competency) {
        Long sourceId = competency.getSource() == null ? null : competency.getSource().getId();
        Long knowledgeAreaId = competency.getKnowledgeArea() == null ? null : competency.getKnowledgeArea().getId();

        return new StandardizedCompetencyResultDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getVersion(),
                knowledgeAreaId, sourceId);
    }
}

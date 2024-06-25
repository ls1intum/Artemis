package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyImportResponseDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, ZonedDateTime softDueDate, Integer masteryThreshold,
        boolean optional, Long courseId, Long linkedStandardizedCompetencyId) {

    /**
     * Creates a CompetencyImportResponseDTO from the given Competency
     *
     * @param competency the Competency
     * @return the created CompetencyImportResponseDTO
     */
    public static CompetencyImportResponseDTO of(Competency competency) {
        Long courseId = competency.getCourse() == null ? null : competency.getCourse().getId();
        Long linkedStandardizedCompetencyId = competency.getLinkedStandardizedCompetency() == null ? null : competency.getLinkedStandardizedCompetency().getId();

        return new CompetencyImportResponseDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getSoftDueDate(),
                competency.getMasteryThreshold(), competency.isOptional(), courseId, linkedStandardizedCompetencyId);
    }
}

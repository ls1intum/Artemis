package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;

/**
 * DTO used to send responses regarding {@link Prerequisite} objects.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PrerequisiteResponseDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, ZonedDateTime softDueDate, int masteryThreshold, boolean optional) {

    public static PrerequisiteResponseDTO of(Prerequisite prerequisite) {
        return new PrerequisiteResponseDTO(prerequisite.getId(), prerequisite.getTitle(), prerequisite.getDescription(), prerequisite.getTaxonomy(), prerequisite.getSoftDueDate(),
                prerequisite.getMasteryThreshold(), prerequisite.isOptional());
    }
}

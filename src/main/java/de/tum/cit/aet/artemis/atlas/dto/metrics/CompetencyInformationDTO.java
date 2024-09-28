package de.tum.cit.aet.artemis.atlas.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * A DTO for the CompetencyInformation entity.
 *
 * @param id               the id of the competency
 * @param title            the title of the competency
 * @param description      the description of the competency
 * @param taxonomy         the taxonomy of the competency
 * @param softDueDate      the soft due date of the competency
 * @param optional         whether the competency is optional
 * @param masteryThreshold the mastery threshold of the competency
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyInformationDTO(long id, String title, String description, CompetencyTaxonomy taxonomy, ZonedDateTime softDueDate, boolean optional, int masteryThreshold) {
}

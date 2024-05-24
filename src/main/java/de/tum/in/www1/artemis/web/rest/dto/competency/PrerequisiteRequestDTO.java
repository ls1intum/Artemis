package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.AbstractCompetency;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;

/**
 * DTO used to send create/update requests regarding {@link Prerequisite} objects.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PrerequisiteRequestDTO(@NotBlank @Size(min = 1, max = AbstractCompetency.MAX_TITLE_LENGTH) String title, String description, CompetencyTaxonomy taxonomy,
        ZonedDateTime softDueDate, int masteryThreshold, boolean optional) {
}

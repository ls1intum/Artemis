package de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;

/**
 * DTO used to send requests regarding {@link StandardizedCompetency} objects.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StandardizedCompetencyDTO(@NotNull @Size(min = 1, max = StandardizedCompetency.MAX_TITLE_LENGTH) String title,
        @Size(max = StandardizedCompetency.MAX_DESCRIPTION_LENGTH) String description, CompetencyTaxonomy taxonomy,
        @NotNull @Size(min = 1, max = StandardizedCompetency.MAX_VERSION_LENGTH) String version, @NotNull Long knowledgeAreaId, Long sourceId) {
}

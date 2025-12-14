package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * DTO for competency operations (create/update) used by the Atlas Agent.
 * Used to transfer competency data between the AI agent and the service layer.
 *
 * @param competencyId the competency ID (null for create, set for update)
 * @param title        the competency title
 * @param description  the competency description
 * @param taxonomy     the competency taxonomy level
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyOperationDTO(Long competencyId, @NotBlank(message = "Title is required for all competencies") String title, String description,
        @NotNull(message = "Taxonomy is required for all competencies") CompetencyTaxonomy taxonomy) {
}

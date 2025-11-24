package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for competency preview response.
 * Used when previewing competencies in the agent chat UI.
 * The client receives a list of these DTOs to display competency cards.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyPreviewResponseDTO(CompetencyPreviewDTO competency, @Nullable Long competencyId, @Nullable Boolean viewOnly) {
}

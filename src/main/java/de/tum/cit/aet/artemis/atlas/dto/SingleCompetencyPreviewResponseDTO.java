package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for single competency preview response.
 * This is the response structure when previewing a single competency.
 * The client checks for "preview": true to identify this response type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SingleCompetencyPreviewResponseDTO(boolean preview, CompetencyPreviewDTO competency, Long competencyId, Boolean viewOnly) {
}

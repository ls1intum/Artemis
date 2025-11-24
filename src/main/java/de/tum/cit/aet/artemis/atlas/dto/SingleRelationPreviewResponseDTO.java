package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for single competency relation preview response.
 * This is the response structure when previewing a single relation.
 * The client checks for "preview": true to identify this response type.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SingleRelationPreviewResponseDTO(boolean preview, CompetencyRelationPreviewDTO relation, boolean viewOnly) {
}

package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for batch competency preview response.
 * This is the response structure when previewing multiple competencies.
 * The client checks for "batchPreview": true to identify this response type.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BatchCompetencyPreviewResponseDTO(boolean batchPreview, int count, @Nullable List<CompetencyPreviewDTO> competencies, boolean viewOnly) {
}

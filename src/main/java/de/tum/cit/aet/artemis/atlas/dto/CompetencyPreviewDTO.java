package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for competency preview information.
 * Matches the TypeScript CompetencyPreview interface in chat-message.model.ts.
 * Used for displaying competency cards in the agent chat UI.
 *
 * Note: For batch previews, competencyId is included per-item to enable create/update detection.
 * For single previews, competencyId is at the wrapper level (CompetencyPreviewResponseDTO).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyPreviewDTO(String title, String description, String taxonomy, String icon, Long competencyId) {
}

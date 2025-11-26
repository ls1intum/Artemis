package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for competency preview response sent to the client.
 * Used when previewing competencies in the agent chat UI.
 * Contains the data required to display competency preview cards.
 *
 * @param title        The competency title
 * @param description  The competency description
 * @param taxonomy     The competency taxonomy level (e.g., "REMEMBER", "UNDERSTAND", "APPLY")
 * @param icon         Font Awesome icon name for the taxonomy level
 * @param competencyId The competency ID (null for create operations, set for update operations)
 * @param viewOnly     Indicates whether this preview is read-only (true) or can be approved/saved (false/null)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyPreviewDTO(String title, String description, String taxonomy, String icon, @Nullable Long competencyId, @Nullable Boolean viewOnly) {
}

package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for competency preview responses.
 * Used when previewing competencies in the agent chat interface.
 * Contains the data required to display competency preview cards.
 *
 * @param title        the competency title
 * @param description  the competency description
 * @param taxonomy     the competency taxonomy level (e.g., "REMEMBER", "UNDERSTAND", "APPLY")
 * @param icon         Font Awesome icon name for the taxonomy level
 * @param competencyId the competency ID (null for create operations, set for update operations)
 * @param viewOnly     indicates whether this preview is read-only (true) or can be approved/saved (false/null)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyPreviewDTO(String title, String description, String taxonomy, String icon, long competencyId, Boolean viewOnly) {
}

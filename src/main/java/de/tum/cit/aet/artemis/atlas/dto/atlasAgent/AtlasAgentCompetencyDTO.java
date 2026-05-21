package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent tool responses representing competency information.
 * Fields are included in JSON when non-empty to ensure concise LLM responses.
 * Annotations here serve that the agent does not allow creation of competencies that lack these properties
 *
 * @param id          the competency ID
 * @param title       the competency title
 * @param description the competency description
 * @param taxonomy    the competency taxonomy level as string
 * @param courseId    the course ID
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentCompetencyDTO(long id, @NotNull String title, @NotNull String description, @NotNull String taxonomy, Long courseId) {
}

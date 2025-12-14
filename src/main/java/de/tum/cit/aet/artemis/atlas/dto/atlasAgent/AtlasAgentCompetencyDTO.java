package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent tool responses representing competency information.
 * Fields are included in JSON when non-empty to ensure concise LLM responses.
 *
 * @param id          the competency ID
 * @param title       the competency title
 * @param description the competency description
 * @param taxonomy    the competency taxonomy level as string (nullable)
 * @param courseId    the course ID (nullable)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentCompetencyDTO(@NotNull Long id, @NotNull String title, @NotNull String description, @NotNull String taxonomy, @Nullable Long courseId) {
}

package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent tool responses representing exercise information.
 * Fields are included in JSON when non-empty to ensure concise LLM responses.
 *
 *
 * @param id          the exercise ID
 * @param title       the exercise title
 * @param type        the exercise type
 * @param maxPoints   the maximum points for the exercise
 * @param releaseDate the release date as string
 * @param dueDate     the due date as string
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentExerciseDTO(long id, String title, String type, double maxPoints, String releaseDate, String dueDate) {
}

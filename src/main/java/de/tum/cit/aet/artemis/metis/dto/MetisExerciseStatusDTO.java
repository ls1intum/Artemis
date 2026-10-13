package de.tum.cit.aet.artemis.metis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Whether an exercise has an enabled comprehension check, which decides whether the exercise page shows the check tab.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisExerciseStatusDTO(boolean enabled) {
}

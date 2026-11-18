package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An exercise reference containing only its id and title.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseIdAndTitleDTO(long id, String title) {
}

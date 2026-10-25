package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseLatestDueDateDTO(long exerciseId, ZonedDateTime dueDate) {
}

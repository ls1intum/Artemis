package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;

/**
 * The facts about a programming exercise that decide whether it can have a comprehension check and when: its course (null for an exam exercise), whether it is
 * an individual or a team exercise, its due date, and its title and short name for the export. A projection of
 * {@code MetisExerciseConfigRepository.findExerciseFacts}; no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisExerciseFactsDTO(long exerciseId, @Nullable Long courseId, ExerciseMode mode, @Nullable ZonedDateTime dueDate, String title, @Nullable String shortName) {
}

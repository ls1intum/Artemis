package de.tum.cit.aet.artemis.core.dto.calendar;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

/**
 * A DTO primarily used to retrieve data about {@link QuizExercise}s that are needed to create {@link CalendarEventDTO}s.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseCalendarEventDTO(@NotNull QuizMode quizMode, @NotNull String title, ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizBatch quizBatch,
        Integer duration) {
}

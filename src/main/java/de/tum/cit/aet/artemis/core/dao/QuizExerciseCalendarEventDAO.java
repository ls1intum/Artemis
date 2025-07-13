package de.tum.cit.aet.artemis.core.dao;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

public record QuizExerciseCalendarEventDAO(@NotNull QuizMode quizMode, @NotNull String title, ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizBatch quizBatch,
        Integer duration) {
}

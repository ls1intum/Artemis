package de.tum.cit.aet.artemis.core.dao;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import javax.annotation.Nullable;

import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

public record QuizExerciseCalendarEventDAO(@NotNull QuizMode quizMode, @NotNull String title, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime dueDate,
        @Nullable QuizBatch quizBatch, @Nullable Integer duration) {
}

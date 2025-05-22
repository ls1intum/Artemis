package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.openapitools.jackson.nullable.JsonNullable;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

public record QuizExerciseForEditorDTO(JsonNullable<@NotNull String> title, JsonNullable<@NotNull String> channelName, JsonNullable<Set<String>> categories,
        JsonNullable<@NotNull DifficultyLevel> difficulty, JsonNullable<@NotNull Integer> duration, JsonNullable<@NotNull Boolean> randomizeQuestionOrder,
        JsonNullable<@NotNull QuizMode> quizMode, JsonNullable<ZonedDateTime> releaseDate, JsonNullable<ZonedDateTime> dueDate,
        JsonNullable<@NotNull IncludedInOverallScore> includedInOverallScore) {

}

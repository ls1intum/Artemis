package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseCreateDTO(@NotEmpty String title, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, DifficultyLevel difficulty,
        @NotNull ExerciseMode mode, @NotNull IncludedInOverallScore includedInOverallScore, Set<CompetencyExerciseLink> competencyLinks, Set<String> categories,
        @NotNull String channelName, Boolean randomizeQuestionOrder, @NotNull QuizMode quizMode, int duration) {
}

package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.question.create.QuizQuestionCreateDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseCreateDTO(@NotEmpty String title, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, DifficultyLevel difficulty,
        @NotNull ExerciseMode mode, @NotNull IncludedInOverallScore includedInOverallScore, Set<CompetencyExerciseLink> competencyLinks, Set<String> categories,
        @NotNull String channelName, boolean randomizeQuestionOrder, @NotNull QuizMode quizMode, int duration, @NotEmpty List<? extends QuizQuestionCreateDTO> quizQuestions) {

    public QuizExercise toDomainObject() {
        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setTitle(title);
        quizExercise.setReleaseDate(releaseDate);
        quizExercise.setStartDate(startDate);
        quizExercise.setDueDate(dueDate);
        quizExercise.setDifficulty(difficulty);
        quizExercise.setMode(mode);
        quizExercise.setIncludedInOverallScore(includedInOverallScore);
        quizExercise.setCompetencyLinks(competencyLinks == null ? Set.of() : competencyLinks);
        quizExercise.setCategories(categories == null ? Set.of() : categories);
        quizExercise.setChannelName(channelName);
        quizExercise.setRandomizeQuestionOrder(randomizeQuestionOrder);
        quizExercise.setQuizMode(quizMode);
        quizExercise.setDuration(duration);
        quizExercise.setQuizQuestions(quizQuestions.stream().map(QuizQuestionCreateDTO::toDomainObject).toList());
        return quizExercise;
    }
}

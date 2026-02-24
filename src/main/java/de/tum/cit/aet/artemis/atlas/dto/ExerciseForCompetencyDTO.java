package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseForCompetencyDTO(long id, String title, String shortName, ExerciseType type, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable Double maxPoints, @Nullable Double bonusPoints,
        @Nullable AssessmentType assessmentType, @Nullable DifficultyLevel difficulty, @Nullable IncludedInOverallScore includedInOverallScore, @Nullable ExerciseMode mode,
        @Nullable Set<String> categories, boolean teamMode, @Nullable Long studentAssignedTeamId, boolean studentAssignedTeamIdComputed, @Nullable Boolean allowOnlineEditor,
        @Nullable Boolean allowOfflineIde, @Nullable Boolean quizStarted, @Nullable Boolean quizEnded) {

    public static ExerciseForCompetencyDTO of(@Nullable Exercise exercise) {
        if (exercise == null) {
            return null;
        }

        Boolean allowOnlineEditor = null;
        Boolean allowOfflineIde = null;
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            allowOnlineEditor = programmingExercise.isAllowOnlineEditor();
            allowOfflineIde = programmingExercise.isAllowOfflineIde();
        }

        Boolean quizStarted = null;
        Boolean quizEnded = null;
        if (exercise instanceof QuizExercise quizExercise) {
            quizStarted = quizExercise.isQuizStarted();
            quizEnded = quizExercise.isQuizEnded();
        }

        return new ExerciseForCompetencyDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getExerciseType(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getAssessmentType(),
                exercise.getDifficulty(), exercise.getIncludedInOverallScore(), exercise.getMode(), exercise.getCategories(), exercise.isTeamMode(),
                exercise.getStudentAssignedTeamId(), exercise.isStudentAssignedTeamIdComputed(), allowOnlineEditor, allowOfflineIde, quizStarted, quizEnded);
    }
}

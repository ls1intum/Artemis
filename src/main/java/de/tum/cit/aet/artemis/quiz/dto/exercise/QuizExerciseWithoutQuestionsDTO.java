package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.CourseForQuizExerciseDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseWithoutQuestionsDTO(Long id, String title, String shortName, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, DifficultyLevel difficulty, boolean visibleToStudents, CourseForQuizExerciseDTO course, String type, Boolean randomizeQuestionOrder,
        Integer allowedNumberOfAttempts, Integer remainingNumberOfAttempts, QuizMode quizMode, Integer duration, Set<QuizBatchDTO> quizBatches, boolean quizStarted,
        boolean quizEnded, IncludedInOverallScore includedInOverallScore, ExerciseMode mode) {

    /**
     * Creates a QuizExerciseWithoutQuestionsDTO object from a QuizExercise object.
     *
     * @param quizExercise the QuizExercise object
     * @return the created QuizExerciseWithoutQuestionsDTO object
     */
    public static QuizExerciseWithoutQuestionsDTO of(final QuizExercise quizExercise) {
        Set<QuizBatch> quizBatches = quizExercise.getQuizBatches();
        Set<QuizBatchDTO> quizBatchesDTOs = Set.of();
        if (Hibernate.isInitialized(quizBatches) && quizBatches != null) {
            quizBatchesDTOs = quizBatches.stream().map(QuizBatchDTO::of).collect(Collectors.toSet());
        }
        return new QuizExerciseWithoutQuestionsDTO(quizExercise.getId(), quizExercise.getTitle(), quizExercise.getShortName(), quizExercise.getReleaseDate(),
                quizExercise.getStartDate(), quizExercise.getDueDate(), quizExercise.getAssessmentDueDate(), quizExercise.getDifficulty(), quizExercise.isVisibleToStudents(),
                CourseForQuizExerciseDTO.of(quizExercise.getCourseViaExerciseGroupOrCourseMember()), quizExercise.getType(), quizExercise.isRandomizeQuestionOrder(),
                quizExercise.getAllowedNumberOfAttempts(), quizExercise.getRemainingNumberOfAttempts(), quizExercise.getQuizMode(), quizExercise.getDuration(), quizBatchesDTOs,
                quizExercise.isQuizStarted(), quizExercise.isQuizEnded(), quizExercise.getIncludedInOverallScore(), quizExercise.getMode());
    }

}

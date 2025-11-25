package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.CourseForQuizExerciseDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseWithoutQuestionsDTO(Long id, String title, String shortName, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, DifficultyLevel difficulty, boolean visibleToStudents, CourseForQuizExerciseDTO course, String type, Boolean randomizeQuestionOrder,
        Integer allowedNumberOfAttempts, Integer remainingNumberOfAttempts, QuizMode quizMode, Integer duration, Set<QuizBatchDTO> quizBatches, boolean quizStarted,
        boolean quizEnded) {

    /**
     * Creates a QuizExerciseWithoutQuestionsDTO object from a QuizExercise object.
     *
     * @param quizExercise the QuizExercise object
     * @return the created QuizExerciseWithoutQuestionsDTO object
     */
    public static QuizExerciseWithoutQuestionsDTO of(final QuizExercise quizExercise) {
        return new QuizExerciseWithoutQuestionsDTO(quizExercise.getId(), quizExercise.getTitle(), quizExercise.getShortName(), quizExercise.getReleaseDate(),
                quizExercise.getStartDate(), quizExercise.getDueDate(), quizExercise.getAssessmentDueDate(), quizExercise.getDifficulty(), quizExercise.isVisibleToStudents(),
                CourseForQuizExerciseDTO.of(quizExercise.getCourseViaExerciseGroupOrCourseMember()), quizExercise.getType(), quizExercise.isRandomizeQuestionOrder(),
                quizExercise.getAllowedNumberOfAttempts(), quizExercise.getRemainingNumberOfAttempts(), quizExercise.getQuizMode(), quizExercise.getDuration(),
                quizExercise.getQuizBatches().stream().map(QuizBatchDTO::of).collect(Collectors.toSet()), quizExercise.isQuizStarted(), quizExercise.isQuizEnded());
    }

}

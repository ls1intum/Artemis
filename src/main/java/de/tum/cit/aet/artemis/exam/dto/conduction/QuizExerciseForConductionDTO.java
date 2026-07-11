package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;

/**
 * Quiz-exercise-specific fields carried in the conduction payload (unwrapped into the exercise object). The quiz
 * questions are projected to their without-solution shape ({@link QuizQuestionWithoutSolutionDTO}), matching the masked
 * entity wire: answerable ids are kept, correct answers / mappings / solutions are not.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseForConductionDTO(Integer allowedNumberOfAttempts, Integer duration, boolean quizStarted, boolean quizEnded, QuizMode quizMode,
        Boolean randomizeQuestionOrder, List<QuizQuestionWithoutSolutionDTO> quizQuestions) {

    /**
     * Extracts the quiz-specific fields, guarding against uninitialized lazy quiz-question collections.
     *
     * @param quizExercise the quiz exercise to convert
     * @return the quiz-specific fields
     */
    public static QuizExerciseForConductionDTO of(QuizExercise quizExercise) {
        var entityQuestions = quizExercise.getQuizQuestions();
        List<QuizQuestionWithoutSolutionDTO> quizQuestions = (entityQuestions == null || !Hibernate.isInitialized(entityQuestions)) ? null
                : entityQuestions.stream().map(QuizQuestionWithoutSolutionDTO::of).toList();
        return new QuizExerciseForConductionDTO(quizExercise.getAllowedNumberOfAttempts(), quizExercise.getDuration(), quizExercise.isQuizStarted(), quizExercise.isQuizEnded(),
                quizExercise.getQuizMode(), quizExercise.isRandomizeQuestionOrder(), quizQuestions);
    }
}

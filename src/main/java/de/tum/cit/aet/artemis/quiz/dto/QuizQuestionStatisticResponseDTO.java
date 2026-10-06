package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

/**
 * Response for a single quiz-question statistics page.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionStatisticResponseDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise, QuizQuestionWithSolutionDTO quizQuestion,
        QuizQuestionStatisticDTO quizQuestionStatistic) {

    /**
     * Creates a question-statistics response.
     *
     * @param quizExercise the quiz exercise
     * @param question     the selected question
     * @param statistic    the calculated question statistic
     * @return the question-statistics response
     */
    public static QuizQuestionStatisticResponseDTO of(QuizExercise quizExercise, QuizQuestion question, QuizQuestionStatisticDTO statistic) {
        return new QuizQuestionStatisticResponseDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), QuizQuestionWithSolutionDTO.of(question), statistic);
    }
}

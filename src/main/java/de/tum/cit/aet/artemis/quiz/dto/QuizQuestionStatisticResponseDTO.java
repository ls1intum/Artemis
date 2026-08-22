package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

/**
 * Response for a single quiz-question statistics page.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionStatisticResponseDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise, List<QuizQuestionWithSolutionDTO> quizQuestions, Long questionId,
        QuizQuestionStatisticDTO statistic) {

    /**
     * Creates a question-statistics response.
     *
     * @param quizExercise the quiz exercise
     * @param questionId   the selected question id
     * @param statistic    the calculated question statistic
     * @return the question-statistics response
     */
    public static QuizQuestionStatisticResponseDTO of(QuizExercise quizExercise, long questionId, QuizQuestionStatisticDTO statistic) {
        List<QuizQuestionWithSolutionDTO> questions = quizExercise.getQuizQuestions().stream().map(QuizQuestionWithSolutionDTO::of).toList();
        return new QuizQuestionStatisticResponseDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), questions, questionId, statistic);
    }
}

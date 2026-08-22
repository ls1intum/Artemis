package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

/**
 * Response for the quiz point-distribution statistics page.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizPointStatisticsDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise, List<QuizQuestionWithSolutionDTO> quizQuestions,
        QuizPointStatisticDTO statistic) {

    /**
     * Creates a point-statistics response.
     *
     * @param quizExercise the quiz exercise
     * @param statistic    the calculated point distribution
     * @return the point-statistics response
     */
    public static QuizPointStatisticsDTO of(QuizExercise quizExercise, QuizPointStatisticDTO statistic) {
        List<QuizQuestionWithSolutionDTO> questions = quizExercise.getQuizQuestions().stream().map(QuizQuestionWithSolutionDTO::of).toList();
        return new QuizPointStatisticsDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), questions, statistic);
    }
}

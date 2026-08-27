package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

/**
 * Response for the quiz statistics overview page.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizStatisticsOverviewDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise, List<QuestionStatisticsDTO> quizQuestions, Integer participantsRated,
        Integer participantsUnrated) {

    /**
     * Creates an overview response from the quiz and its per-question statistics.
     *
     * @param quizExercise            the quiz exercise
     * @param statisticsByQuestion    statistics keyed by question id
     * @param ratedParticipantCount   the number of latest eligible rated results
     * @param unratedParticipantCount the number of latest eligible unrated results
     * @return the overview response
     */
    public static QuizStatisticsOverviewDTO of(QuizExercise quizExercise, Map<Long, QuizQuestionStatisticDTO> statisticsByQuestion, long ratedParticipantCount,
            long unratedParticipantCount) {
        List<QuestionStatisticsDTO> questions = quizExercise.getQuizQuestions().stream()
                .map(question -> QuestionStatisticsDTO.of(question, statisticsByQuestion.get(question.getId()))).toList();
        return new QuizStatisticsOverviewDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), questions, Math.toIntExact(ratedParticipantCount),
                Math.toIntExact(unratedParticipantCount));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuestionStatisticsDTO(@JsonUnwrapped QuizQuestionWithSolutionDTO question, QuizQuestionStatisticDTO quizQuestionStatistic) {

    static QuestionStatisticsDTO of(QuizQuestion question, QuizQuestionStatisticDTO statistic) {
        return new QuestionStatisticsDTO(QuizQuestionWithSolutionDTO.of(question), statistic);
    }
}

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
     * @param quizExercise         the quiz exercise
     * @param statisticsByQuestion statistics keyed by question id
     * @return the overview response
     */
    public static QuizStatisticsOverviewDTO of(QuizExercise quizExercise, Map<Long, QuizQuestionStatisticDTO> statisticsByQuestion) {
        List<QuestionStatisticsDTO> questions = quizExercise.getQuizQuestions().stream()
                .map(question -> QuestionStatisticsDTO.of(question, statisticsByQuestion.get(question.getId()))).toList();
        QuizQuestionStatisticDTO participantSource = quizExercise.getQuizQuestions().stream().map(QuizQuestion::getId).map(statisticsByQuestion::get)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        int ratedCount = participantSource == null ? 0 : participantSource.participantsRated();
        int unratedCount = participantSource == null ? 0 : participantSource.participantsUnrated();
        return new QuizStatisticsOverviewDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), questions, ratedCount, unratedCount);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuestionStatisticsDTO(@JsonUnwrapped QuizQuestionWithSolutionDTO question, QuizQuestionStatisticDTO statistic) {

    static QuestionStatisticsDTO of(QuizQuestion question, QuizQuestionStatisticDTO statistic) {
        return new QuestionStatisticsDTO(QuizQuestionWithSolutionDTO.of(question), statistic);
    }
}

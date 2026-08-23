package de.tum.cit.aet.artemis.quiz.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

class QuizStatisticsOverviewDTOTest {

    @Test
    void shouldUseMaximumParticipantCountAcrossQuestionStatistics() {
        QuizExercise quizExercise = new QuizExercise();
        MultipleChoiceQuestion firstQuestion = questionWithId(1L);
        MultipleChoiceQuestion secondQuestion = questionWithId(2L);
        quizExercise.addQuestion(firstQuestion);
        quizExercise.addQuestion(secondQuestion);

        Map<Long, QuizQuestionStatisticDTO> statisticsByQuestion = Map.of(firstQuestion.getId(), QuizQuestionStatisticDTO.of(firstQuestion, new long[] { 1, 4, 0, 0 }, null),
                secondQuestion.getId(), QuizQuestionStatisticDTO.of(secondQuestion, new long[] { 3, 2, 0, 0 }, null));

        QuizStatisticsOverviewDTO overview = QuizStatisticsOverviewDTO.of(quizExercise, statisticsByQuestion);

        assertThat(overview.participantsRated()).isEqualTo(3);
        assertThat(overview.participantsUnrated()).isEqualTo(4);
    }

    private static MultipleChoiceQuestion questionWithId(long id) {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.setId(id);
        return question;
    }
}

package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.QuizQuestionStatisticDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticsOverviewDTO;

class QuizStatisticsOverviewDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUseQuizParticipantCountsIndependentlyOfQuestionStatistics() {
        QuizExercise quizExercise = new QuizExercise();
        MultipleChoiceQuestion firstQuestion = questionWithId(1L);
        MultipleChoiceQuestion secondQuestion = questionWithId(2L);
        quizExercise.addQuestion(firstQuestion);
        quizExercise.addQuestion(secondQuestion);

        Map<Long, QuizQuestionStatisticDTO> statisticsByQuestion = Map.of(firstQuestion.getId(), QuizQuestionStatisticDTO.of(firstQuestion, 1, 4, 0, 0, null),
                secondQuestion.getId(), QuizQuestionStatisticDTO.of(secondQuestion, 3, 2, 0, 0, null));

        QuizStatisticsOverviewDTO overview = QuizStatisticsOverviewDTO.of(quizExercise, statisticsByQuestion, 5, 6);

        assertThat(overview.participantsRated()).isEqualTo(5);
        assertThat(overview.participantsUnrated()).isEqualTo(6);
    }

    @Test
    void shouldExposeOnlyQuestionFieldsUsedByTheOverview() {
        QuizExercise quizExercise = new QuizExercise();
        MultipleChoiceQuestion question = questionWithId(1L);
        question.setTitle("Question");
        question.setText("Large question payload");
        question.setPoints(2);
        AnswerOption answerOption = new AnswerOption();
        answerOption.setId(11L);
        answerOption.setText("Large solution payload");
        question.addAnswerOption(answerOption);
        quizExercise.addQuestion(question);

        QuizQuestionStatisticDTO statistic = QuizQuestionStatisticDTO.of(question, 1, 2, 1, 0, null);
        QuizStatisticsOverviewDTO overview = QuizStatisticsOverviewDTO.of(quizExercise, Map.of(question.getId(), statistic), 1, 2);

        JsonNode questionJson = objectMapper.valueToTree(overview).path("quizQuestions").path(0);
        assertThat(questionJson.path("id").asLong()).isEqualTo(question.getId());
        assertThat(questionJson.path("title").asText()).isEqualTo(question.getTitle());
        assertThat(questionJson.path("points").asDouble()).isEqualTo(question.getPoints());
        assertThat(questionJson.has("text")).isFalse();
        assertThat(questionJson.has("answerOptions")).isFalse();
    }

    private static MultipleChoiceQuestion questionWithId(long id) {
        MultipleChoiceQuestion question = new MultipleChoiceQuestion();
        question.setId(id);
        return question;
    }
}

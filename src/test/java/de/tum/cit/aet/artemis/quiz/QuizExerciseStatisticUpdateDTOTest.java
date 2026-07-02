package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.quiz.domain.AnswerCounter;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseStatisticUpdateDTO;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;

class QuizExerciseStatisticUpdateDTOTest {

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Test
    void multipleChoiceStatisticPayloadResolvesAnswerCountersWithoutLeakingSolutions() throws Exception {
        QuizExercise quizExercise = quizExerciseWithMultipleChoiceStatistic();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        MultipleChoiceQuestionStatistic statistic = (MultipleChoiceQuestionStatistic) question.getQuizQuestionStatistic();
        AnswerOption firstOption = question.getAnswerOptions().getFirst();
        AnswerOption secondOption = question.getAnswerOptions().getLast();

        AnswerCounter firstCounter = answerCounterFor(statistic, firstOption.getId());
        firstCounter.setId(101L);
        firstCounter.setRatedCounter(2);
        firstCounter.setUnRatedCounter(1);
        AnswerCounter secondCounter = answerCounterFor(statistic, secondOption.getId());
        secondCounter.setId(102L);
        secondCounter.setRatedCounter(0);
        secondCounter.setUnRatedCounter(3);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(QuizExerciseStatisticUpdateDTO.of(quizExercise)));
        JsonNode questionNode = root.path("quizQuestions").get(0);
        JsonNode firstAnswerOptionNode = questionNode.path("answerOptions").get(0);
        JsonNode answerCounters = questionNode.path("quizQuestionStatistic").path("answerCounters");
        JsonNode firstAnswerCounterNode = answerCounterNodeFor(answerCounters, firstOption.getId());

        assertThat(root.path("id").asLong()).isEqualTo(42L);
        assertThat(questionNode.path("id").asLong()).isEqualTo(11L);
        assertThat(firstAnswerOptionNode.path("id").asLong()).isEqualTo(firstOption.getId());
        assertThat(firstAnswerOptionNode.path("text").asText()).isEqualTo("A");
        assertThat(firstAnswerOptionNode.path("hint").asText()).isEqualTo("H1");
        assertThat(firstAnswerOptionNode.has("isCorrect")).isFalse();
        assertThat(firstAnswerOptionNode.has("explanation")).isFalse();

        assertThat(answerCounters).hasSize(2);
        assertThat(firstAnswerCounterNode.path("id").asLong()).isEqualTo(101L);
        assertThat(firstAnswerCounterNode.path("ratedCounter").asInt()).isEqualTo(2);
        assertThat(firstAnswerCounterNode.path("unRatedCounter").asInt()).isEqualTo(1);
        assertThat(firstAnswerCounterNode.path("answer").path("id").asLong()).isEqualTo(firstOption.getId());
        assertThat(firstAnswerCounterNode.path("answer").path("text").asText()).isEqualTo("A");
        assertThat(firstAnswerCounterNode.path("answer").has("isCorrect")).isFalse();
        assertThat(firstAnswerCounterNode.path("answer").has("explanation")).isFalse();
    }

    @Test
    void multipleChoiceStatisticPayloadFailsForMissingAnswerOptionReference() {
        QuizExercise quizExercise = quizExerciseWithMultipleChoiceStatistic();
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        MultipleChoiceQuestionStatistic statistic = (MultipleChoiceQuestionStatistic) question.getQuizQuestionStatistic();
        AnswerCounter staleCounter = new AnswerCounter();
        staleCounter.setId(103L);
        staleCounter.setAnswerOptionId(999L);
        statistic.addAnswerCounters(staleCounter);

        assertThatThrownBy(() -> QuizExerciseStatisticUpdateDTO.of(quizExercise)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Answer counter 103 references missing answer option 999 in question 11");
    }

    private static QuizExercise quizExerciseWithMultipleChoiceStatistic() {
        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setId(42L);
        MultipleChoiceQuestion question = QuizExerciseFactory.createMultipleChoiceQuestion();
        question.setId(11L);
        quizExercise.addQuestion(question);

        question.initializeStatistic();
        MultipleChoiceQuestionStatistic statistic = (MultipleChoiceQuestionStatistic) question.getQuizQuestionStatistic();
        statistic.setId(99L);
        question.getAnswerOptions().forEach(statistic::addAnswerOption);
        return quizExercise;
    }

    private static AnswerCounter answerCounterFor(MultipleChoiceQuestionStatistic statistic, Long answerOptionId) {
        return statistic.getAnswerCounters().stream().filter(counter -> Objects.equals(counter.getAnswerOptionId(), answerOptionId)).findFirst().orElseThrow();
    }

    private static JsonNode answerCounterNodeFor(JsonNode answerCounters, Long answerOptionId) {
        for (JsonNode answerCounter : answerCounters) {
            if (answerCounter.path("answer").path("id").asLong() == answerOptionId) {
                return answerCounter;
            }
        }
        throw new AssertionError("Missing answer counter for answer option " + answerOptionId);
    }
}

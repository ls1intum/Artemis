package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseStatisticUpdateDTO;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;

class QuizExerciseStatisticUpdateDTOTest {

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Test
    void shouldContainCountersWithoutSolutionsOrEntityMutationWhenCreatingStatisticUpdate() throws Exception {
        QuizExercise quizExercise = createQuizWithStatistics();
        MultipleChoiceQuestion multipleChoiceQuestion = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        ShortAnswerQuestion shortAnswerQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsBytes(QuizExerciseStatisticUpdateDTO.of(quizExercise)));
        JsonNode multipleChoiceNode = findQuestionByType(root.path("quizQuestions"), "multiple-choice");
        JsonNode dragAndDropNode = findQuestionByType(root.path("quizQuestions"), "drag-and-drop");
        JsonNode shortAnswerNode = findQuestionByType(root.path("quizQuestions"), "short-answer");

        assertThat(root.path("id").asLong()).isEqualTo(42L);
        assertThat(multipleChoiceNode.path("quizQuestionStatistic").path("answerCounters")).hasSize(2);
        assertThat(multipleChoiceNode.path("answerOptions").get(0).get("isCorrect")).isNull();
        assertThat(multipleChoiceNode.path("answerOptions").get(0).get("explanation")).isNull();
        JsonNode counterAnswer = multipleChoiceNode.path("quizQuestionStatistic").path("answerCounters").get(0).path("answer");
        assertThat(counterAnswer.get("isCorrect")).isNull();
        assertThat(counterAnswer.get("explanation")).isNull();
        assertThat(dragAndDropNode.get("correctMappings")).isNull();
        assertThat(dragAndDropNode.path("quizQuestionStatistic").path("dropLocationCounters")).hasSize(4);
        assertThat(shortAnswerNode.get("solutions")).isNull();
        assertThat(shortAnswerNode.get("correctMappings")).isNull();
        assertThat(shortAnswerNode.path("quizQuestionStatistic").path("shortAnswerSpotCounters")).hasSize(2);

        assertThat(multipleChoiceQuestion.getAnswerOptions()).extracting(option -> option.isIsCorrect()).containsExactly(true, false);
        assertThat(multipleChoiceQuestion.getAnswerOptions()).extracting(option -> option.getExplanation()).containsExactly("E1", "E2");
        assertThat(dragAndDropQuestion.getCorrectMappings()).isNotEmpty();
        assertThat(shortAnswerQuestion.getSolutions()).isNotEmpty();
        assertThat(shortAnswerQuestion.getCorrectMappings()).isNotEmpty();
    }

    private static QuizExercise createQuizWithStatistics() {
        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setId(42L);

        MultipleChoiceQuestion multipleChoiceQuestion = QuizExerciseFactory.createMultipleChoiceQuestion();
        DragAndDropQuestion dragAndDropQuestion = QuizExerciseFactory.createDragAndDropQuestion();
        ShortAnswerQuestion shortAnswerQuestion = QuizExerciseFactory.createShortAnswerQuestion();
        quizExercise.addQuestion(multipleChoiceQuestion);
        quizExercise.addQuestion(dragAndDropQuestion);
        quizExercise.addQuestion(shortAnswerQuestion);

        multipleChoiceQuestion.initializeStatistic();
        MultipleChoiceQuestionStatistic multipleChoiceStatistic = (MultipleChoiceQuestionStatistic) multipleChoiceQuestion.getQuizQuestionStatistic();
        multipleChoiceQuestion.getAnswerOptions().forEach(multipleChoiceStatistic::addAnswerOption);

        dragAndDropQuestion.initializeStatistic();
        DragAndDropQuestionStatistic dragAndDropStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
        dragAndDropQuestion.getDropLocations().forEach(dragAndDropStatistic::addDropLocation);

        shortAnswerQuestion.initializeStatistic();
        ShortAnswerQuestionStatistic shortAnswerStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
        shortAnswerQuestion.getSpots().forEach(shortAnswerStatistic::addSpot);
        return quizExercise;
    }

    private static JsonNode findQuestionByType(JsonNode questions, String type) {
        for (JsonNode question : questions) {
            if (type.equals(question.path("type").asText())) {
                return question;
            }
        }
        throw new AssertionError("Missing quiz question of type " + type);
    }
}

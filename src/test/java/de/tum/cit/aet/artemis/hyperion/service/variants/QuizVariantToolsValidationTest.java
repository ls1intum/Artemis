package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;

class QuizVariantToolsValidationTest {

    private final JsonMapper mapper = JsonObjectMapper.get();

    private final QuizExerciseTestRepository repository = mock(QuizExerciseTestRepository.class);

    private final QuizExerciseService quizService = mock(QuizExerciseService.class);

    private final ExerciseVariantJobService jobs = mock(ExerciseVariantJobService.class);

    private QuizVariantTools tools;

    private QuizExercise quiz;

    private MultipleChoiceQuestion original;

    @BeforeEach
    void setup() {
        original = new MultipleChoiceQuestion();
        original.setId(11L);
        original.setTitle("Original");
        original.setText("Which answer?");
        original.setPoints(3.0);
        original.setScoringType(ScoringType.ALL_OR_NOTHING);
        AnswerOption option = new AnswerOption();
        option.setText("Correct");
        option.setIsCorrect(true);
        original.setAnswerOptions(new ArrayList<>(List.of(option)));
        quiz = new QuizExercise();
        quiz.setId(7L);
        quiz.setTitle("Quiz");
        quiz.setDuration(60);
        quiz.setQuizQuestions(new ArrayList<>(List.of(original)));
        when(repository.findByIdWithQuestionsElseThrow(7L)).thenReturn(quiz);
        when(repository.findByIdWithQuestionsAndCategoriesAndBatchesElseThrow(7L)).thenReturn(quiz);
        tools = new QuizVariantTools(7L, "job", jobs, repository, quizService, mapper);
    }

    private String json(QuizQuestion question) {
        return mapper.writeValueAsString(mapper.readTree(QuizVariantTools.serializeQuestions(mapper, List.of(question))).get(0));
    }

    @Test
    void validReplacementPreservesIdentityGradingAndExerciseBackReference() {
        ObjectNode update = (ObjectNode) mapper.readTree(json(original));
        update.put("id", 999);
        update.put("title", "Updated");
        update.put("points", 100);
        update.put("scoringType", "PROPORTIONAL_WITH_PENALTY");
        assertThat(tools.updateQuestion(0, mapper.writeValueAsString(update))).isEqualTo("Question 0 updated. The quiz is currently valid.");
        QuizQuestion saved = quiz.getQuizQuestions().getFirst();
        assertThat(saved.getTitle()).isEqualTo("Updated");
        assertThat(saved.getId()).isEqualTo(11L);
        assertThat(saved.getPoints()).isEqualTo(3.0);
        assertThat(saved.getScoringType()).isEqualTo(ScoringType.ALL_OR_NOTHING);
        assertThat(saved.getExercise()).isSameAs(quiz);
        assertThat(mapper.readTree(tools.getQuestions()).get(0).path("type").asString()).isEqualTo("multiple-choice");
        assertThat(tools.validateQuiz()).contains("Question 0 (MultipleChoiceQuestion, \"Updated\"): valid", "The quiz is valid.");
    }

    @Test
    void validationErrorsLeaveQuestionUntouchedAndReportPreciseCorrection() {
        assertThat(tools.updateQuestion(null, "{}")).contains("the \"index\" argument is required");
        assertThat(tools.updateQuestion(0, null)).contains("the \"questionJson\" argument is required");
        assertThat(tools.updateQuestion(0, " ")).contains("the \"questionJson\" argument is required");
        assertThat(tools.updateQuestion(-1, "{}")).contains("out of range");
        assertThat(tools.updateQuestion(1, "{}")).contains("out of range");
        assertThat(tools.updateQuestion(0, "invalid")).contains("the question JSON is invalid");
        assertThat(tools.updateQuestion(0, "{\"type\":\"short-answer\"}")).contains("the question type must not change");
        ObjectNode invalid = (ObjectNode) mapper.readTree(json(original));
        invalid.put("title", "");
        assertThat(tools.updateQuestion(0, mapper.writeValueAsString(invalid))).contains("the updated question is not valid");
        assertThat(quiz.getQuizQuestions()).containsExactly(original);
        verify(quizService, never()).save(any());
    }

    @Test
    void batchEditsReportEveryFailureAndSaveOnlySuccessfulChanges() {
        String result = tools.updateQuestions(Arrays.asList(null, new QuizVariantTools.QuestionEdit(null, "{}"), new QuizVariantTools.QuestionEdit(0, null),
                new QuizVariantTools.QuestionEdit(0, " "), new QuizVariantTools.QuestionEdit(5, "{}"), new QuizVariantTools.QuestionEdit(0, json(original))));
        assertThat(result).contains("Edit 1: Error: the \"index\" argument is required", "Edit 3 (question 0): Error: the \"questionJson\" argument is required",
                "Edit 5 (question 5): Error: question index 5 is out of range", "Edit 6 (question 0): updated.", "1 of 6 question(s) updated.");
        assertThat(quiz.getQuizQuestions().getFirst().getExercise()).isSameAs(quiz);
        verify(quizService).save(quiz);
    }

    @Test
    void absentEditsGapsAndRepositoryErrorsAreRecoverableToolResults() {
        assertThat(tools.updateQuestions(null)).contains("no question edits were provided");
        assertThat(tools.updateQuestions(List.of())).contains("no question edits were provided");
        quiz.setQuizQuestions(new ArrayList<>(Arrays.asList((QuizQuestion) null)));
        assertThat(tools.updateQuestion(0, "{}")).contains("there is no question at index 0");
        // The verifier's report must identify the gap even when overall validity cannot evaluate the damaged list.
        when(repository.findByIdWithQuestionsElseThrow(7L)).thenThrow(new IllegalStateException("repository unavailable"));
        assertThat(tools.getQuestions()).isEqualTo("Error: could not read the quiz questions: repository unavailable");
        assertThat(tools.validateQuiz()).isEqualTo("Error: could not validate the quiz: repository unavailable");
        when(repository.findByIdWithQuestionsAndCategoriesAndBatchesElseThrow(7L)).thenThrow(new IllegalStateException("repository unavailable"));
        assertThat(tools.updateQuestion(0, "{}")).isEqualTo("Error: could not update question 0: repository unavailable");
        assertThat(tools.updateQuestions(List.of(new QuizVariantTools.QuestionEdit(0, "{}")))).isEqualTo("Error: could not update the questions: repository unavailable");
    }

    @Test
    void shortAnswerReplacementReconnectsExistingMappingsAndKeepsComponentIds() {
        ShortAnswerQuestion question = new ShortAnswerQuestion();
        question.setId(12L);
        question.setTitle("Fill the gap");
        question.setText("[-spot 1]");
        question.setPoints(1.0);
        question.setScoringType(ScoringType.ALL_OR_NOTHING);
        ShortAnswerSpot spot = new ShortAnswerSpot();
        spot.setId(21L);
        spot.setSpotNr(1);
        ShortAnswerSolution solution = new ShortAnswerSolution();
        solution.setId(22L);
        solution.setText("answer");
        question.setSpots(new ArrayList<>(List.of(spot)));
        question.setSolutions(new ArrayList<>(List.of(solution)));
        ShortAnswerMapping mapping = new ShortAnswerMapping();
        mapping.setId(23L);
        mapping.setSpot(spot);
        mapping.setSolution(solution);
        question.setCorrectMappings(Set.of(mapping));
        quiz.setQuizQuestions(new ArrayList<>(List.of(question)));

        assertThat(tools.updateQuestion(0, json(question))).contains("Question 0 updated");
        ShortAnswerQuestion saved = (ShortAnswerQuestion) quiz.getQuizQuestions().getFirst();
        assertThat(saved.getExercise()).isSameAs(quiz);
        assertThat(saved.getCorrectMappings()).singleElement().satisfies(restored -> {
            assertThat(restored.getSpot().getId()).isEqualTo(21L);
            assertThat(restored.getSolution().getId()).isEqualTo(22L);
            assertThat(restored.getSpot()).isSameAs(saved.getSpots().getFirst());
            assertThat(restored.getSolution()).isSameAs(saved.getSolutions().getFirst());
        });
    }

    @Test
    void canceledToolsStopBeforeReadingOrWritingButStillAcceptFinish() {
        when(jobs.isCancelRequested("job")).thenReturn(true);
        assertThat(tools.getQuestions()).contains("CANCELLED");
        assertThat(tools.updateQuestion(0, "{}")).contains("CANCELLED");
        assertThat(tools.updateQuestions(List.of())).contains("CANCELLED");
        assertThat(tools.validateQuiz()).contains("CANCELLED");
        assertThat(tools.finish("stopped")).isEqualTo("Summary recorded. You are done with this round.");
        assertThat(tools.finishSummary()).isEqualTo("stopped");
        assertThat(tools.toolCallStats()).isEmpty();
        verify(quizService, never()).save(any());
    }
}

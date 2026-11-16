package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.quiz.dto.QuizQuestionStatisticDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.ShortAnswerQuestionWithoutMappingDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerBeforeEvaluationDTO;

/**
 * Covers the branch every quiz projection factory needs but no production path reaches.
 * <p>
 * The factories map a question or answer onto the one record that matches its type. {@link QuizQuestion} and
 * {@link SubmittedAnswer} are abstract but not sealed, so the compiler cannot prove the three known types are the only
 * ones and each switch needs a default. Before the projections became sealed hierarchies that default silently
 * produced a value with every branch null, which is how an unmapped question type would have reached a client as an
 * object with no content. It now throws, and this is the test that says so.
 */
class QuizProjectionFactoryTest {

    /** A question type none of the projections know about. */
    private static class UnknownQuestion extends QuizQuestion {

        @Override
        protected ScoringStrategy makeScoringStrategy() {
            return null;
        }

        @Override
        public QuizQuestion copyQuestionId() {
            return this;
        }
    }

    /** An answer type none of the projections know about, pointing at a question they also do not know. */
    private static class UnknownAnswer extends SubmittedAnswer {

        @Override
        public void checkAndDeleteReferences(QuizExercise quizExercise) {
            // nothing to clean up
        }
    }

    @Test
    void shouldRejectAnUnknownQuestionTypeWithSolutions() {
        assertThatIllegalArgumentException().isThrownBy(() -> QuizQuestionWithSolutionDTO.of(new UnknownQuestion())).withMessageContaining("UnknownQuestion");
    }

    @Test
    void shouldRejectAnUnknownQuestionTypeWithoutSolutions() {
        assertThatIllegalArgumentException().isThrownBy(() -> QuizQuestionWithoutSolutionDTO.of(new UnknownQuestion())).withMessageContaining("UnknownQuestion");
    }

    private static ShortAnswerQuestion shortAnswerQuestionWithOneSpotAndOneSolution() {
        ShortAnswerSpot spot = new ShortAnswerSpot();
        spot.setSpotNr(1);
        ShortAnswerSolution solution = new ShortAnswerSolution();
        solution.setText("answer");
        ShortAnswerQuestion question = new ShortAnswerQuestion();
        question.setSpots(List.of(spot));
        question.setSolutions(List.of(solution));
        return question;
    }

    /**
     * The projection for the conduction of a quiz is the one the factory picks for a short answer question: it carries the spots but not the solutions.
     */
    @Test
    void shouldOmitShortAnswerSolutionsWhenProjectingAQuestionForConduction() {
        ShortAnswerQuestion question = shortAnswerQuestionWithOneSpotAndOneSolution();

        JsonNode json = JsonObjectMapper.get().valueToTree(QuizQuestionWithoutSolutionDTO.of(question));

        assertThat(json.has("spots")).isTrue();
        assertThat(json.get("spots")).hasSize(1);
        assertThat(json.has("solutions")).isFalse();
    }

    @Test
    void shouldProjectAShortAnswerQuestionWithoutSpotsForConduction() {
        ShortAnswerQuestion withoutSpotList = shortAnswerQuestionWithOneSpotAndOneSolution();
        withoutSpotList.setSpots(null);
        ShortAnswerQuestion withEmptySpotList = shortAnswerQuestionWithOneSpotAndOneSolution();
        withEmptySpotList.setSpots(List.of());

        assertThat(ShortAnswerQuestionWithoutMappingDTO.ofWithoutSolutions(withoutSpotList).spots()).isNull();
        assertThat(ShortAnswerQuestionWithoutMappingDTO.ofWithoutSolutions(withEmptySpotList).spots()).isNull();
        assertThat(ShortAnswerQuestionWithoutMappingDTO.ofWithoutSolutions(withoutSpotList).solutions()).isNull();
        assertThat(JsonObjectMapper.get().valueToTree(QuizQuestionWithoutSolutionDTO.of(withoutSpotList)).has("solutions")).isFalse();
    }

    /**
     * The projection used for result views keeps the solutions.
     */
    @Test
    void shouldKeepShortAnswerSolutionsInTheProjectionWithSolutions() {
        ShortAnswerQuestion question = shortAnswerQuestionWithOneSpotAndOneSolution();

        assertThat(ShortAnswerQuestionWithoutMappingDTO.of(question).solutions()).hasSize(1);
        assertThat(ShortAnswerQuestionWithoutMappingDTO.ofWithoutSolutions(question).similarityValue()).isEqualTo(question.getSimilarityValue());
    }

    @Test
    void shouldRejectAnUnknownAnswerTypeBeforeEvaluation() {
        SubmittedAnswer answer = new UnknownAnswer();
        answer.setQuizQuestion(new UnknownQuestion());

        // the question is projected first, so this is the question factory rejecting the unknown type
        assertThatIllegalArgumentException().isThrownBy(() -> SubmittedAnswerBeforeEvaluationDTO.of(answer)).withMessageContaining("UnknownQuestion");
    }

    @Test
    void shouldRejectAnUnknownQuestionTypeWithStatistics() {
        assertThatIllegalArgumentException().isThrownBy(() -> QuizQuestionStatisticDTO.of(new UnknownQuestion(), 0, 0, 0, 0, null)).withMessageContaining("UnknownQuestion");
    }

    @Test
    void shouldRejectAnUnknownAnswerTypeAfterEvaluation() {
        SubmittedAnswer answer = new UnknownAnswer();
        answer.setQuizQuestion(new UnknownQuestion());

        assertThatIllegalArgumentException().isThrownBy(() -> SubmittedAnswerAfterEvaluationDTO.of(answer)).withMessageContaining("UnknownQuestion");
    }
}

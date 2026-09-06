package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;
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

        @Override
        public void initializeStatistic() {
            // no statistic for a type that is never persisted
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

    @Test
    void shouldRejectAnUnknownAnswerTypeBeforeEvaluation() {
        SubmittedAnswer answer = new UnknownAnswer();
        answer.setQuizQuestion(new UnknownQuestion());

        // the question is projected first, so this is the question factory rejecting the unknown type
        assertThatIllegalArgumentException().isThrownBy(() -> SubmittedAnswerBeforeEvaluationDTO.of(answer)).withMessageContaining("UnknownQuestion");
    }

    @Test
    void shouldRejectAnUnknownAnswerTypeAfterEvaluation() {
        SubmittedAnswer answer = new UnknownAnswer();
        answer.setQuizQuestion(new UnknownQuestion());

        assertThatIllegalArgumentException().isThrownBy(() -> SubmittedAnswerAfterEvaluationDTO.of(answer)).withMessageContaining("UnknownQuestion");
    }
}

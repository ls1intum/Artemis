package de.tum.in.www1.artemis.domain;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestion;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedAnswer;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShortAnswerSubmittedTextTest {

    private ShortAnswerSubmittedText shortAnswerSubmittedText;

    private ShortAnswerQuestion shortAnswerQuestion;

    @BeforeEach
    public void init() {
        shortAnswerQuestion = new ShortAnswerQuestion();
        ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
        shortAnswerSubmittedAnswer.setQuizQuestion(shortAnswerQuestion);
        shortAnswerSubmittedText = new ShortAnswerSubmittedText();
        shortAnswerSubmittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer);
    }

    @Test
    public void testSubmissionWithMatchingLetterCaseAndExactMatching() {
        shortAnswerQuestion.setMatchLetterCase(true);
        shortAnswerQuestion.setSimilarityValue(100);

        String solution = "Example solution";

        boolean observed = shortAnswerSubmittedText.isSubmittedTextCorrect("Example solution", solution);

        assertTrue(observed);

        solution = "Exampl slution";

        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("Example solution", solution);

        assertFalse(observed);

        solution = "example solution";

        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("Example solution", solution);

        assertFalse(observed);
    }

    @Test
    public void testSubmissionWithoutMatchingLetterCaseAndExactMatching() {
        shortAnswerQuestion.setMatchLetterCase(false);
        shortAnswerQuestion.setSimilarityValue(100);

        String solution = "Example solution";

        boolean observed = shortAnswerSubmittedText.isSubmittedTextCorrect("Example solution", solution);

        assertTrue(observed);

        solution = "Exampl slution";

        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("Example solution", solution);

        assertFalse(observed);

        solution = "example solution";

        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("Example solution", solution);

        assertTrue(observed);
    }

    @Test
    public void testSubmissionWithNonExactMatching() {
        shortAnswerQuestion.setMatchLetterCase(false);
        shortAnswerQuestion.setSimilarityValue(70);

        String solution = "Example solution";

        boolean observed = shortAnswerSubmittedText.isSubmittedTextCorrect("Example solut", solution);

        assertTrue(observed);

        solution = "Exampl slution";

        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("Exmple soluton", solution);

        assertTrue(observed);

        solution = "example solution";

        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("random text", solution);

        assertFalse(observed);
    }
}

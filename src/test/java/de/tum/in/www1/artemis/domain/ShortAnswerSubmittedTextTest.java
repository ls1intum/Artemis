package de.tum.in.www1.artemis.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestion;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedAnswer;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedText;

class ShortAnswerSubmittedTextTest {

    private ShortAnswerSubmittedText shortAnswerSubmittedText;

    private ShortAnswerQuestion shortAnswerQuestion;

    /**
     * initialize attributes for test cases
     */
    @BeforeEach
    void init() {
        shortAnswerQuestion = new ShortAnswerQuestion();
        ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
        shortAnswerSubmittedAnswer.setQuizQuestion(shortAnswerQuestion);
        shortAnswerSubmittedText = new ShortAnswerSubmittedText();
        shortAnswerSubmittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer);
    }

    /**
     * testSubmissionWithMatchingLetterCaseAndExactMatching
     * tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} with similarity value 100 and letter case matching
     */
    @Test
    void testSubmissionWithMatchingLetterCaseAndExactMatching() {
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

    /**
     * @func testSubmissionWithoutMatchingLetterCaseAndExactMatching
     * @desc tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} with similarity value 100 and without letter case matching
     */
    @Test
    void testSubmissionWithoutMatchingLetterCaseAndExactMatching() {
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

    /**
     * @func testSubmissionWithNonExactMatching
     * @desc tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} with similarity value 70 and without letter case matching
     */
    @Test
    void testSubmissionWithNonExactMatching() {
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

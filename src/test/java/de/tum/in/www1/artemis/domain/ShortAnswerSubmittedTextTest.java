package de.tum.in.www1.artemis.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.enumeration.SpotType;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestion;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSpot;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedAnswer;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedText;

class ShortAnswerSubmittedTextTest {

    private ShortAnswerSubmittedText shortAnswerSubmittedText;

    private ShortAnswerQuestion shortAnswerQuestion;

    private ShortAnswerSpot shortAnswerSpot;

    /**
     * @func init
     * @desc initialize attributes for test cases
     */
    @BeforeEach
    void init() {
        shortAnswerQuestion = new ShortAnswerQuestion();
        ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
        shortAnswerSubmittedAnswer.setQuizQuestion(shortAnswerQuestion);
        shortAnswerSubmittedText = new ShortAnswerSubmittedText();
        shortAnswerSubmittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer);
        shortAnswerSpot = new ShortAnswerSpot();
        shortAnswerSubmittedText.setSpot(shortAnswerSpot);
    }

    /**
     * @func testSubmissionWithMatchingLetterCaseAndExactMatching
     * @desc tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} with similarity value 100 and letter case matching
     */
    @Test
    void testSubmissionWithMatchingLetterCaseAndExactMatching() {
        shortAnswerSpot.setType(SpotType.TEXT);
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
        shortAnswerSpot.setType(SpotType.TEXT);
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
        shortAnswerSpot.setType(SpotType.TEXT);
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

    /**
     * @func testNumberSubmissionInSolutionRange
     * @desc tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} in range of solution
     */
    @Test
    void testNumberSubmissionInSolutionRange() {
        shortAnswerSpot.setType(SpotType.NUMBER);

        String solution = "1-100";
        boolean observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100", solution);
        assertTrue(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("1", solution);
        assertTrue(observed);

        solution = "-100-100";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100", solution);
        assertTrue(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-100", solution);
        assertTrue(observed);

        solution = "-100--1";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-100", solution);
        assertTrue(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-1", solution);
        assertTrue(observed);

        solution = ".1-100.5";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100.5", solution);
        assertTrue(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect(".1", solution);
        assertTrue(observed);

        solution = "-100.5-100.5";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100.5", solution);
        assertTrue(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-100.5", solution);
        assertTrue(observed);

        solution = "-.100--.1";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-.1000", solution);
        assertTrue(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-0.100", solution);
        assertTrue(observed);
    }

    /**
     * @func testNumberSubmissionNotInSolutionRange
     * @desc tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} not in range of solution
     */
    @Test
    void testNumberSubmissionNotInSolutionRange() {
        shortAnswerSpot.setType(SpotType.NUMBER);

        String solution = "1-100";
        boolean observed = shortAnswerSubmittedText.isSubmittedTextCorrect("101", solution);
        assertFalse(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("0", solution);
        assertFalse(observed);

        solution = "-100-100";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("101", solution);
        assertFalse(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-101", solution);
        assertFalse(observed);

        solution = "-100--1";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-101", solution);
        assertFalse(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("0", solution);
        assertFalse(observed);

        solution = ".1-100.5";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100.5000001", solution);
        assertFalse(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect(".09999", solution);
        assertFalse(observed);

        solution = "-100.5--.100";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-100.50001", solution);
        assertFalse(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-0.099999", solution);
        assertFalse(observed);

        solution = "-100.5--.000015";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-101", solution);
        assertFalse(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-.000014999", solution);
        assertFalse(observed);
    }

    /**
     * @func testNumberSubmissionExactlySameSolution
     * @desc tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} exactly the same as the solution
     */
    @Test
    void testNumberSubmissionExactlySameSolution() {
        shortAnswerSpot.setType(SpotType.NUMBER);

        String solution = "100";
        boolean observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100", solution);
        assertTrue(observed);

        solution = "0";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("0", solution);
        assertTrue(observed);

        solution = "-100";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-100", solution);
        assertTrue(observed);

        solution = "100";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100.00", solution);
        assertTrue(observed);

        solution = "0.00";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("0.0", solution);
        assertTrue(observed);

        solution = "-100.00";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-100", solution);
        assertTrue(observed);

        solution = "0.1";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("0.1000", solution);
        assertTrue(observed);
    }

    /**
     * @func testNumberSubmissionNotExactlySameSolution
     * @desc tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} not exactly the same as the solution
     */
    @Test
    void testNumberSubmissionNotExactlySameSolution() {
        shortAnswerSpot.setType(SpotType.NUMBER);

        String solution = "100";
        boolean observed = shortAnswerSubmittedText.isSubmittedTextCorrect("10", solution);
        assertFalse(observed);

        solution = "0";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("1", solution);
        assertFalse(observed);

        solution = "-100";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-1000", solution);
        assertFalse(observed);

        solution = "100";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100.0000001", solution);
        assertFalse(observed);

        solution = "0.00";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("0.000001", solution);
        assertFalse(observed);

        solution = "-100.00";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("-100.00001", solution);
        assertFalse(observed);

        solution = "0.1";
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("0.10001", solution);
        assertFalse(observed);
    }

    /**
     * @func testNumberSubmissionNotNumber
     * @desc tests {@link ShortAnswerSubmittedText#isSubmittedTextCorrect(String, String)} submitted text is not number
     */
    @Test
    void testNumberSubmissionNotNumber() {
        shortAnswerSpot.setType(SpotType.NUMBER);

        String solution = "100";
        boolean observed = shortAnswerSubmittedText.isSubmittedTextCorrect("not a number", solution);
        assertFalse(observed);
        observed = shortAnswerSubmittedText.isSubmittedTextCorrect("100x", solution);
        assertFalse(observed);
    }
}

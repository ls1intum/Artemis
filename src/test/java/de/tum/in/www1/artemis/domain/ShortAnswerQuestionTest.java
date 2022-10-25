package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.enumeration.SpotType;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestion;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSpot;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedAnswer;
import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedText;

class ShortAnswerQuestionTest {

    @Test
    void checkSolutionsAreTrimmed() {
        var shortAnswerSubmittedText = new ShortAnswerSubmittedText();
        var shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
        var shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerSubmittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer);
        shortAnswerSubmittedAnswer.setQuizQuestion(shortAnswerQuestion);
        var shortAnswerSpot = new ShortAnswerSpot();
        shortAnswerSubmittedText.setSpot(shortAnswerSpot);
        shortAnswerSpot.setType(SpotType.TEXT);
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("  submitted untrimmed \n", "submitted untrimmed")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("solution untrimmed", "  solution untrimmed \n")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("both untrimmed  ", "  both untrimmed\n")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("  wrong but untrimmed", "different  ")).isFalse();
        shortAnswerSpot.setType(SpotType.NUMBER);
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("10", " 1-10 ")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect(" 10 ", " 1-10 ")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect(" -10 ", "-10-10")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect(" 10 ", " 10 ")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect(" 10 ", "10")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("-10", " -10 ")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("0.10", " 0.1-10.0 ")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect(" 0.10 ", " 0.1-10.0 ")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect(" -0.10 ", "-0.1-10.0")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect(" 10.00 ", " 10.0 ")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect(" -10.00 ", "-10.0")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("10.00", " 10.0 ")).isTrue();
    }
}

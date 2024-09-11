package de.tum.cit.aet.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;

class ShortAnswerQuestionTest {

    @Test
    void checkSolutionsAreTrimmed() {
        var shortAnswerSubmittedText = new ShortAnswerSubmittedText();
        var shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
        var shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerSubmittedText.setSubmittedAnswer(shortAnswerSubmittedAnswer);
        shortAnswerSubmittedAnswer.setQuizQuestion(shortAnswerQuestion);
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("  submitted untrimmed \n", "submitted untrimmed")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("solution untrimmed", "  solution untrimmed \n")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("both untrimmed  ", "  both untrimmed\n")).isTrue();
        assertThat(shortAnswerSubmittedText.isSubmittedTextCorrect("  wrong but untrimmed", "different  ")).isFalse();
    }
}

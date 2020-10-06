package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedText;

public class ShortAnswerQuestionTest {

    @Test
    public void checkSolutionsAreTrimmed() {
        var shortAnswerQuestion = new ShortAnswerSubmittedText();
        assertThat(shortAnswerQuestion.isSubmittedTextCorrect("  submitted untrimmed \n", "submitted untrimmed")).isTrue();
        assertThat(shortAnswerQuestion.isSubmittedTextCorrect("solution untrimmed", "  solution untrimmed \n")).isTrue();
        assertThat(shortAnswerQuestion.isSubmittedTextCorrect("both untrimmed  ", "  both untrimmed\n")).isTrue();
        assertThat(shortAnswerQuestion.isSubmittedTextCorrect("  wrong but untrimmed", "different  ")).isFalse();
    }
}

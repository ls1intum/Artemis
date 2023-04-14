package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.config.Constants;

class FeedbackTest {

    @Test
    void setTruncatedFeedbackDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailTextTruncated(getText(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 100));

        assertThat(feedback.getDetailText()).hasSize(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH);
        assertThat(feedback.getLongFeedbackText()).isNull();
        assertThat(feedback.getHasLongFeedbackText()).isFalse();
    }

    @Test
    void setTruncatedFeedbackDetailTextNull() {
        final Feedback feedback = new Feedback();
        feedback.setDetailTextTruncated(null);

        assertThat(feedback.getDetailText()).isNull();
        assertThat(feedback.getHasLongFeedbackText()).isFalse();
    }

    @Test
    void setShortDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText("abc");

        assertThat(feedback.getDetailText()).isEqualTo("abc");
        assertThat(feedback.getLongFeedbackText()).isNull();
        assertThat(feedback.getHasLongFeedbackText()).isFalse();
    }

    @Test
    void setNullDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(null);

        assertThat(feedback.getDetailText()).isNull();
        assertThat(feedback.getLongFeedbackText()).isNull();
        assertThat(feedback.getHasLongFeedbackText()).isFalse();
    }

    @Test
    void setLongDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(getText(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10));

        assertThat(feedback.getDetailText()).hasSize(Constants.FEEDBACK_PREVIEW_TEXT_MAX_LENGTH);
        assertThat(feedback.getHasLongFeedbackText()).isTrue();

        final LongFeedbackText longFeedbackText = feedback.getLongFeedbackText();
        assertThat(longFeedbackText.getFeedback()).isSameAs(feedback);
        assertThat(longFeedbackText.getText()).hasSize(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10);
    }

    @Test
    void setDetailTextBetweenSoftMaxLengthAndTrueMax() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(getText(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH + 100));

        assertThat(feedback.getDetailText()).hasSize(Constants.FEEDBACK_PREVIEW_TEXT_MAX_LENGTH);
        assertThat(feedback.getHasLongFeedbackText()).isTrue();

        final LongFeedbackText longFeedbackText = feedback.getLongFeedbackText();
        assertThat(longFeedbackText.getFeedback()).isSameAs(feedback);
        assertThat(longFeedbackText.getText()).hasSize(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH + 100);
    }

    @Test
    void detailTextTrimMarker() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(getText(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10));

        assertThat(feedback.getDetailText()).hasSize(Constants.FEEDBACK_PREVIEW_TEXT_MAX_LENGTH).endsWith(" [...]");
        assertThat(feedback.getHasLongFeedbackText()).isTrue();
    }

    @Test
    void trimVeryLongLongFeedback() {
        final String veryLongFeedback = getText(Constants.LONG_FEEDBACK_MAX_LENGTH + 1_000);

        final Feedback feedback = new Feedback();
        feedback.setDetailText(veryLongFeedback);

        assertThat(feedback.getLongFeedbackText().getText()).hasSize(Constants.LONG_FEEDBACK_MAX_LENGTH);
    }

    @Test
    void copyWithLongFeedback() {
        final String feedbackText = getText(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH * 3);

        final Feedback feedback = new Feedback();
        feedback.setDetailText(feedbackText);

        final Feedback copiedFeedback = feedback.copyFeedback();
        assertThat(copiedFeedback.getHasLongFeedbackText()).isTrue();
        assertThat(copiedFeedback.getDetailText()).hasSize(Constants.FEEDBACK_PREVIEW_TEXT_MAX_LENGTH);

        final LongFeedbackText copiedLongFeedback = copiedFeedback.getLongFeedbackText();
        assertThat(copiedLongFeedback).isNotNull();
        assertThat(copiedLongFeedback.getText()).isEqualTo(feedbackText);
        assertThat(copiedLongFeedback.getFeedback()).isSameAs(copiedFeedback);
    }

    private String getText(final int length) {
        return "0".repeat(length);
    }
}

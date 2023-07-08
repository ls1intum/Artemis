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
        assertThat(feedback.getLongFeedbackText()).isEmpty();
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
        assertThat(feedback.getLongFeedbackText()).isEmpty();
        assertThat(feedback.getHasLongFeedbackText()).isFalse();
    }

    @Test
    void setNullDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(null);

        assertThat(feedback.getDetailText()).isNull();
        assertThat(feedback.getLongFeedbackText()).isEmpty();
        assertThat(feedback.getHasLongFeedbackText()).isFalse();
    }

    @Test
    void setLongDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(getText(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10));

        assertThat(feedback.getDetailText()).hasSize(Constants.FEEDBACK_PREVIEW_TEXT_MAX_LENGTH);
        assertThat(feedback.getHasLongFeedbackText()).isTrue();

        final LongFeedbackText longFeedbackText = feedback.getLongFeedback().orElseThrow();
        assertThat(longFeedbackText.getFeedback()).isSameAs(feedback);
        assertThat(longFeedbackText.getText()).hasSize(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10);
    }

    @Test
    void setDetailTextBetweenSoftMaxLengthAndTrueMax() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(getText(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH + 100));

        assertThat(feedback.getDetailText()).hasSize(Constants.FEEDBACK_PREVIEW_TEXT_MAX_LENGTH);
        assertThat(feedback.getHasLongFeedbackText()).isTrue();

        final LongFeedbackText longFeedbackText = feedback.getLongFeedback().orElseThrow();
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

        final LongFeedbackText longFeedback = feedback.getLongFeedback().orElseThrow();
        assertThat(longFeedback.getText()).hasSize(Constants.LONG_FEEDBACK_MAX_LENGTH);
    }

    private String getText(final int length) {
        return "0".repeat(length);
    }
}

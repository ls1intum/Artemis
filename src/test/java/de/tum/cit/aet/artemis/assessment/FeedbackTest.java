package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.config.FeedbackConfiguration;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.core.config.Constants;

class FeedbackTest {

    @Test
    void referenceElementIdIgnoresTheElementTypeInFrontOfIt() {
        // The editor's name for a kind of element changed with Apollon, so the same element can be referenced as
        // `Class:<id>` by an older assessment and `class:<id>` by a newer one. Only the id identifies the element.
        assertThat(new Feedback().reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2680").getReferenceElementId()).isEqualTo("6aba5764-d102-4740-9675-b2bd0a4f2680");
        assertThat(new Feedback().reference("class:6aba5764-d102-4740-9675-b2bd0a4f2680").getReferenceElementId()).isEqualTo("6aba5764-d102-4740-9675-b2bd0a4f2680");
        assertThat(new Feedback().reference("node:6aba5764-d102-4740-9675-b2bd0a4f2680").getReferenceElementId()).isEqualTo("6aba5764-d102-4740-9675-b2bd0a4f2680");
    }

    @Test
    void referenceElementIdHandlesAReferenceWithoutATypeAndNoReferenceAtAll() {
        // A programming reference carries further colons; only the first one separates the type.
        assertThat(new Feedback().reference("file:src/Main.java_line:3").getReferenceElementId()).isEqualTo("src/Main.java_line:3");
        assertThat(new Feedback().reference("bare-id").getReferenceElementId()).isEqualTo("bare-id");
        assertThat(new Feedback().getReferenceElementId()).isNull();
    }

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
        final int maxFeedbackLength = FeedbackConfiguration.getMaxFeedbackLengthStatic();
        final String veryLongFeedback = getText(maxFeedbackLength + 1_000);

        final Feedback feedback = new Feedback();
        feedback.setDetailText(veryLongFeedback);

        final LongFeedbackText longFeedback = feedback.getLongFeedback().orElseThrow();
        assertThat(longFeedback.getText()).hasSize(maxFeedbackLength);
    }

    private String getText(final int length) {
        return "0".repeat(length);
    }
}

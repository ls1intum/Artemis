package de.tum.in.www1.artemis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;

class FeedbackRepositoryTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Test
    void copyFeedbackWithLongFeedback() {
        final String longText = "0".repeat(Constants.FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS + 10);

        final Feedback feedback = new Feedback();
        feedback.setHasLongFeedbackText(true);
        feedback.setDetailText(longText);
        feedback.setCredits(1.0);

        final Feedback savedFeedback = feedbackRepository.save(feedback);

        final Feedback copiedFeedback = savedFeedback.copyFeedback();
        assertThat(copiedFeedback.getLongFeedbackText()).isNotNull();
        assertThat(copiedFeedback.getLongFeedbackText().getText()).isEqualTo(longText);

        final Feedback newSavedFeedback = feedbackRepository.save(copiedFeedback);
        assertThat(newSavedFeedback.getId()).isNotEqualTo(savedFeedback.getId());
        assertThat(newSavedFeedback.getLongFeedbackText().getId()).isEqualTo(newSavedFeedback.getId());
    }
}

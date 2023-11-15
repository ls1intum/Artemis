package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.LongFeedbackText;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.LongFeedbackTextRepository;

class FeedbackServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private LongFeedbackTextRepository longFeedbackTextRepository;

    @Test
    void copyWithLongFeedback() {
        final String feedbackText = "0".repeat(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH * 3);

        final Feedback feedback = new Feedback();
        feedback.setDetailText(feedbackText);

        final Feedback copiedFeedback = feedbackService.copyFeedback(feedback);
        assertThat(copiedFeedback.getHasLongFeedbackText()).isTrue();
        assertThat(copiedFeedback.getDetailText()).hasSize(Constants.FEEDBACK_PREVIEW_TEXT_MAX_LENGTH);

        final LongFeedbackText copiedLongFeedback = copiedFeedback.getLongFeedback().orElseThrow();
        assertThat(copiedLongFeedback).isNotNull();
        assertThat(copiedLongFeedback.getText()).isEqualTo(feedbackText);
        assertThat(copiedLongFeedback.getFeedback()).isSameAs(copiedFeedback);
    }

    @Test
    void copyFeedbackWithLongFeedback() {
        final String longText = "0".repeat(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10);

        final Feedback feedback = new Feedback();
        feedback.setHasLongFeedbackText(true);
        feedback.setDetailText(longText);
        feedback.setCredits(1.0);

        assertThat(feedback.getLongFeedback()).isPresent();

        final long feedbackId = feedbackRepository.save(feedback).getId();
        // load from database again to check that it works even with lazy loading
        final Feedback freshlyLoadedFeedback = feedbackRepository.findById(feedbackId).orElseThrow();
        assertThat(freshlyLoadedFeedback.getHasLongFeedbackText()).isTrue();

        final Feedback copiedFeedback = feedbackService.copyFeedback(freshlyLoadedFeedback);
        assertThat(copiedFeedback.getLongFeedback()).isNotEmpty();
        final LongFeedbackText longFeedback = copiedFeedback.getLongFeedback().orElseThrow();
        assertThat(longFeedback.getText()).isEqualTo(longText);

        final Feedback newSavedFeedback = feedbackRepository.save(copiedFeedback);
        assertThat(newSavedFeedback.getId()).isNotEqualTo(feedbackId);

        final Optional<LongFeedbackText> savedNewLongFeedback = longFeedbackTextRepository.findByFeedbackId(newSavedFeedback.getId());
        assertThat(savedNewLongFeedback).isPresent();
    }
}

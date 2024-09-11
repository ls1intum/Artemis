package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.domain.Feedback;
import de.tum.cit.aet.artemis.domain.LongFeedbackText;

@Profile(PROFILE_CORE)
@Service
public class FeedbackService {

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    public FeedbackService(LongFeedbackTextRepository longFeedbackTextRepository) {
        this.longFeedbackTextRepository = longFeedbackTextRepository;
    }

    /**
     * Creates a deep copy of the feedback including attached {@link LongFeedbackText}.
     *
     * @param originalFeedback The feedback that should be copied.
     * @return A copy of the feedback with an empty ID.
     */
    public Feedback copyFeedback(final Feedback originalFeedback) {
        Optional<LongFeedbackText> longFeedbackText = Optional.empty();

        if (originalFeedback.getHasLongFeedbackText()) {
            if (Hibernate.isInitialized(originalFeedback.getLongFeedbackText())) {
                longFeedbackText = originalFeedback.getLongFeedback();
            }
            // still empty: the feedback was not fetched from the DB, load it explicitly now
            if (longFeedbackText.isEmpty()) {
                longFeedbackText = longFeedbackTextRepository.findByFeedbackId(originalFeedback.getId());
            }
        }

        return copyFeedback(originalFeedback, longFeedbackText);
    }

    private Feedback copyFeedback(final Feedback originalFeedback, final Optional<LongFeedbackText> longFeedbackText) {
        final Feedback feedback = new Feedback();

        feedback.setDetailText(originalFeedback.getDetailText());
        feedback.setType(originalFeedback.getType());
        // For manual result each feedback needs to have a credit. If no credit is set, we set it to 0.0
        feedback.setCredits(Objects.requireNonNullElse(originalFeedback.getCredits(), 0.0));
        feedback.setText(originalFeedback.getText());

        if (originalFeedback.isPositive() == null) {
            feedback.setPositiveViaCredits();
        }
        else {
            feedback.setPositive(originalFeedback.isPositive());
        }

        feedback.setReference(originalFeedback.getReference());
        feedback.setVisibility(originalFeedback.getVisibility());
        feedback.setGradingInstruction(originalFeedback.getGradingInstruction());
        feedback.setTestCase(originalFeedback.getTestCase());

        feedback.setHasLongFeedbackText(originalFeedback.getHasLongFeedbackText());
        longFeedbackText.ifPresent(longFeedback -> feedback.setDetailText(longFeedback.getText()));

        return feedback;
    }
}

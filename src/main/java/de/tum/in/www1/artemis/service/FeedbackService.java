package de.tum.in.www1.artemis.service;

import java.util.Objects;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.LongFeedbackText;
import de.tum.in.www1.artemis.repository.LongFeedbackTextRepository;

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
        if (!originalFeedback.getHasLongFeedbackText()) {
            return copyFeedback(originalFeedback, Optional.empty());
        }
        else if (Hibernate.isInitialized(originalFeedback.getLongFeedbackText())) {
            final Optional<LongFeedbackText> longFeedbackText = originalFeedback.getLongFeedbackText().stream().findAny();
            return copyFeedback(originalFeedback, longFeedbackText);
        }
        else {
            final Optional<LongFeedbackText> longFeedbackText = longFeedbackTextRepository.findByFeedbackId(originalFeedback.getId());
            return copyFeedback(originalFeedback, longFeedbackText);
        }
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

        feedback.setHasLongFeedbackText(originalFeedback.getHasLongFeedbackText());
        longFeedbackText.ifPresent(longFeedback -> feedback.setDetailText(longFeedback.getText()));

        return feedback;
    }
}

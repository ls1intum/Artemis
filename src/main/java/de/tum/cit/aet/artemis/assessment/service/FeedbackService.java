package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;

@Profile(PROFILE_CORE)
@Lazy
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

    /**
     * Creates a copy of the given test-case feedback for attaching to another result. The copy shares the
     * immutable, deduplicated message row — copying is cheap by design.
     *
     * @param originalFeedback The feedback that should be copied.
     * @return A copy of the feedback without an owning result (the caller attaches it).
     */
    public TestCaseFeedback copyTestCaseFeedback(final TestCaseFeedback originalFeedback) {
        TestCaseFeedback feedback = new TestCaseFeedback();
        feedback.setTestCase(originalFeedback.getTestCase());
        feedback.setPositive(originalFeedback.isPositive());
        feedback.setMessage(originalFeedback.getMessage());
        return feedback;
    }

    /**
     * Creates a copy of the given SCA feedback for attaching to another result. The copy shares the
     * immutable, deduplicated message row.
     *
     * @param originalFeedback The feedback that should be copied.
     * @return A copy of the feedback without an owning result (the caller attaches it).
     */
    public ScaFeedback copyScaFeedback(final ScaFeedback originalFeedback) {
        ScaFeedback feedback = new ScaFeedback();
        feedback.setTool(originalFeedback.getTool());
        feedback.setCategory(originalFeedback.getCategory());
        feedback.setToolCategory(originalFeedback.getToolCategory());
        feedback.setRule(originalFeedback.getRule());
        feedback.setFilePath(originalFeedback.getFilePath());
        feedback.setStartLine(originalFeedback.getStartLine());
        feedback.setEndLine(originalFeedback.getEndLine());
        feedback.setStartColumn(originalFeedback.getStartColumn());
        feedback.setEndColumn(originalFeedback.getEndColumn());
        feedback.setPriority(originalFeedback.getPriority());
        feedback.setPenalty(originalFeedback.getPenalty());
        feedback.setMessage(originalFeedback.getMessage());
        return feedback;
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

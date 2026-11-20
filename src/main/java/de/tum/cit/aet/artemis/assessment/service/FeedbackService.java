package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class FeedbackService {

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    public FeedbackService(LongFeedbackTextRepository longFeedbackTextRepository, TestCaseFeedbackRepository testCaseFeedbackRepository,
            ScaFeedbackRepository scaFeedbackRepository) {
        this.longFeedbackTextRepository = longFeedbackTextRepository;
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
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

    /**
     * Copies the typed automatic feedback (test-case and SCA rows) of the old result to the new result.
     * The rows are loaded from the database (the old result's collections may be uninitialized) and the
     * copies share the deduplicated message rows. No-op for results of non-programming exercises.
     *
     * @param newResult the result to copy the typed feedback to
     * @param oldResult the result to copy the typed feedback from
     */
    public void copyTypedFeedbackToResult(Result newResult, Result oldResult) {
        if (oldResult == null || oldResult.getId() == null) {
            return;
        }
        // fetch the shared message rows eagerly: the copies keep the message reference, and the new result
        // may be synthesized for serialization right away (e.g. exam test-run drafts) - a lazy proxy would
        // fail there with a LazyInitializationException
        testCaseFeedbackRepository.findWithTestCaseAndMessageByResultIds(List.of(oldResult.getId())).stream().map(this::copyTestCaseFeedback)
                .forEach(newResult::addTestCaseFeedback);
        scaFeedbackRepository.findWithMessageByResultIds(List.of(oldResult.getId())).stream().map(this::copyScaFeedback).forEach(newResult::addScaFeedback);

        // Insert the copies right away when the target result already exists: a synthesized legacy view is addressed by the id of the row it comes from, so every caller that
        // serializes the new result afterwards needs those ids. The rows are new, so this persists them in place - the ids land on these very instances and the eagerly
        // fetched test cases and messages above survive, both of which a merge copy would lose. A result that is not persisted yet gets its rows through the caller's save.
        if (newResult.getId() != null) {
            newResult.setTestCaseFeedbacks(testCaseFeedbackRepository.saveAll(newResult.getTestCaseFeedbacks()));
            newResult.setScaFeedbacks(scaFeedbackRepository.saveAll(newResult.getScaFeedbacks()));
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
        feedback.setTestCase(originalFeedback.getTestCase());

        feedback.setHasLongFeedbackText(originalFeedback.getHasLongFeedbackText());
        longFeedbackText.ifPresent(longFeedback -> feedback.setDetailText(longFeedback.getText()));

        return feedback;
    }
}

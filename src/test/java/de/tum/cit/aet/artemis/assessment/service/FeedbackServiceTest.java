package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class FeedbackServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

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
    void copyScaFeedbackCopiesAllColumns() {
        final ScaFeedback original = new ScaFeedback();
        original.setTool(StaticCodeAnalysisTool.SPOTBUGS);
        original.setCategory("Bad Practice");
        original.setToolCategory("BAD_PRACTICE");
        original.setRule("SOME_RULE");
        original.setFilePath("src/Main.java");
        original.setStartLine(1);
        original.setEndLine(2);
        original.setStartColumn(3);
        original.setEndColumn(4);
        original.setPriority("HIGH");
        original.setPenalty(1.5);

        final ScaFeedback copy = feedbackService.copyScaFeedback(original);

        assertThat(copy).usingRecursiveComparison().ignoringFields("id", "result").isEqualTo(original);
        // the tool-reported category feeds the synthesized issue JSON and the re-categorization matching
        assertThat(copy.getToolCategory()).isEqualTo("BAD_PRACTICE");
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

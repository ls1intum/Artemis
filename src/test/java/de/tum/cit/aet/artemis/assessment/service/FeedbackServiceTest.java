package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class FeedbackServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private LongFeedbackTextRepository longFeedbackTextRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    private TextExercise exercise;

    @BeforeEach
    void initTestCase() {
        Course course = courseUtilService.createCourse();
        exercise = TextExerciseFactory.generateTextExercise(null, null, null, course);
        exercise = exerciseRepository.save(exercise);
    }

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

        // Create a submission and result for the feedback (result_id and exercise_id are NOT NULL)
        TextSubmission submission = new TextSubmission();
        submission = submissionRepository.save(submission);

        Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(exercise.getId());
        result = resultRepository.save(result);

        final Feedback feedback = new Feedback();
        feedback.setHasLongFeedbackText(true);
        feedback.setDetailText(longText);
        feedback.setCredits(1.0);
        feedback.setResult(result);

        assertThat(feedback.getLongFeedback()).isPresent();

        final long feedbackId = feedbackRepository.save(feedback).getId();
        // load from database again to check that it works even with lazy loading
        final Feedback freshlyLoadedFeedback = feedbackRepository.findById(feedbackId).orElseThrow();
        assertThat(freshlyLoadedFeedback.getHasLongFeedbackText()).isTrue();

        final Feedback copiedFeedback = feedbackService.copyFeedback(freshlyLoadedFeedback);
        assertThat(copiedFeedback.getLongFeedback()).isNotEmpty();
        final LongFeedbackText longFeedback = copiedFeedback.getLongFeedback().orElseThrow();
        assertThat(longFeedback.getText()).isEqualTo(longText);

        // Create another result for the copied feedback
        Result result2 = new Result();
        result2.setSubmission(submission);
        result2.setExerciseId(exercise.getId());
        result2 = resultRepository.save(result2);
        copiedFeedback.setResult(result2);

        final Feedback newSavedFeedback = feedbackRepository.save(copiedFeedback);
        assertThat(newSavedFeedback.getId()).isNotEqualTo(feedbackId);

        final Optional<LongFeedbackText> savedNewLongFeedback = longFeedbackTextRepository.findByFeedbackId(newSavedFeedback.getId());
        assertThat(savedNewLongFeedback).isPresent();
    }
}

package de.tum.cit.aet.artemis.text.service;

import static org.hibernate.Hibernate.isInitialized;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.service.ComplaintResponseService;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Conditional(TextEnabled.class)
@Lazy
@Service
public class TextAssessmentService extends AssessmentService {

    private final TextBlockService textBlockService;

    public TextAssessmentService(UserRepository userRepository, ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository,
            FeedbackRepository feedbackRepository, ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            SubmissionRepository submissionRepository, TextBlockService textBlockService, Optional<ExamDateApi> examDateApi, SubmissionService submissionService,
            Optional<LtiApi> ltiApi, SingleUserNotificationService singleUserNotificationService, ResultWebsocketService resultWebsocketService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateApi, userRepository, ltiApi, singleUserNotificationService, resultWebsocketService);
        this.textBlockService = textBlockService;
    }

    /**
     * Load entities from database needed for text assessment, set potential feedback impact count & compute
     * Feedback suggestions (Athena):
     * 1. Create or load the result
     * 2. Set potential Feedback impact
     * 3. Compute Feedback Suggestions
     * 4. Load Text Blocks
     * 5. Compute Fallback Text Blocks if needed
     *
     * @param textSubmission Text Submission to be assessed
     * @param result         for which we prepare the submission
     */
    public void prepareSubmissionForAssessment(TextSubmission textSubmission, @Nullable Result result) {
        final Participation participation = textSubmission.getParticipation();

        if (result != null) {
            // Load Feedback already created for this assessment
            final List<Feedback> assessments = feedbackRepository.findByResult(result);
            result.setFeedbacks(assessments);
            result.setSubmission(textSubmission); // make sure this is not a Hibernate Proxy
        }
        else {
            // We are the first ones to open assess this submission, we want to lock it.
            result = new Result();
            result.setSubmission(textSubmission);
            result.setExerciseId(participation.getExercise().getId());
            resultService.createNewRatedManualResult(result);
            result.setCompletionDate(null);
            result = resultRepository.save(result);
            textSubmission.addResult(result);
            submissionRepository.save(textSubmission);
        }

        // Fetch feedback text blocks
        final var textBlocks = textBlockService.findAllBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(textBlocks);

        // If we did not fetch blocks from the database before, we fall back to computing them based on syntax.
        if (textSubmission.getBlocks() == null || !isInitialized(textSubmission.getBlocks()) || textSubmission.getBlocks().isEmpty()) {
            textBlockService.computeTextBlocksForSubmissionBasedOnSyntax(textSubmission);
        }
    }
}

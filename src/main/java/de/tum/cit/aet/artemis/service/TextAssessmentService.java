package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.hibernate.Hibernate.isInitialized;

import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.domain.Feedback;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.TextSubmission;
import de.tum.cit.aet.artemis.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.service.connectors.lti.LtiNewResultService;
import de.tum.cit.aet.artemis.service.exam.ExamDateService;
import de.tum.cit.aet.artemis.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.web.websocket.ResultWebsocketService;

@Profile(PROFILE_CORE)
@Service
public class TextAssessmentService extends AssessmentService {

    private final TextBlockService textBlockService;

    public TextAssessmentService(UserRepository userRepository, ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository,
            FeedbackRepository feedbackRepository, ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            SubmissionRepository submissionRepository, TextBlockService textBlockService, ExamDateService examDateService, GradingCriterionRepository gradingCriterionRepository,
            SubmissionService submissionService, Optional<LtiNewResultService> ltiNewResultService, SingleUserNotificationService singleUserNotificationService,
            ResultWebsocketService resultWebsocketService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateService, userRepository, ltiNewResultService, singleUserNotificationService, resultWebsocketService);
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
            result.setParticipation(participation);

            resultService.createNewRatedManualResult(result);
            result.setCompletionDate(null);
            result = resultRepository.save(result);
            result.setSubmission(textSubmission);
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

        // Remove participation after storing in database because submission already has the participation set
        result.setParticipation(null);
    }
}

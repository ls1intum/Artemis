package de.tum.in.www1.artemis.service;

import static org.hibernate.Hibernate.isInitialized;

import java.util.List;

import javax.annotation.Nullable;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.lti.LtiNewResultService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;

@Service
public class TextAssessmentService extends AssessmentService {

    private final TextBlockService textBlockService;

    public TextAssessmentService(UserRepository userRepository, ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository,
            FeedbackRepository feedbackRepository, ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            SubmissionRepository submissionRepository, TextBlockService textBlockService, ExamDateService examDateService, GradingCriterionRepository gradingCriterionRepository,
            SubmissionService submissionService, LtiNewResultService ltiNewResultService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateService, gradingCriterionRepository, userRepository, ltiNewResultService);
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

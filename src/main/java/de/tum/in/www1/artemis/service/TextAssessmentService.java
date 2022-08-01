package de.tum.in.www1.artemis.service;

import static org.hibernate.Hibernate.isInitialized;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;

@Service
public class TextAssessmentService extends AssessmentService {

    private final TextBlockService textBlockService;

    private final Optional<AutomaticTextFeedbackService> automaticTextFeedbackService;

    private final FeedbackConflictRepository feedbackConflictRepository;

    public TextAssessmentService(UserRepository userRepository, ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository,
            FeedbackRepository feedbackRepository, ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            SubmissionRepository submissionRepository, TextBlockService textBlockService, Optional<AutomaticTextFeedbackService> automaticTextFeedbackService,
            ExamDateService examDateService, ExerciseDateService exerciseDateService, FeedbackConflictRepository feedbackConflictRepository,
            GradingCriterionRepository gradingCriterionRepository, SubmissionService submissionService, LtiService ltiService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionService,
                submissionRepository, examDateService, exerciseDateService, gradingCriterionRepository, userRepository, ltiService);
        this.textBlockService = textBlockService;
        this.automaticTextFeedbackService = automaticTextFeedbackService;
        this.feedbackConflictRepository = feedbackConflictRepository;
    }

    /**
     * Load entities from database needed for text assessment, set potential feedback impact count & compute
     * Feedback suggestions (Athene):
     *   1. Create or load the result
     *   2. Set potential Feedback impact
     *   3. Compute Feedback Suggestions
     *   4. Load Text Blocks
     *   5. Compute Fallback Text Blocks if needed
     *
     * @param textSubmission Text Submission to be assessed
     * @param result for which we prepare the submission
     */
    public void prepareSubmissionForAssessment(TextSubmission textSubmission, @Nullable Result result) {
        final Participation participation = textSubmission.getParticipation();
        final TextExercise exercise = (TextExercise) participation.getExercise();

        final boolean computeFeedbackSuggestions = automaticTextFeedbackService.isPresent() && exercise.isAutomaticAssessmentEnabled();

        if (result != null) {
            // Load Feedback already created for this assessment
            final List<Feedback> assessments = exercise.isAutomaticAssessmentEnabled() ? getAssessmentsForResultWithConflicts(result) : feedbackRepository.findByResult(result);
            result.setFeedbacks(assessments);
            if (assessments.isEmpty() && computeFeedbackSuggestions) {
                automaticTextFeedbackService.get().suggestFeedback(result);
            }
            result.setSubmission(textSubmission); // make sure this is not a Hibernate Proxy
        }
        else {
            // We are the first ones to open assess this submission, we want to lock it.
            result = new Result();
            result.setParticipation(participation);

            resultService.createNewRatedManualResult(result, false);
            result.setCompletionDate(null);
            result = resultRepository.save(result);
            result.setSubmission(textSubmission);
            textSubmission.addResult(result);
            submissionRepository.save(textSubmission);

            // If enabled, we want to compute feedback suggestions using Athene.
            if (computeFeedbackSuggestions) {
                automaticTextFeedbackService.get().suggestFeedback(result);
            }
        }

        // If we did not call AutomaticTextFeedbackService::suggestFeedback, we need to fetch them now.
        if (!result.getFeedbacks().isEmpty() || !computeFeedbackSuggestions) {
            final var textBlocks = textBlockService.findAllBySubmissionId(textSubmission.getId());
            textSubmission.setBlocks(textBlocks);
        }

        // If we did not fetch blocks from the database before, we fall back to computing them based on syntax.
        if (textSubmission.getBlocks() == null || !isInitialized(textSubmission.getBlocks()) || textSubmission.getBlocks().isEmpty()) {
            textBlockService.computeTextBlocksForSubmissionBasedOnSyntax(textSubmission);
        }

        // Remove participation after storing in database because submission already has the participation set
        result.setParticipation(null);

        // Set each block's impact on other submissions for the current 'textSubmission'
        if (computeFeedbackSuggestions) {
            textBlockService.setNumberOfAffectedSubmissionsPerBlock(result);
            result.setSubmission(textSubmission);
        }
    }

    private List<Feedback> getAssessmentsForResultWithConflicts(Result result) {
        List<Feedback> feedbackList = this.feedbackRepository.findByResult(result);
        final List<FeedbackConflict> allConflictsByFeedbackList = this.feedbackConflictRepository
                .findAllConflictsByFeedbackList(feedbackList.stream().map(Feedback::getId).toList());
        feedbackList.forEach(feedback -> {
            feedback.setFirstConflicts(allConflictsByFeedbackList.stream().filter(conflict -> conflict.getFirstFeedback().getId().equals(feedback.getId())).toList());
            feedback.setSecondConflicts(allConflictsByFeedbackList.stream().filter(conflict -> conflict.getSecondFeedback().getId().equals(feedback.getId())).toList());
        });
        return feedbackList;
    }
}

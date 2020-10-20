package de.tum.in.www1.artemis.service;

import static org.hibernate.Hibernate.isInitialized;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class TextAssessmentService extends AssessmentService {

    private final TextSubmissionRepository textSubmissionRepository;

    private final TextBlockService textBlockService;

    private final UserService userService;

    private final Optional<AutomaticTextFeedbackService> automaticTextFeedbackService;

    public TextAssessmentService(UserService userService, ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository,
            FeedbackRepository feedbackRepository, ResultRepository resultRepository, TextSubmissionRepository textSubmissionRepository,
            StudentParticipationRepository studentParticipationRepository, ResultService resultService, SubmissionRepository submissionRepository,
            TextBlockService textBlockService, Optional<AutomaticTextFeedbackService> automaticTextFeedbackService, ExamService examService,
            GradingCriterionService gradingCriterionService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionRepository, examService,
                gradingCriterionService);
        this.textSubmissionRepository = textSubmissionRepository;
        this.userService = userService;
        this.textBlockService = textBlockService;
        this.automaticTextFeedbackService = automaticTextFeedbackService;
    }

    /**
     * This function is used for manually assessed results. It updates the completion date, sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it
     * saves the assessment in the file system the total score is calculated and set in the result.
     *
     * @param resultId       the resultId the assessment belongs to
     * @param textExercise   the text exercise the assessment belongs to
     * @param textAssessment the assessments as a list
     * @return the ResponseEntity with result as body
     * @throws BadRequestAlertException on invalid feedback input
     */
    @Transactional
    public Result submitAssessment(Long resultId, TextExercise textExercise, List<Feedback> textAssessment) throws BadRequestAlertException {
        Result result = saveAssessment(resultId, textAssessment);
        Double calculatedScore = calculateTotalScore(textAssessment);
        return submitResult(result, textExercise, calculatedScore);
    }

    /**
     * This function is used for manually assessed results. It updates the completion date and sets the assessor attribute. Furthermore, it
     * saves the assessment in the file system the total score is calculated and set in the result.
     *
     * @param resultId       the resultId the assessment belongs to
     * @param textAssessment the assessments as string
     * @return the ResponseEntity with result as body
     * @throws BadRequestAlertException on invalid feedback input
     */
    @Transactional
    public Result saveAssessment(Long resultId, List<Feedback> textAssessment) throws BadRequestAlertException {

        final boolean hasAssessmentWithTooLongReference = textAssessment.stream().filter(Feedback::hasReference)
                .anyMatch(f -> f.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "textAssessment",
                    "feedbackReferenceTooLong");
        }

        Optional<Result> desiredResult = resultRepository.findById(resultId);
        Result result = desiredResult.orElseGet(Result::new);

        User user = userService.getUser();
        result.setAssessor(user);

        // TODO: how can the result be connected with the submission, if the result is newly created?
        // TODO: where is the relationship between result and participation established?

        if (result.getSubmission() instanceof TextSubmission && result.getSubmission().getResult() == null) {
            TextSubmission textSubmission = (TextSubmission) result.getSubmission();
            textSubmission.setResult(result);
            textSubmissionRepository.save(textSubmission);
        }

        // Note: If there is old feedback that gets removed here and not added again in the for-loop, it will also be
        // deleted in the database because of the 'orphanRemoval = true' flag.
        result.getFeedbacks().clear();
        for (Feedback feedback : textAssessment) {
            if (feedback.getCredits() == null) {
                feedback.setCredits(0.0);
            }
            feedback.setPositive(feedback.getCredits() >= 0);
            result.addFeedback(feedback);
        }
        result.setHasFeedback(false);
        result.determineAssessmentType();

        return resultRepository.save(result);
    }

    public List<Feedback> getAssessmentsForResult(Result result) {
        return this.feedbackRepository.findByResult(result);
    }

    /**
     * Load entities from database needed for text assessment & compute Feedback suggestions (Athene):
     *   1. Create or load the result
     *   2. Compute Feedback Suggestions
     *   3. Load Text Blocks
     *   4. Compute Fallback Text Blocks if needed
     *
     * @param textSubmission Text Submission to be assessed
     */
    public void prepareSubmissionForAssessment(TextSubmission textSubmission) {
        final Participation participation = textSubmission.getParticipation();
        final TextExercise exercise = (TextExercise) participation.getExercise();
        Result result = textSubmission.getResult();

        final boolean computeFeedbackSuggestions = automaticTextFeedbackService.isPresent() && exercise.isAutomaticAssessmentEnabled();

        if (result != null) {
            // Load Feedback already created for this assessment
            final List<Feedback> assessments = getAssessmentsForResult(result);
            result.setFeedbacks(assessments);
            if (assessments.isEmpty() && computeFeedbackSuggestions) {
                automaticTextFeedbackService.get().suggestFeedback(result);
            }
        }
        else {
            // We we are the first ones to open assess this submission, we want to lock it.
            result = new Result();
            result.setParticipation(participation);
            result.setSubmission(textSubmission);
            resultService.createNewRatedManualResult(result, false);
            result.setCompletionDate(null);
            result = resultRepository.save(result);
            textSubmission.setResult(result);

            // If enabled, we want to compute feedback suggestions using Athene.
            if (computeFeedbackSuggestions) {
                result.setSubmission(textSubmission); // make sure this is not a Hibernate Proxy
                automaticTextFeedbackService.get().suggestFeedback(result);
            }
        }

        // If we did not call AutomaticTextFeedbackService::suggestFeedback, we need to fetch them now.
        if (!result.getFeedbacks().isEmpty() || !computeFeedbackSuggestions) {
            final List<TextBlock> textBlocks = textBlockService.findAllBySubmissionId(textSubmission.getId());
            textSubmission.setBlocks(textBlocks);
        }

        // If we did not fetch blocks from the database before, we fall back to computing them based on syntax.
        if (textSubmission.getBlocks() == null || !isInitialized(textSubmission.getBlocks()) || textSubmission.getBlocks().isEmpty()) {
            textBlockService.computeTextBlocksForSubmissionBasedOnSyntax(textSubmission);
        }
    }
}

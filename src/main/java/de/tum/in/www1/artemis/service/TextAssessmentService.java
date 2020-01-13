package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class TextAssessmentService extends AssessmentService {

    private final TextSubmissionRepository textSubmissionRepository;

    private final UserService userService;

    public TextAssessmentService(UserService userService, ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository,
            FeedbackRepository feedbackRepository, ResultRepository resultRepository, TextSubmissionRepository textSubmissionRepository,
            StudentParticipationRepository studentParticipationRepository, ResultService resultService, AuthorizationCheckService authCheckService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, authCheckService);
        this.textSubmissionRepository = textSubmissionRepository;
        this.userService = userService;
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
        Result result = saveAssessment(resultId, textAssessment, textExercise);
        Double calculatedScore = calculateTotalScore(textAssessment);

        return submitResult(result, textExercise, calculatedScore);
    }

    /**
     * This function is used for manually assessed results. It updates the completion date and sets the assessor attribute. Furthermore, it
     * saves the assessment in the file system the total score is calculated and set in the result.
     *
     * @param resultId       the resultId the assessment belongs to
     * @param textAssessment the assessments as string
     * @param textExercise  the corresponding TextExercise
     * @return the ResponseEntity with result as body
     * @throws BadRequestAlertException on invalid feedback input
     */
    @Transactional
    public Result saveAssessment(Long resultId, List<Feedback> textAssessment, TextExercise textExercise) throws BadRequestAlertException {
        checkGeneralFeedback(textAssessment);

        final boolean hasAssessmentWithTooLongReference = textAssessment.stream().filter(Feedback::hasReference)
                .anyMatch(f -> f.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "textAssessment",
                    "feedbackReferenceTooLong");
        }

        Optional<Result> desiredResult = resultRepository.findById(resultId);
        Result result = desiredResult.orElseGet(Result::new);

        // check the assessment due date if the user tries to override an existing submitted result
        if (result.getCompletionDate() != null) {
            checkAssessmentDueDate(textExercise);
        }

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
     * Helper function to calculate the total score of a feedback list. It loops through all assessed model elements and sums the credits up.
     *
     * @param assessments the List of Feedback
     * @return the total score
     */
    // TODO CZ: move to AssessmentService class, as it's the same for modeling and text exercises (i.e. total score is sum of feedback credits) apart from rounding, but maybe also
    // good for text exercises?
    private Double calculateTotalScore(List<Feedback> assessments) {
        return assessments.stream().mapToDouble(Feedback::getCredits).sum();
    }
}

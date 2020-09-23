package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    private final UserService userService;

    public ProgrammingAssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            SubmissionRepository submissionRepository, ExamService examService, UserService userService) {
        super(complaintResponseService, complaintRepository, feedbackRepository, resultRepository, studentParticipationRepository, resultService, submissionRepository,
                examService);
        this.userService = userService;
    }

    /**
     * Handles an assessment update after a complaint. It first saves the corresponding complaint response and then updates the Result that was complaint about. Note, that it
     * updates the score and the feedback of the original Result, but NOT the assessor. The user that is responsible for the update can be found in the 'reviewer' field of the
     * complaint.
     *
     * @param originalResult   the original assessment that was complained about
     * @param exercise programming exercise
     * @param assessmentUpdate the assessment update
     * @return the updated Result
     */
    // NOTE: transactional makes sense here because we change multiple objects in the database and the changes might be invalid in case, one save operation fails
    @Transactional
    public Result updateAssessmentAfterComplaint(Result originalResult, Exercise exercise, ProgrammingAssessmentUpdate assessmentUpdate) {
        originalResult.setResultString(assessmentUpdate.getResultString());
        originalResult.setScore(assessmentUpdate.getScore());
        return super.updateAssessmentAfterComplaint(originalResult, exercise, assessmentUpdate);
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the
     * database.
     *
     * @param result the new result of a programming exercise
     * @return result that was saved in the database
     */
    @Transactional
    public Result saveManualAssessment(Result result) {
        result.setHasFeedback(!result.getFeedbacks().isEmpty());

        User user = userService.getUserWithGroupsAndAuthorities();
        result.setHasComplaint(false);
        result.setAssessmentType(AssessmentType.MANUAL);
        result.setAssessor(user);

        result.setCompletionDate(null);
        result.setRated(false);

        result.getFeedbacks().forEach(feedback -> {
            feedback.setResult(result);
        });

        // Note: This also saves the feedback objects in the database because of the 'cascade = CascadeType.ALL' option.
        return resultRepository.save(result);
    }

    /**
     * This function is used for submitting a manual assessment/result. It gets the result that belongs to the given resultId, updates the completion date, sets the assessment type
     * to MANUAL and sets the assessor attribute. Afterwards, it saves the update result in the database again.
     *
     * @param resultId the id of the result that should be submitted
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitManualAssessment(long resultId) {
        Result result = resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("No result for the given resultId could be found"));
        // Every manual assessed programming submission is rated
        result.setRated(true);
        result.setCompletionDate(ZonedDateTime.now());
        // TODO: Make it possible to give scores/points for manual results
        // Double calculatedScore = calculateTotalScore(result.getFeedbacks());
        // return submitResult(result, exercise, calculatedScore);

        return resultRepository.save(result);
    }
}

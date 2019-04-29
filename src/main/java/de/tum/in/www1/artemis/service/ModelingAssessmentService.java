package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.AssessmentUpdate;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ModelingAssessmentService extends AssessmentService {

    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final UserService userService;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ComplaintResponseService complaintResponseService;

    public ModelingAssessmentService(ResultRepository resultRepository, UserService userService, ModelingSubmissionRepository modelingSubmissionRepository,
                                     ComplaintResponseService complaintResponseService) {
        super(resultRepository);
        this.userService = userService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.complaintResponseService = complaintResponseService;
    }

    /**
     * This function is used for submitting a manual assessment/result. It updates the completion date, sets the assessment type to MANUAL and sets the assessor attribute.
     * Furthermore, it saves the result in the database.
     *
     * @param result   the result the assessment belongs to
     * @param exercise the exercise the assessment belongs to
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitManualAssessment(Result result, ModelingExercise exercise) {
        // TODO CZ: use AssessmentService#submitResult() function instead
        result.setRatedIfNotExceeded(exercise.getDueDate(), result.getSubmission().getSubmissionDate());
        result.setCompletionDate(ZonedDateTime.now());
        result.evaluateFeedback(exercise.getMaxScore()); // TODO CZ: move to AssessmentService class, as it's the same for
        // modeling and text exercises (i.e. total score is sum of feedback
        // credits)
        resultRepository.save(result);
        return result;
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the
     * database.
     *
     * @param modelingSubmission the modeling submission to which the feedback belongs to
     * @param modelingAssessment the assessment as a feedback list that should be added to the result of the corresponding submission
     */
    @Transactional
    public Result saveManualAssessment(ModelingSubmission modelingSubmission, List<Feedback> modelingAssessment) {
        Result result = modelingSubmission.getResult();
        if (result == null) {
            result = new Result();
        }

        result.setHasComplaint(false);
        result.setExampleResult(modelingSubmission.isExampleSubmission());
        result.setAssessmentType(AssessmentType.MANUAL);
        User user = userService.getUser();
        result.setAssessor(user);

        // Note: If there is old feedback that gets removed here and not added again in the for-loop, it
        // will also be
        // deleted in the database because of the 'orphanRemoval = true' flag.
        result.getFeedbacks().clear();
        for (Feedback feedback : modelingAssessment) {
            feedback.setPositive(feedback.getCredits() >= 0);
            feedback.setType(FeedbackType.MANUAL);
            result.addFeedback(feedback);
        }
        // Note: this boolean flag is only used for programming exercises
        result.setHasFeedback(false);

        if (result.getSubmission() == null) {
            result.setSubmission(modelingSubmission);
            modelingSubmission.setResult(result);
            modelingSubmissionRepository.save(modelingSubmission);
        }
        // Note: This also saves the feedback objects in the database because of the 'cascade =
        // CascadeType.ALL' option.
        return resultRepository.save(result);
    }

    /**
     * Handles an assessment update after a complaint. It first saves the corresponding complaint response and then
     * updates the Result that was complaint about.
     * Note, that it updates the score and the feedback of the original Result, but NOT the assessor. The user that is
     * responsible for the update can be found in the 'reviewer' field of the complaint. The original Result is stored
     * in the 'resultBeforeComplaint' field of the ComplaintResponse for future lookup.
     *
     * @param modelingSubmission the modeling submission the assessment that was complained about belongs to
     * @param assessmentUpdate the assessment update containing a ComplaintResponse and the updated Feedback list
     * @return the updated Result
     */
    @Transactional
    public Result updateAssessmentAfterComplaint(ModelingSubmission modelingSubmission, AssessmentUpdate assessmentUpdate) {
        if (assessmentUpdate.getFeedbacks() == null || assessmentUpdate.getComplaintResponse() == null) {
            throw new BadRequestAlertException("Feedbacks and complaint response must not be null.", "AssessmentUpdate", "notnull");
        }
        // save the corresponding complaint response
        ComplaintResponse complaintResponse = complaintResponseService.createComplaintResponse(assessmentUpdate.getComplaintResponse());
        // and update the result that was complained about
        // TODO CZ: implement assessment update
        return new Result();
    }

    /**
     * Gets an example modeling submission with the given submissionId and returns the result of the submission.
     *
     * @param submissionId the id of the example modeling submission
     * @return the result of the submission
     * @throws EntityNotFoundException when no submission can be found for the given id
     */
    @Transactional(readOnly = true)
    public Result getExampleAssessment(long submissionId) {
        Optional<ModelingSubmission> optionalModelingSubmission = modelingSubmissionRepository.findExampleSubmissionByIdWithEagerResult(submissionId);
        ModelingSubmission modelingSubmission = optionalModelingSubmission
                .orElseThrow(() -> new EntityNotFoundException("Example Submission with id \"" + submissionId + "\" does not exist"));
        return modelingSubmission.getResult();
    }
}

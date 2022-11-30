package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AssessmentService;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/** REST controller for managing ModelingAssessment. */
@RestController
@RequestMapping("/api")
public class ModelingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentResource.class);

    private static final String ENTITY_NAME = "modelingAssessment";

    private static final String PUT_SUBMIT_ASSESSMENT_200_REASON = "Given assessment has been saved and used for automatic assessment by Compass";

    private static final String POST_ASSESSMENT_AFTER_COMPLAINT_200_REASON = "Assessment has been updated after complaint";

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    public ModelingAssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ModelingExerciseRepository modelingExerciseRepository,
            AssessmentService assessmentService, ModelingSubmissionRepository modelingSubmissionRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            WebsocketMessagingService messagingService, ExerciseRepository exerciseRepository, ResultRepository resultRepository, ExamService examService,
            SubmissionRepository submissionRepository, SingleUserNotificationService singleUserNotificationService) {
        super(authCheckService, userRepository, exerciseRepository, assessmentService, resultRepository, examService, messagingService, exampleSubmissionRepository,
                submissionRepository, singleUserNotificationService);
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.authCheckService = authCheckService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
    }

    /**
     * Get the result of the modeling submission with the given id. See {@link AssessmentResource#getAssessmentBySubmissionId}.
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @return the assessment of the given submission
     */
    @GetMapping("/modeling-submissions/{submissionId}/result")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Result> getAssessmentBySubmissionId(@PathVariable Long submissionId) {
        return super.getAssessmentBySubmissionId(submissionId);
    }

    /**
     * Retrieve the result for an example submission, only if the user is an instructor or if the example submission is not used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the example submission
     * @return the result linked to the example submission
     */
    @GetMapping("/exercise/{exerciseId}/modeling-submissions/{submissionId}/example-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> getModelingExampleAssessment(@PathVariable long exerciseId, @PathVariable long submissionId) {
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        return super.getExampleAssessment(exerciseId, submissionId);
    }

    /**
     * PUT modeling-submissions/:submissionId/result/resultId/assessment : save manual modeling assessment. See {@link AssessmentResource#saveAssessment}.
     *
     * @param submissionId id of the submission
     * @param resultId id of the result
     * @param feedbacks list of feedbacks
     * @param submit if true the assessment is submitted, else only saved
     * @return result after saving/submitting modeling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = PUT_SUBMIT_ASSESSMENT_200_REASON, content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Result.class)) }),
            @ApiResponse(responseCode = "403", description = ErrorConstants.REQ_403_REASON), @ApiResponse(responseCode = "404", description = ErrorConstants.REQ_404_REASON) })
    @PutMapping("/modeling-submissions/{submissionId}/result/{resultId}/assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> saveModelingAssessment(@PathVariable long submissionId, @PathVariable long resultId,
            @RequestParam(value = "submit", defaultValue = "false") boolean submit, @RequestBody List<Feedback> feedbacks) {
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedback(submissionId);
        return super.saveAssessment(submission, submit, feedbacks, resultId);
    }

    /**
     * PUT modeling-submissions/:submissionId/example-assessment : save manual example modeling assessment
     *
     * @param exampleSubmissionId id of the example submission
     * @param feedbacks list of feedbacks
     * @return result after saving example modeling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = PUT_SUBMIT_ASSESSMENT_200_REASON, content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Result.class)) }),
            @ApiResponse(responseCode = "403", description = ErrorConstants.REQ_403_REASON), @ApiResponse(responseCode = "404", description = ErrorConstants.REQ_404_REASON) })
    @PutMapping("/modeling-submissions/{exampleSubmissionId}/example-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> saveModelingExampleAssessment(@PathVariable long exampleSubmissionId, @RequestBody List<Feedback> feedbacks) {
        log.debug("REST request to save modeling example assessment : {}", exampleSubmissionId);
        return super.saveExampleAssessment(exampleSubmissionId, feedbacks);
    }

    /**
     * Update an assessment after a complaint was accepted. After the result is updated accordingly, Compass is notified about the changed assessment in order to adapt all
     * automatic assessments based on this result, as well.
     *
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the assessment update containing the new feedback items and the response to the complaint
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = POST_ASSESSMENT_AFTER_COMPLAINT_200_REASON, content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = Result.class)) }),
            @ApiResponse(responseCode = "403", description = ErrorConstants.REQ_403_REASON), @ApiResponse(responseCode = "404", description = ErrorConstants.REQ_404_REASON) })
    @PutMapping("/modeling-submissions/{submissionId}/assessment-after-complaint")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> updateModelingAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody AssessmentUpdate assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ModelingSubmission modelingSubmission = modelingSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(submissionId);
        long exerciseId = modelingSubmission.getParticipation().getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(modelingExercise, user);

        Result result = assessmentService.updateAssessmentAfterComplaint(modelingSubmission.getLatestResult(), modelingExercise, assessmentUpdate);

        var participation = result.getParticipation();
        // remove circular dependencies if the results of the participation are there
        if (participation != null && Hibernate.isInitialized(participation.getResults()) && participation.getResults() != null) {
            participation.setResults(null);
        }

        if (participation instanceof StudentParticipation studentParticipation && !authCheckService.isAtLeastInstructorForExercise(modelingExercise, user)) {
            studentParticipation.setParticipant(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PutMapping("/modeling-submissions/{submissionId}/cancel-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long submissionId) {
        return super.cancelAssessment(submissionId);
    }

    /**
     * Delete an assessment of a given submission.
     *
     * @param participationId - the id of the participation to the submission
     * @param submissionId - the id of the submission for which the current assessment should be deleted
     * @param resultId     - the id of the result which should get deleted
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not an instructor of the course or an admin
     */
    @DeleteMapping("/participations/{participationId}/modeling-submissions/{submissionId}/results/{resultId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}

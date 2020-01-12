package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelAssessmentConflict;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/** REST controller for managing ModelingAssessment. */
@RestController
@RequestMapping("/api")
public class ModelingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentResource.class);

    private static final String ENTITY_NAME = "modelingAssessment";

    private static final String PUT_ASSESSMENT_409_REASON = "Given assessment conflicts with existing assessments in the database. Assessment has been stored but is not used for automatic assessment by compass";

    private static final String PUT_SUBMIT_ASSESSMENT_200_REASON = "Given assessment has been saved and used for automatic assessment by Compass";

    private static final String POST_ASSESSMENT_AFTER_COMPLAINT_200_REASON = "Assessment has been updated after complaint";

    private final CompassService compassService;

    private final ModelingExerciseService modelingExerciseService;

    private final AuthorizationCheckService authCheckService;

    private final ModelingAssessmentService modelingAssessmentService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final ExampleSubmissionService exampleSubmissionService;

    private final WebsocketMessagingService messagingService;

    public ModelingAssessmentResource(AuthorizationCheckService authCheckService, UserService userService, CompassService compassService,
            ModelingExerciseService modelingExerciseService, ModelingAssessmentService modelingAssessmentService, ModelingSubmissionService modelingSubmissionService,
            ExampleSubmissionService exampleSubmissionService, WebsocketMessagingService messagingService) {
        super(authCheckService, userService);
        this.compassService = compassService;
        this.modelingExerciseService = modelingExerciseService;
        this.authCheckService = authCheckService;
        this.modelingAssessmentService = modelingAssessmentService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.messagingService = messagingService;
        this.exampleSubmissionService = exampleSubmissionService;
    }

    /**
     * GET modeling-submissions/:submissionId/partial-assessment : get a partial assessment for modeling submission
     *
     * @param submissionId id of the submission
     * @return partial assessment for specified submission
     */
    @GetMapping("/modeling-submissions/{submissionId}/partial-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')") // TODO MJ better path
    // "/modeling-submissions/{submissionId}/result"?
    // TODO MJ merge with getAssessmentBySubmissionId() ?
    // Note: This endpoint is currently not used and not fully tested after migrating UML models and modeling
    // submissions from file system to database.
    public ResponseEntity<Result> getPartialAssessment(@PathVariable Long submissionId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        ModelingSubmission submission = modelingSubmissionService.findOneWithEagerResult(submissionId);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(participation.getExercise().getId());
        checkAuthorization(modelingExercise, user);
        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            List<Feedback> partialFeedbackAssessment = compassService.getPartialAssessment(participation.getExercise().getId(), submission);
            Result result = submission.getResult();
            if (result != null) {
                result.getFeedbacks().clear();
                result.getFeedbacks().addAll(partialFeedbackAssessment);
                return ResponseEntity.ok(result);
            }
            else {
                return notFound();
            }
        }
        else {
            return notFound();
        }
    }

    /**
     * Get the result of the modeling submission with the given id. Returns a 403 Forbidden response if the user is not allowed to retrieve the assessment. The user is not allowed
     * to retrieve the assessment if he is not a student of the corresponding course, the submission is not his submission, the result is not finished or the assessment due date of
     * the corresponding exercise is in the future (or not set).
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @return the submission with the given id
     */
    @GetMapping("/modeling-submissions/{submissionId}/result")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getAssessmentBySubmissionId(@PathVariable Long submissionId) {
        log.debug("REST request to get assessment for submission with id {}", submissionId);
        ModelingSubmission submission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        Exercise exercise = participation.getExercise();

        Result result = submission.getResult();
        if (result == null) {
            return notFound();
        }

        if (!authCheckService.isUserAllowedToGetResult(exercise, participation, result)) {
            return forbidden();
        }

        // remove sensitive information for students
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            exercise.filterSensitiveInformation();
            result.setAssessor(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve the result for an example submission, only if the user is an instructor or if the example submission is not used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the example submission
     * @return the result linked to the example submission
     */
    @GetMapping("/exercise/{exerciseId}/submission/{submissionId}/modelingExampleAssessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getExampleAssessment(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        ExampleSubmission exampleSubmission = exampleSubmissionService.findOneBySubmissionId(submissionId);

        // It is allowed to get the example assessment, if the user is an instructor or
        // if the user is a tutor and the submission is not used for tutorial in the tutor dashboard
        boolean isAllowed = authCheckService.isAtLeastInstructorForExercise(modelingExercise)
                || authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise) && !exampleSubmission.isUsedForTutorial();
        if (!isAllowed) {
            forbidden();
        }

        return ResponseEntity.ok(modelingAssessmentService.getExampleAssessment(submissionId));
    }

    /**
     * PUT modeling-submissions/:submissionId/feedback : save manual modeling assessment
     *
     * @param submissionId id of the submission
     * @param feedbacks list of feedbacks
     * @param ignoreConflict currently not used
     * @param submit if true the assessment is submitted, else only saved
     * @return result after saving/submitting modeling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 200, message = PUT_SUBMIT_ASSESSMENT_200_REASON, response = Result.class),
            @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
            @ApiResponse(code = 409, message = PUT_ASSESSMENT_409_REASON, response = ModelAssessmentConflict.class, responseContainer = "List") })
    @PutMapping("/modeling-submissions/{submissionId}/feedback")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO MJ changing submitted assessment always produces Conflict
    public ResponseEntity<Object> saveModelingAssessment(@PathVariable Long submissionId, @RequestParam(value = "ignoreConflicts", defaultValue = "false") boolean ignoreConflict,
            @RequestParam(value = "submit", defaultValue = "false") boolean submit, @RequestBody List<Feedback> feedbacks) {
        User user = userService.getUserWithGroupsAndAuthorities();
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise, user);

        Result result = modelingAssessmentService.saveManualAssessment(modelingSubmission, feedbacks, modelingExercise);

        if (submit) {
            // SK: deactivate conflict handling for now, because it is not fully implemented yet.
            // List<ModelAssessmentConflict> conflicts = new ArrayList<>();
            // if (compassService.isSupported(modelingExercise.getDiagramType())) {
            // try {
            // conflicts = compassService.getConflicts(modelingSubmission, exerciseId, result, result.getFeedbacks());
            // }
            // catch (Exception ex) { // catch potential null pointer exceptions as they should not prevent submitting an assessment
            // log.warn("Exception occurred when trying to get conflicts for model with submission id " + modelingSubmission.getId(), ex);
            // }
            // }
            // if (!conflicts.isEmpty() && !ignoreConflict) {
            // conflictService.loadSubmissionsAndFeedbacksAndAssessorOfConflictingResults(conflicts);
            // return ResponseEntity.status(HttpStatus.CONFLICT).body(conflicts);
            // }
            // else {
            result = modelingAssessmentService.submitManualAssessment(result.getId(), modelingExercise, modelingSubmission.getSubmissionDate());
            if (compassService.isSupported(modelingExercise.getDiagramType())) {
                compassService.addAssessment(exerciseId, submissionId, result.getFeedbacks());
            }
            // }
        }
        // remove information about the student for tutors to ensure double-blind assessment
        if (!authCheckService.isAtLeastInstructorForExercise(modelingExercise, user)) {
            ((StudentParticipation) result.getParticipation()).setStudent(null);
        }
        if (submit && ((result.getParticipation()).getExercise().getAssessmentDueDate() == null
                || (result.getParticipation()).getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now()))) {
            messagingService.broadcastNewResult(result.getParticipation(), result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * PUT modeling-submissions/:exampleSubmissionId/exampleAssessment : save manual example modeling assessment
     *
     * @param exampleSubmissionId id of the submission
     * @param feedbacks list of feedbacks
     * @return result after saving example modeling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 200, message = PUT_SUBMIT_ASSESSMENT_200_REASON, response = Result.class),
            @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
            @ApiResponse(code = 409, message = PUT_ASSESSMENT_409_REASON, response = ModelAssessmentConflict.class, responseContainer = "List") })
    @PutMapping("/modeling-submissions/{exampleSubmissionId}/exampleAssessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Object> saveModelingExampleAssessment(@PathVariable Long exampleSubmissionId, @RequestBody List<Feedback> feedbacks) {
        User user = userService.getUserWithGroupsAndAuthorities();
        ExampleSubmission exampleSubmission = exampleSubmissionService.findOneWithEagerResult(exampleSubmissionId);
        ModelingSubmission modelingSubmission = (ModelingSubmission) exampleSubmission.getSubmission();
        ModelingExercise modelingExercise = (ModelingExercise) exampleSubmission.getExercise();
        checkAuthorization(modelingExercise, user);
        Result result = modelingAssessmentService.saveManualAssessment(modelingSubmission, feedbacks, modelingExercise);
        return ResponseEntity.ok(result);
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
    @ApiResponses({ @ApiResponse(code = 200, message = POST_ASSESSMENT_AFTER_COMPLAINT_200_REASON, response = Result.class),
            @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON) })
    @PutMapping("/modeling-submissions/{submissionId}/assessment-after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateModelingAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody AssessmentUpdate assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userService.getUserWithGroupsAndAuthorities();
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise, user);

        Result result = modelingAssessmentService.updateAssessmentAfterComplaint(modelingSubmission.getResult(), modelingExercise, assessmentUpdate);

        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            compassService.addAssessment(exerciseId, submissionId, result.getFeedbacks());
        }

        // remove circular dependencies if the results of the participation are there
        if (result.getParticipation() != null && Hibernate.isInitialized(result.getParticipation().getResults()) && result.getParticipation().getResults() != null) {
            result.getParticipation().setResults(null);
        }

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(modelingExercise, user)) {
            ((StudentParticipation) result.getParticipation()).setStudent(null);
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
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity cancelAssessment(@PathVariable Long submissionId) {
        log.debug("REST request to cancel assessment of submission: {}", submissionId);
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOneWithEagerResult(submissionId);
        if (modelingSubmission.getResult() == null) {
            // if there is no result everything is fine
            return ResponseEntity.ok().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise, user);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(modelingExercise, user);
        if (!(isAtLeastInstructor || userService.getUser().getId().equals(modelingSubmission.getResult().getAssessor().getId()))) {
            // tutors cannot cancel the assessment of other tutors (only instructors can)
            return forbidden();
        }
        modelingAssessmentService.cancelAssessmentOfSubmission(modelingSubmission);
        return ResponseEntity.ok().build();
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}

package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/** REST controller for managing ModelingAssessment. */
@RestController
@RequestMapping("/api")
public class ModelingAssessmentResource extends AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentResource.class); // TODO MJ add logging or remove unused logger

    private static final String ENTITY_NAME = "modelingAssessment";

    private static final String PUT_ASSESSMENT_409_REASON = "Given assessment conflicts with existing assessments in the database. Assessment has been stored but is not used for automatic assessment by compass";

    private static final String PUT_ASSESSMENT_200_REASON = "Given assessment has been saved but is not used for automatic assessment by Compass";

    private static final String PUT_SUBMIT_ASSESSMENT_200_REASON = "Given assessment has been saved and used for automatic assessment by Compass";

    private static final String POST_ASSESSMENT_AFTER_COMPLAINT_200_REASON = "Assessment has been updated after complaint";

    private final CompassService compassService;

    private final ModelingExerciseService modelingExerciseService;

    private final AuthorizationCheckService authCheckService;

    private final CourseService courseService;

    private final ModelingAssessmentService modelingAssessmentService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final ExampleSubmissionService exampleSubmissionService;

    private final SimpMessageSendingOperations messagingTemplate;

    public ModelingAssessmentResource(AuthorizationCheckService authCheckService, UserService userService, CompassService compassService,
            ModelingExerciseService modelingExerciseService, AuthorizationCheckService authCheckService1, CourseService courseService,
            ModelingAssessmentService modelingAssessmentService, ModelingSubmissionService modelingSubmissionService, ExampleSubmissionService exampleSubmissionService,
            SimpMessageSendingOperations messagingTemplate) {
        super(authCheckService, userService);
        this.compassService = compassService;
        this.modelingExerciseService = modelingExerciseService;
        this.authCheckService = authCheckService1;
        this.courseService = courseService;
        this.exampleSubmissionService = exampleSubmissionService;
        this.modelingAssessmentService = modelingAssessmentService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/modeling-submissions/{submissionId}/partial-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')") // TODO MJ better path
    // "/modeling-submissions/{submissionId}/result"?
    // TODO MJ merge with getAssessmentBySubmissionId() ?
    // Note: This endpoint is currently not used and not fully tested after migrating UML models and
    // modeling
    // submissions from file system to database.
    public ResponseEntity<Result> getPartialAssessment(@PathVariable Long submissionId) {
        ModelingSubmission submission = modelingSubmissionService.findOneWithEagerResult(submissionId);
        Participation participation = submission.getParticipation();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(participation.getExercise().getId());
        checkAuthorization(modelingExercise);
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

    @GetMapping("/modeling-submissions/{submissionId}/result")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getAssessmentBySubmissionId(@PathVariable Long submissionId) {
        ModelingSubmission submission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        Participation participation = submission.getParticipation();
        Exercise exercise = participation.getExercise();
        if (!courseService.userHasAtLeastStudentPermissions(exercise.getCourse()) || !authCheckService.isOwnerOfParticipation(participation)) {
            return forbidden();
        }
        Result result = submission.getResult();
        if (result == null) {
            return notFound();
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
    @Transactional
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

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 200, message = PUT_SUBMIT_ASSESSMENT_200_REASON, response = Result.class),
            @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
            @ApiResponse(code = 409, message = PUT_ASSESSMENT_409_REASON, response = Conflict.class, responseContainer = "List") })
    @PutMapping("/modeling-submissions/{submissionId}/feedback")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO MJ changing submitted assessment always produces Conflict
    public ResponseEntity<Object> saveModelingAssessment(@PathVariable Long submissionId, @RequestParam(value = "ignoreConflicts", defaultValue = "false") boolean ignoreConflict,
            @RequestParam(value = "submit", defaultValue = "false") boolean submit, @RequestBody List<Feedback> feedbacks) {
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        long exerciseId = modelingSubmission.getParticipation().getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);

        Result result = modelingAssessmentService.saveManualAssessment(modelingSubmission, feedbacks, modelingExercise);
        // TODO CZ: move submit logic to modeling assessment service
        if (submit) {
            List<Conflict> conflicts = new ArrayList<>();
            if (compassService.isSupported(modelingExercise.getDiagramType())) {
                try {
                    conflicts = compassService.getConflicts(modelingSubmission, exerciseId, result, result.getFeedbacks());
                }
                catch (Exception ex) { // catch potential null pointer exceptions as they should not prevent submitting an assessment
                    log.warn("Exception occurred when trying to get conflicts for model with submission id " + modelingSubmission.getId(), ex);
                }
            }
            if (!conflicts.isEmpty() && !ignoreConflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(conflicts);
            }
            else {
                modelingAssessmentService.submitManualAssessment(result, modelingExercise, modelingSubmission.getSubmissionDate());
                if (compassService.isSupported(modelingExercise.getDiagramType())) {
                    compassService.addAssessment(exerciseId, submissionId, feedbacks);
                }
            }
        }
        // remove information about the student for tutors to ensure double-blind assessment
        if (!authCheckService.isAtLeastInstructorForExercise(modelingExercise)) {
            result.getParticipation().setStudent(null);
        }
        if (submit && (result.getParticipation().getExercise().getAssessmentDueDate() == null
                || result.getParticipation().getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now()))) {
            messagingTemplate.convertAndSend("/topic/participation/" + result.getParticipation().getId() + "/newResults", result);
        }
        return ResponseEntity.ok(result);
    }

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 200, message = PUT_SUBMIT_ASSESSMENT_200_REASON, response = Result.class),
            @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
            @ApiResponse(code = 409, message = PUT_ASSESSMENT_409_REASON, response = Conflict.class, responseContainer = "List") })
    @PutMapping("/modeling-submissions/{exampleSubmissionId}/exampleAssessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<Object> saveModelingExampleAssessment(@PathVariable Long exampleSubmissionId, @RequestBody List<Feedback> feedbacks) {
        ExampleSubmission exampleSubmission = exampleSubmissionService.findOneWithEagerResult(exampleSubmissionId);
        ModelingSubmission modelingSubmission = (ModelingSubmission) exampleSubmission.getSubmission();
        ModelingExercise modelingExercise = (ModelingExercise) exampleSubmission.getExercise();
        checkAuthorization(modelingExercise);
        Result result = modelingAssessmentService.saveManualAssessment(modelingSubmission, feedbacks, modelingExercise);
        return ResponseEntity.ok(result);
    }

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 200, message = POST_ASSESSMENT_AFTER_COMPLAINT_200_REASON, response = Result.class),
            @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON) })
    @PostMapping("/modeling-submissions/{submissionId}/assessment-after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateModelingAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody AssessmentUpdate assessmentUpdate) {
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        long exerciseId = modelingSubmission.getParticipation().getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        Result result = modelingAssessmentService.updateAssessmentAfterComplaint(modelingSubmission.getResult(), modelingExercise, assessmentUpdate);
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
        if (!userService.getUser().getId().equals(modelingSubmission.getResult().getAssessor().getId())) {
            // you cannot cancel the assessment of other tutors
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

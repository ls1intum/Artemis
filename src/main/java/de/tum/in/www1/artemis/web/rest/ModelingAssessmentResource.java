package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

/**
 * REST controller for managing ModelingAssessment.
 */
@RestController
@RequestMapping("/api")
public class ModelingAssessmentResource extends AssessmentResource {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentResource.class); //TODO MJ add logging or remove unused logger

    private static final String ENTITY_NAME = "modelingAssessment";
    private static final String PUT_ASSESSMENT_409_REASON = "Given assessment conflicts with exsisting assessments in the database. Assessment has been stored but is not used for automatic assessment by compass";
    private static final String PUT_ASSESSMENT_200_REASON = "Given assessment has been saved but is not used for automatic assessment by Compass";
    private static final String PUT_SUBMIT_ASSESSMENT_200_REASON = "Given assessment has been saved and used for automatic assessment by Compass";

    private final CompassService compassService;
    private final ModelingExerciseService modelingExerciseService;
    private final AuthorizationCheckService authCheckService;
    private final CourseService courseService;
    private final ModelingAssessmentService modelingAssessmentService;
    private final ModelingSubmissionService modelingSubmissionService;


    public ModelingAssessmentResource(
        AuthorizationCheckService authCheckService,
        UserService userService,
        CompassService compassService,
        ModelingExerciseService modelingExerciseService,
        AuthorizationCheckService authCheckService1,
        CourseService courseService,
        ModelingAssessmentService modelingAssessmentService,
        ModelingSubmissionService modelingSubmissionService) {
        super(authCheckService, userService);
        this.compassService = compassService;
        this.modelingExerciseService = modelingExerciseService;
        this.authCheckService = authCheckService1;
        this.courseService = courseService;
        this.modelingAssessmentService = modelingAssessmentService;
        this.modelingSubmissionService = modelingSubmissionService;
    }


    @DeleteMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> resetOptimalModels(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        compassService.resetModelsWaitingForAssessment(exerciseId);
        return ResponseEntity.noContent().build();
    }


    //TODO MJ add api documentation (returns list of submission ids as array)
    @GetMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<Long[]> getNextOptimalModelSubmissions(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        // TODO: we need to make sure that per participation there is only one optimalModel
        Set<Long> optimalModelSubmissions = compassService.getModelsWaitingForAssessment(exerciseId);
        if (optimalModelSubmissions.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(optimalModelSubmissions.toArray(new Long[]{}));
    }


    @GetMapping("/modeling-submissions/{submissionId}/partial-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
//TODO MJ better path "/modeling-submissions/{submissionId}/result"?
    // TODO MJ merge with getAssessmentBySubmissionId() ?
    // Note: This endpoint is currently not used and not fully tested after migrating UML models and modeling
    //       submissions from file system to database.
    public ResponseEntity<Result> getPartialAssessment(@PathVariable Long submissionId) {
        ModelingSubmission submission = modelingSubmissionService.findOneWithEagerResult(submissionId);
        Participation participation = submission.getParticipation();
        ModelingExercise modelingExercise =
            modelingExerciseService.findOne(participation.getExercise().getId());
        checkAuthorization(modelingExercise);
        List<Feedback> partialFeedbackAssessment =
            compassService.getPartialAssessment(participation.getExercise().getId(), submission);
        Result result = submission.getResult();
        if (result != null) {
            result.getFeedbacks().clear();
            result.getFeedbacks().addAll(partialFeedbackAssessment);
            return ResponseEntity.ok(result);
        } else {
            return notFound();
        }
    }


    @GetMapping("/modeling-submissions/{submissionId}/result")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getAssessmentBySubmissionId(@PathVariable Long submissionId) {
        ModelingSubmission submission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        Participation participation = submission.getParticipation();
        if (!courseService.userHasAtLeastStudentPermissions(participation.getExercise().getCourse())
            || !authCheckService.isOwnerOfParticipation(participation)) {
            return forbidden();
        }
        Result result = submission.getResult();
        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return notFound();
        }
    }


    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({
        @ApiResponse(code = 200, message = PUT_SUBMIT_ASSESSMENT_200_REASON, response = Result.class),
        @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON),
        @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
        @ApiResponse(
            code = 409,
            message = PUT_ASSESSMENT_409_REASON,
            response = Conflict.class,
            responseContainer = "List")
    })
    @PutMapping("/modeling-submissions/{submissionId}/feedback")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO MJ changing submitted assessment always produces Conflict
    @Transactional
    public ResponseEntity<Object> saveModelingAssessment(
        @PathVariable Long submissionId,
        @RequestParam(value = "ignoreConflicts", defaultValue = "false") boolean ignoreConflict,
        @RequestParam(value = "submit", defaultValue = "false") boolean submit,
        @RequestBody List<Feedback> feedbacks) {
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        long exerciseId = modelingSubmission.getParticipation().getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        Result result = modelingAssessmentService.saveManualAssessment(modelingSubmission, feedbacks);
        // TODO CZ: move submit logic to modeling assessment service
        if (submit) {
            List<ModelAssessmentConflict> conflicts =
                compassService.getConflicts(exerciseId, result, result.getFeedbacks());
            if (!conflicts.isEmpty() && !ignoreConflict) {

                return ResponseEntity.status(HttpStatus.CONFLICT).body(conflicts);
            } else {
                modelingAssessmentService.submitManualAssessment(result, modelingExercise);
                compassService.addAssessment(exerciseId, submissionId, feedbacks);
            }
        }
        return ResponseEntity.ok(result);
    }


    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}

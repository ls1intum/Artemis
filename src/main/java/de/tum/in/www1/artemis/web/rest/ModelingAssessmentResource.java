package de.tum.in.www1.artemis.web.rest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getNextOptimalModelSubmissions(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);

        // TODO: we need to make sure that per participation there is only one optimalModel
        Set<Long> optimalModelSubmissions = compassService.getModelsWaitingForAssessment(exerciseId);
        JsonArray response = new JsonArray();
        for (Long optimalModelSubmissionId : optimalModelSubmissions) {
            JsonObject entry = new JsonObject();
            response.add(entry);
            entry.addProperty("id", optimalModelSubmissionId);
        }
        return ResponseEntity.ok(response.toString());
    }

    @GetMapping("submissions/{submissionId}/partial-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getPartialAssessment(@PathVariable Long submissionId) {
        ModelingSubmission submission = modelingSubmissionService.findOne(submissionId);
        Participation participation = submission.getParticipation();
        ModelingExercise modelingExercise =
            modelingExerciseService.findOne(participation.getExercise().getId());
        checkAuthorization(modelingExercise);
        if (submission.getResult() instanceof HibernateProxy) {
            submission.setResult((Result) Hibernate.unproxy(submission.getResult()));
        }
        List<Feedback> partialFeedbackAssessment = compassService.getPartialAssessment(participation.getExercise().getId(), submissionId);
        Result result = submission.getResult();
        result.setFeedbacks(partialFeedbackAssessment);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/submissions/{submissionId}/modeling-assessment")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getAssessmentBySubmissionId(@PathVariable Long submissionId) {
        ModelingSubmission submission = modelingSubmissionService.findOne(submissionId);
        Participation participation = submission.getParticipation();
        if (!courseService.userHasAtLeastStudentPermissions(participation.getExercise().getCourse())
            || !authCheckService.isOwnerOfParticipation(participation)) {
            return forbidden();
        }
        if (submission.getResult() instanceof HibernateProxy) {
            submission.setResult((Result) Hibernate.unproxy(submission.getResult()));
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
    // TODO: in this case we should already send the feedback items in the Result object
    public ResponseEntity<Object> submitModelingAssessment(
        @PathVariable Long submissionId,
        @RequestParam(value = "ignoreConflicts", defaultValue = "false") boolean ignoreConflict,
        @RequestParam(value = "submit", defaultValue = "false") boolean submit,
        @RequestBody List<Feedback> feedbacks) {
        ModelingSubmission submission = modelingSubmissionService.findOne(submissionId);
        long exerciseId = submission.getParticipation().getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        Result result = submission.getResult();
        modelingAssessmentService.saveManualAssessment(result);
        if (submit) {
            List<Conflict> conflicts =
                compassService.getConflicts(exerciseId, submissionId, result.getFeedbacks());
            if (!conflicts.isEmpty() && !ignoreConflict) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(conflicts);
            } else {
                modelingAssessmentService.submitManualAssessment(result, modelingExercise);
//                compassService.addAssessment(exerciseId, submissionId, modelingAssessment);
            }
        }
        return ResponseEntity.ok(result);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}

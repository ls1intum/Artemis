package de.tum.in.www1.artemis.web.rest;

import java.util.*;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.google.gson.*;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.service.compass.assessment.ModelElementAssessment;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.swagger.annotations.*;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

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
    private static final String REQ_404_REASON = "Requested ressource does not exist.";
    private static final String REQ_403_REASON = "Unsufficient permission to perform this request";

    private final JsonAssessmentRepository jsonAssessmentRepository;
    private final ParticipationRepository participationRepository;
    private final ResultService resultService;
    private final CompassService compassService;
    private final ModelingExerciseService modelingExerciseService;
    private final AuthorizationCheckService authCheckService;
    private final CourseService courseService;
    private final ModelingAssessmentService modelingAssessmentService;


    //TODO: all API path in this class do not really make sense, we should restructure them and potentially start with /exercise/
    public ModelingAssessmentResource(AuthorizationCheckService authCheckService, UserService userService, JsonAssessmentRepository jsonAssessmentRepository, ParticipationRepository participationRepository, ResultService resultService, CompassService compassService, ModelingExerciseService modelingExerciseService, CourseService courseService, ModelingAssessmentService modelingAssessmentService) {
        super(authCheckService, userService);
        this.jsonAssessmentRepository = jsonAssessmentRepository;
        this.participationRepository = participationRepository;
        this.resultService = resultService;
        this.compassService = compassService;
        this.modelingExerciseService = modelingExerciseService;
        this.authCheckService = authCheckService;
        this.courseService = courseService;
        this.modelingAssessmentService = modelingAssessmentService;
    }


    @DeleteMapping("/modeling-assessments/exercise/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> resetOptimalModels(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        compassService.resetModelsWaitingForAssessment(exerciseId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }


    @GetMapping("/modeling-assessments/exercise/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getNextOptimalModelSubmissions(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);

        //TODO: we need to make sure that per participation there is only one optimalModel
        Set<Long> optimalModelSubmissions = compassService.getModelsWaitingForAssessment(exerciseId);
        JsonArray response = new JsonArray();
        for (Long optimalModelSubmissionId : optimalModelSubmissions) {
            JsonObject entry = new JsonObject();
            response.add(entry);
            entry.addProperty("id", optimalModelSubmissionId);
        }
        return ResponseEntity.ok(response.toString());
    }


    @GetMapping("/modeling-assessments/exercise/{exerciseId}/submission/{submissionId}/partial-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getPartialAssessment(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        JsonObject partialAssessment = compassService.getPartialAssessment(exerciseId, submissionId);
        return ResponseEntity.ok(partialAssessment.get("assessments").toString());
    }


    /**
     * Returns assessments (if found) for a given participationId and submissionId.
     *
     * @param participationId the participationId for which to find assessments for
     * @param submissionId    the submissionId for which to find assessments for
     * @return the ResponseEntity with assessments string as body
     */
    @GetMapping("/modeling-assessments/participation/{participationId}/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getAssessmentBySubmissionId(@PathVariable Long participationId, @PathVariable Long submissionId) {
        Optional<Participation> optionalParticipation = participationRepository.findById(participationId);
        if (!optionalParticipation.isPresent()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).body(null);
        }

        Participation participation = optionalParticipation.get();

        if (!courseService.userHasAtLeastStudentPermissions(participation.getExercise().getCourse()) || !authCheckService.isOwnerOfParticipation(participation)) {
            return forbidden();
        }

        Long exerciseId = participation.getExercise().getId();
        Long studentId = participation.getStudent().getId();
        if (jsonAssessmentRepository.exists(exerciseId, studentId, submissionId, true)) {
            JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(exerciseId, studentId, submissionId, true);
            return ResponseEntity.ok(assessmentJson.get("assessments").toString());
        }
        if (jsonAssessmentRepository.exists(exerciseId, studentId, submissionId, false)) {
            JsonObject assessmentJson = jsonAssessmentRepository.readAssessment(exerciseId, studentId, submissionId, false);
            return ResponseEntity.ok(assessmentJson.get("assessments").toString());
        }
        return ResponseEntity.ok("");
    }


    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({
        @ApiResponse(code = 200, message = PUT_SUBMIT_ASSESSMENT_200_REASON, response = Result.class),
        @ApiResponse(code = 404, message = REQ_404_REASON),
        @ApiResponse(code = 403, message = REQ_403_REASON)
    })
    @PutMapping("/modeling-assessments/exercise/{exerciseId}/result/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> saveModelingAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody List<ModelElementAssessment> modelingAssessment) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        Result result = resultService.findOne(resultId);
        modelingAssessmentService.saveManualAssessment(result, exerciseId, modelingAssessment);
        return ResponseEntity.ok(result);
    }


    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({
        @ApiResponse(code = 200, message = PUT_SUBMIT_ASSESSMENT_200_REASON, response = Result.class),
        @ApiResponse(code = 403, message = REQ_403_REASON),
        @ApiResponse(code = 404, message = REQ_404_REASON),
        @ApiResponse(code = 409, message = PUT_ASSESSMENT_409_REASON, response = Conflict.class, responseContainer = "List")
    })
    @PutMapping("/modeling-assessments/exercise/{exerciseId}/result/{resultId}/submit")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    //TODO MJ changing submitted assessment always produces Conflict
    public ResponseEntity<Object> submitModelingAssessment(@PathVariable Long exerciseId,
                                                           @PathVariable Long resultId,
                                                           @RequestParam(value = "ignoreConflict", defaultValue = "false") boolean ignoreConflict,
                                                           @RequestBody List<ModelElementAssessment> modelingAssessment) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        Result result = resultService.findOne(resultId);
        Long submissionId = result.getSubmission().getId();
        List<Conflict> conflicts = compassService.getConflicts(exerciseId, submissionId, modelingAssessment);
        if (!conflicts.isEmpty() && !ignoreConflict) {
            modelingAssessmentService.saveManualAssessment(result, modelingExercise.getId(), modelingAssessment);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflicts);
        } else {
            modelingAssessmentService.submitManualAssessment(result, modelingExercise, modelingAssessment);
            compassService.addAssessment(exerciseId, submissionId, modelingAssessment);
            return ResponseEntity.ok(result);
        }
    }


    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}

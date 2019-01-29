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
import de.tum.in.www1.artemis.service.compass.conflict.*;
import de.tum.in.www1.artemis.web.rest.util.*;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing ModelingAssessment.
 */
@RestController
@RequestMapping("/api")
public class ModelingAssessmentResource extends AssessmentResource {
    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentResource.class);

    private static final String ENTITY_NAME = "modelingAssessment";

    private final JsonAssessmentRepository jsonAssessmentRepository;
    private final ParticipationRepository participationRepository;
    private final CompassService compassService;
    private final ModelingExerciseService modelingExerciseService;
    private final AuthorizationCheckService authCheckService;
    private final CourseService courseService;
    private final ModelingAssessmentService modelingAssessmentService;


    public ModelingAssessmentResource(JsonAssessmentRepository jsonAssessmentRepository, ParticipationRepository participationRepository, CompassService compassService, ModelingExerciseService modelingExerciseService, UserService userService, AuthorizationCheckService authCheckService, CourseService courseService, ModelingAssessmentService modelingAssessmentService) {
        super(authCheckService, userService);

        this.jsonAssessmentRepository = jsonAssessmentRepository;
        this.participationRepository = participationRepository;
        this.compassService = compassService;
        this.modelingExerciseService = modelingExerciseService;
        this.authCheckService = authCheckService;
        this.courseService = courseService;
        this.modelingAssessmentService = modelingAssessmentService;
    }

    //TODO: all API path in this class do not really make sense, we should restructure them and potentially start with /exercise/


    @DeleteMapping("/modeling-assessments/exercise/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> resetOptimalModels(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        if (modelingExercise == null) {
            return ResponseUtil.notFound();
        }

        ResponseEntity responseFailure = checkExercise(modelingExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        compassService.resetModelsWaitingForAssessment(exerciseId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }


    @GetMapping("/modeling-assessments/exercise/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getNextOptimalModelSubmissions(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);

        ResponseEntity responseFailure = checkExercise(modelingExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

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

        ResponseEntity responseFailure = checkExercise(modelingExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

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


    /**
     * Saves assessments and updates result.
     *
     * @param exerciseId         the exerciseId the assessment belongs to
     * @param resultId           the resultId the assessment belongs to
     * @param modelingAssessment the assessments as string
     * @return the ResponseEntity with result as body
     */
    @PutMapping("/modeling-assessments/exercise/{exerciseId}/result/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ConflictResultWrapper> saveModelingAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody String modelingAssessment) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        if (modelingExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("modelingExercise", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }
        ResponseEntity responseFailure = checkExercise(modelingExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        Result result = modelingAssessmentService.saveManualAssessment(resultId, exerciseId, modelingAssessment);
        Optional<Conflict> conflict = compassService.checkForConflict(exerciseId, result.getSubmission().getId());
        return ResponseEntity.ok(new ConflictResultWrapper(conflict.get(), result));
    }

    //TODO change to POST and remove submision in path or merge with saveNodelingAssessment?


    /**
     * Saves assessments and updates result. Sets result to rated so the student can see the assessments.
     *
     * @param exerciseId         the exerciseId the assessment belongs to
     * @param resultId           the resultId the assessment belongs to
     * @param modelingAssessment the assessments as string
     * @return the ResponseEntity with result as body
     */
    @PutMapping("/modeling-assessments/exercise/{exerciseId}/result/{resultId}/submit")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ConflictResultWrapper> submitModelingAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody String modelingAssessment) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);

        ResponseEntity responseFailure = checkExercise(modelingExercise);
        if (responseFailure != null) {
            return responseFailure;
        }

        Result result = modelingAssessmentService.submitManualAssessment(resultId, exerciseId, modelingAssessment);
        Long submissionId = result.getSubmission().getId();
        // add assessment to compass to include it in the automatic grading process
        compassService.addAssessment(exerciseId, submissionId, modelingAssessment);
        Optional<Conflict> conflict = compassService.checkForConflict(exerciseId, submissionId);
        return ResponseEntity.ok(new ConflictResultWrapper(conflict.get(), result));
    }


    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }
}

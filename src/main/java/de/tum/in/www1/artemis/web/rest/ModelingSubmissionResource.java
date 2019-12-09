package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.*;
import io.swagger.annotations.*;

/**
 * REST controller for managing ModelingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ModelingSubmissionResource extends GenericSubmissionResource<ModelingSubmission> {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionResource.class);

    private static final String ENTITY_NAME = "modelingSubmission";

    private static final String GET_200_SUBMISSIONS_REASON = "";

    private final ModelingSubmissionService modelingSubmissionService;

    private final ModelingExerciseService modelingExerciseService;

    private final CompassService compassService;

    public ModelingSubmissionResource(ModelingSubmissionService modelingSubmissionService, ModelingExerciseService modelingExerciseService,
            ParticipationService participationService, CourseService courseService, AuthorizationCheckService authCheckService, CompassService compassService,
            ExerciseService exerciseService, UserService userService) {
        super(courseService, authCheckService, userService, exerciseService, participationService);
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseService = modelingExerciseService;
        this.compassService = compassService;
    }

    /**
     * POST /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Create a new modelingSubmission. This is called when a student saves his model the first time after
     * starting the exercise or starting a retry.
     *
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param principal          the current user principal
     * @param modelingSubmission the modelingSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO MJ return 201 CREATED with location header instead of ModelingSubmission
    // TODO MJ merge with update
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@PathVariable long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to create ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        checkAuthorization(modelingExercise, user);
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, principal.getName());
        modelingSubmission.hideDetails(authCheckService, user);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * PUT /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Updates an existing modelingSubmission. This function is called by the modeling editor for saving and
     * submitting modeling submissions. The submit specific handling occurs in the ModelingSubmissionService.save() function.
     *
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param principal          the current user principal
     * @param modelingSubmission the modelingSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingSubmission, or with status 400 (Bad Request) if the modelingSubmission is not valid, or
     *         with status 500 (Internal Server Error) if the modelingSubmission couldn't be updated
     */
    @PutMapping("/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@PathVariable long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission.getModel());
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        checkAuthorization(modelingExercise, user);

        if (modelingSubmission.getId() == null) {
            return createModelingSubmission(exerciseId, principal, modelingSubmission);
        }
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, principal.getName());
        modelingSubmission.hideDetails(authCheckService, user);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /exercises/{exerciseId}/modeling-submissions: get all modeling submissions by exercise id. If the parameter assessedByTutor is true, this method will
     * only return all the modeling submissions where the tutor has a result associated
     *
     * @param exerciseId id of the exercise for which the modeling submission should be returned
     * @param submittedOnly if true, it returns only submission with submitted flag set to true
     * @param assessedByTutor if true, it returns only the submissions which are assessed by the current user as a tutor
     * @return a list of modeling submissions
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 200, message = GET_200_SUBMISSIONS_REASON, response = ModelingSubmission.class, responseContainer = "List"),
            @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON), })
    @GetMapping(value = "/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO: separate this into 2 calls, one for instructors (with all submissions) and one for tutors (only the submissions for the requesting tutor)
    public ResponseEntity<List<ModelingSubmission>> getAllModelingSubmissions(@PathVariable long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor) {
        log.debug("REST request to get all ModelingSubmissions");
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = modelingExerciseService.findOne(exerciseId);
        if (assessedByTutor) {
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                throw new AccessForbiddenException("You are not allowed to access this resource");
            }
        }
        else if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        final List<ModelingSubmission> modelingSubmissions;
        if (assessedByTutor) {
            modelingSubmissions = modelingSubmissionService.getAllSubmissionsByTutorForExercise(exerciseId, user.getId());
        }
        else {
            modelingSubmissions = modelingSubmissionService.getSubmissions(exerciseId, submittedOnly, ModelingSubmission.class);
        }

        // tutors should not see information about the student of a submission
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            modelingSubmissions.forEach(submission -> submission.hideDetails(authCheckService, user));
        }

        // remove unnecessary data from the REST response
        modelingSubmissions.forEach(submission -> {
            if (submission.getParticipation() != null && submission.getParticipation().getExercise() != null) {
                submission.getParticipation().setExercise(null);
            }
        });

        return ResponseEntity.ok().body(modelingSubmissions);
    }

    /**
     * GET /modeling-submissions/{submissionId} : Gets an existing modelingSubmission with result. If no result exists for this submission a new Result object is created and
     * assigned to the submission.
     *
     * @param submissionId the id of the modelingSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission for the given id, or with status 404 (Not Found) if the modelingSubmission could not be
     *         found
     */
    @GetMapping("/modeling-submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable long submissionId) {
        log.debug("REST request to get ModelingSubmission with id: {}", submissionId);
        // TODO CZ: include exerciseId in path to get exercise for auth check more easily?
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOne(submissionId);
        final StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        final ModelingExercise modelingExercise = (ModelingExercise) studentParticipation.getExercise();
        final User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise, user)) {
            return forbidden();
        }
        modelingSubmission = modelingSubmissionService.getLockedModelingSubmission(submissionId, modelingExercise);
        // Make sure the exercise is connected to the participation in the json response

        studentParticipation.setExercise(modelingExercise);
        modelingSubmission.hideDetails(authCheckService, user);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /modeling-submission-without-assessment : get one modeling submission without assessment.
     *
     * @param exerciseId id of the exercise for which the modeling submission should be returned
     * @param lockSubmission optional value to define if the submission should be locked and has the value of false if not set manually
     * @return the ResponseEntity with status 200 (OK) and a modeling submission without assessment in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/modeling-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> getModelingSubmissionWithoutAssessment(@PathVariable long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission) {
        log.debug("REST request to get a modeling submission without assessment");
        final Exercise exercise = exerciseService.findOne(exerciseId);
        final var exerciseInvalid = this.checkExerciseValidityForTutor(exercise, ModelingExercise.class, modelingSubmissionService);
        if (exerciseInvalid != null) {
            return exerciseInvalid;
        }

        final ModelingSubmission modelingSubmission;
        if (lockSubmission) {
            // TODO rename this, because if Compass is activated we pass a submission with a partial automatic result
            modelingSubmission = modelingSubmissionService.getLockedModelingSubmissionWithoutResult((ModelingExercise) exercise);
        }
        else {
            final Optional<ModelingSubmission> optionalModelingSubmission = modelingSubmissionService.getSubmissionWithoutManualResult(exercise, ModelingSubmission.class);
            if (optionalModelingSubmission.isEmpty()) {
                return notFound();
            }
            modelingSubmission = optionalModelingSubmission.get();
        }

        // Make sure the exercise is connected to the participation in the json response
        final StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        studentParticipation.setExercise(exercise);
        modelingSubmission.hideDetails(authCheckService, userService.getUserWithGroupsAndAuthorities());
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * Given an exerciseId, find a modeling submission for that exercise which still doesn't have a manual result. If the diagram type is supported by Compass we get an array of
     * ids of the next optimal submissions from Compass, i.e. the submissions for which an assessment means the most knowledge gain for the automatic assessment mechanism. If it's
     * not supported by Compass we just get an array with the id of a random submission without manual assessment.
     *
     * @param exerciseId the id of the modeling exercise for which we want to get a submission without manual result
     * @return an array of modeling submission id(s) without a manual result
     */
    @GetMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<Long[]> getNextOptimalModelSubmissions(@PathVariable long exerciseId) {
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        checkAuthorization(modelingExercise, user);
        // Check if the limit of simultaneously locked submissions has been reached
        modelingSubmissionService.checkSubmissionLockLimit(modelingExercise.getCourse().getId());

        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            // ask Compass for optimal submission to assess if diagram type is supported
            final List<Long> optimalModelSubmissions = compassService.getModelsWaitingForAssessment(exerciseId);

            if (optimalModelSubmissions.isEmpty()) {
                return ResponseEntity.ok(new Long[] {}); // empty
            }

            // shuffle the model list to prevent that the user gets the same submission again after canceling an assessment
            Collections.shuffle(optimalModelSubmissions);
            return ResponseEntity.ok(optimalModelSubmissions.toArray(new Long[] {}));
        }
        else {
            // otherwise get a random (non-optimal) submission that is not assessed
            final List<ModelingSubmission> submissionsWithoutResult = participationService.findByExerciseIdWithLatestSubmissionWithoutManualResults(modelingExercise.getId())
                    .stream().map(StudentParticipation::findLatestModelingSubmission).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

            if (submissionsWithoutResult.isEmpty()) {
                return ResponseEntity.ok(new Long[] {}); // empty
            }

            Random r = new Random();
            return ResponseEntity.ok(new Long[] { submissionsWithoutResult.get(r.nextInt(submissionsWithoutResult.size())).getId() });
        }
    }

    /**
     * DELETE /exercises/{exerciseId}/optimal-model-submissions: Reset models waiting for assessment by Compass by emptying the waiting list
     *
     * @param exerciseId id of the exercise
     * @return the response entity with status 200 (OK) if reset was performed successfully, otherwise appropriate error code
     */
    @DeleteMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> resetOptimalModels(@PathVariable long exerciseId) {
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        checkAuthorization(modelingExercise, user);
        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            compassService.resetModelsWaitingForAssessment(exerciseId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the submission with data needed for the modeling editor, which includes the participation, the model and the result (if the assessment was already submitted).
     *
     * @param participationId the participationId for which to find the submission and data for the modeling editor
     * @return the ResponseEntity with the submission as body
     */
    @GetMapping("/modeling-editor/{participationId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> getSubmissionForModelingEditor(@PathVariable long participationId) {
        return getDataForEditor(participationId, ModelingExercise.class, ModelingSubmission.class, new ModelingSubmission());
    }

    private void checkAuthorization(ModelingExercise exercise, User user) throws AccessForbiddenException {
        final Course course = courseService.findOne(exercise.getCourse().getId());
        if (!authCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourse().getTitle());
        }
    }
}

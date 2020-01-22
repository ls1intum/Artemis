package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
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
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.swagger.annotations.*;

/**
 * REST controller for managing ModelingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ModelingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionResource.class);

    private static final String ENTITY_NAME = "modelingSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String GET_200_SUBMISSIONS_REASON = "";

    private final ModelingSubmissionService modelingSubmissionService;

    private final ModelingExerciseService modelingExerciseService;

    private final ParticipationService participationService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final CompassService compassService;

    private final ExerciseService exerciseService;

    private final UserService userService;

    public ModelingSubmissionResource(ModelingSubmissionService modelingSubmissionService, ModelingExerciseService modelingExerciseService,
            ParticipationService participationService, CourseService courseService, AuthorizationCheckService authCheckService, CompassService compassService,
            ExerciseService exerciseService, UserService userService) {
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseService = modelingExerciseService;
        this.participationService = participationService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.compassService = compassService;
        this.exerciseService = exerciseService;
        this.userService = userService;
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
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to create ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        checkAuthorization(modelingExercise, user);
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, principal.getName());
        this.modelingSubmissionService.hideDetails(modelingSubmission, user);
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
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission.getModel());
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        checkAuthorization(modelingExercise, user);

        if (modelingSubmission.getId() == null) {
            return createModelingSubmission(exerciseId, principal, modelingSubmission);
        }
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, principal.getName());
        this.modelingSubmissionService.hideDetails(modelingSubmission, user);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /exercises/{exerciseId}/modeling-submissions: get all modeling submissions by exercise id. If the parameter assessedByTutor is true, this method will return
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
    public ResponseEntity<List<ModelingSubmission>> getAllModelingSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
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
            modelingSubmissions = modelingSubmissionService.getAllModelingSubmissionsByTutorForExercise(exerciseId, user.getId());
        }
        else {
            modelingSubmissions = modelingSubmissionService.getModelingSubmissions(exerciseId, submittedOnly);
        }

        // tutors should not see information about the student of a submission
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            modelingSubmissions.forEach(submission -> modelingSubmissionService.hideDetails(submission, user));
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
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long submissionId) {
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
        this.modelingSubmissionService.hideDetails(modelingSubmission, user);
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
    public ResponseEntity<ModelingSubmission> getModelingSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission) {
        log.debug("REST request to get a modeling submission without assessment");
        final Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        if (!(exercise instanceof ModelingExercise)) {
            return badRequest();
        }

        // Tutors cannot start assessing submissions if the exercise due date hasn't been reached yet
        if (exercise.getDueDate() != null && exercise.getDueDate().isAfter(ZonedDateTime.now())) {
            return notFound();
        }

        // Check if the limit of simultaneously locked submissions has been reached
        modelingSubmissionService.checkSubmissionLockLimit(exercise.getCourse().getId());

        final ModelingSubmission modelingSubmission;
        if (lockSubmission) {
            modelingSubmission = modelingSubmissionService.lockModelingSubmissionWithoutResult((ModelingExercise) exercise);
        }
        else {
            final Optional<ModelingSubmission> optionalModelingSubmission = modelingSubmissionService.getModelingSubmissionWithoutManualResult((ModelingExercise) exercise);
            if (optionalModelingSubmission.isEmpty()) {
                return notFound();
            }
            modelingSubmission = optionalModelingSubmission.get();
        }

        // Make sure the exercise is connected to the participation in the json response
        final StudentParticipation studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        studentParticipation.setExercise(exercise);
        this.modelingSubmissionService.hideDetails(modelingSubmission, user);
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
    public ResponseEntity<Long[]> getNextOptimalModelSubmissions(@PathVariable Long exerciseId) {
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
            var participations = participationService.findByExerciseIdWithLatestSubmissionWithoutManualResults(modelingExercise.getId());
            var submissionsWithoutResult = participations.stream().map(StudentParticipation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get)
                    .collect(Collectors.toList());

            if (submissionsWithoutResult.isEmpty()) {
                return ResponseEntity.ok(new Long[] {}); // empty
            }

            Random random = new Random();
            return ResponseEntity.ok(new Long[] { submissionsWithoutResult.get(random.nextInt(submissionsWithoutResult.size())).getId() });
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
    public ResponseEntity<String> resetOptimalModels(@PathVariable Long exerciseId) {
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
    public ResponseEntity<ModelingSubmission> getSubmissionForModelingEditor(@PathVariable Long participationId) {
        StudentParticipation participation = participationService.findOneWithEagerSubmissionsAndResults(participationId);
        ModelingExercise modelingExercise;

        if (participation.getExercise() == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "modelingExercise", "exerciseEmpty", "The exercise belonging to the participation is null."))
                    .body(null);
        }

        if (participation.getExercise() instanceof ModelingExercise) {
            modelingExercise = (ModelingExercise) participation.getExercise();

            // make sure sensitive information are not sent to the client
            modelingExercise.filterSensitiveInformation();
        }
        else {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "modelingExercise", "wrongExerciseType", "The exercise of the participation is not a modeling exercise."))
                    .body(null);
        }

        // Students can only see their own models (to prevent cheating). TAs, instructors and admins can see all models.
        if (!(authCheckService.isOwnerOfParticipation(participation) || authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise))) {
            return forbidden();
        }

        Optional<Submission> optionalSubmission = participation.findLatestSubmission();
        ModelingSubmission modelingSubmission;
        if (optionalSubmission.isEmpty()) {
            // this should never happen as the submission is initialized along with the participation when the exercise is started
            modelingSubmission = new ModelingSubmission();
            modelingSubmission.setParticipation(participation);
        }
        else {
            // only try to get and set the model if the modelingSubmission existed before
            modelingSubmission = (ModelingSubmission) optionalSubmission.get();
        }

        // make sure only the latest submission and latest result is sent to the client
        participation.setSubmissions(null);
        participation.setResults(null);

        // do not send the result to the client if the assessment is not finished
        if (modelingSubmission.getResult() != null && (modelingSubmission.getResult().getCompletionDate() == null || modelingSubmission.getResult().getAssessor() == null)) {
            modelingSubmission.setResult(null);
        }

        if (modelingSubmission.getResult() != null && !authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            modelingSubmission.getResult().setAssessor(null);
        }

        return ResponseEntity.ok(modelingSubmission);
    }

    private void checkAuthorization(ModelingExercise exercise, User user) throws AccessForbiddenException {
        final Course course = courseService.findOne(exercise.getCourse().getId());
        if (!authCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourse().getTitle());
        }
    }
}

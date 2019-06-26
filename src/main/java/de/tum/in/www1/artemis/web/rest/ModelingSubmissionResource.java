package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.*;
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

    private final ResultService resultService;

    private final AuthorizationCheckService authCheckService;

    private final CompassService compassService;

    private final ExerciseService exerciseService;

    private final UserService userService;

    public ModelingSubmissionResource(ModelingSubmissionService modelingSubmissionService, ModelingExerciseService modelingExerciseService,
            ParticipationService participationService, CourseService courseService, ResultService resultService, AuthorizationCheckService authCheckService,
            CompassService compassService, ExerciseService exerciseService, UserService userService) {
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseService = modelingExerciseService;
        this.participationService = participationService;
        this.courseService = courseService;
        this.resultService = resultService;
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
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, principal.getName());
        hideDetails(modelingSubmission);
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
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@PathVariable Long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() == null) {
            return createModelingSubmission(exerciseId, principal, modelingSubmission);
        }
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, principal.getName());
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }

    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 200, message = GET_200_SUBMISSIONS_REASON, response = ModelingSubmission.class, responseContainer = "List"),
            @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON), })
    @GetMapping(value = "/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ModelingSubmission>> getAllModelingSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor) {
        log.debug("REST request to get all ModelingSubmissions");
        Exercise exercise = modelingExerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        if (assessedByTutor) {
            User user = userService.getUserWithGroupsAndAuthorities();
            List<ModelingSubmission> submissions = modelingSubmissionService.getAllModelingSubmissionsByTutorForExercise(exerciseId, user.getId());
            return ResponseEntity.ok().body(clearStudentInformation(submissions, exercise));
        }

        List<ModelingSubmission> submissions = modelingSubmissionService.getModelingSubmissions(exerciseId, submittedOnly);
        return ResponseEntity.ok(clearStudentInformation(submissions, exercise));
    }

    /**
     * Remove information about the student from the submissions for tutors to ensure a double-blind assessment
     */
    private List<ModelingSubmission> clearStudentInformation(List<ModelingSubmission> submissions, Exercise exercise) {
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            submissions.forEach(submission -> submission.getParticipation().setStudent(null));
        }
        return submissions;
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
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get ModelingSubmission with id: {}", submissionId);
        // TODO CZ: include exerciseId in path to get exercise for auth check more easily?
        ModelingExercise modelingExercise = (ModelingExercise) modelingSubmissionService.findOne(submissionId).getParticipation().getExercise();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            return forbidden();
        }
        ModelingSubmission modelingSubmission = modelingSubmissionService.getLockedModelingSubmission(submissionId, modelingExercise);
        // Make sure the exercise is connected to the participation in the json response
        modelingSubmission.getParticipation().setExercise(modelingExercise);
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /modeling-submission-without-assessment : get one modeling submission without assessment.
     *
     * @return the ResponseEntity with status 200 (OK) and a modeling submission without assessment in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/modeling-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> getModelingSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission) {
        log.debug("REST request to get a modeling submission without assessment");
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
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

        ModelingSubmission modelingSubmission;
        if (lockSubmission) {
            modelingSubmission = modelingSubmissionService.getLockedModelingSubmissionWithoutResult((ModelingExercise) exercise);
        }
        else {
            Optional<ModelingSubmission> optionalModelingSubmission = modelingSubmissionService.getModelingSubmissionWithoutResult((ModelingExercise) exercise);
            if (!optionalModelingSubmission.isPresent()) {
                return notFound();
            }
            modelingSubmission = optionalModelingSubmission.get();
        }

        // Make sure the exercise is connected to the participation in the json response
        modelingSubmission.getParticipation().setExercise(exercise);
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * Given an exerciseId, find a modeling submission for that exercise which still doesn't have any result. If the diagram type is supported by Compass we get an array of ids of
     * the next optimal submissions from Compass, i.e. the submissions for which an assessment means the most knowledge gain for the automatic assessment mechanism. If it's not
     * supported by Compass we just get an array with the id of a random submission without assessment.
     *
     * @param exerciseId the id of the modeling exercise for which we want to get a submission without result
     * @return an array of modeling submission id(s) without any result
     */
    @GetMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<Long[]> getNextOptimalModelSubmissions(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        // Check if the limit of simultaneously locked submissions has been reached
        modelingSubmissionService.checkSubmissionLockLimit(modelingExercise.getCourse().getId());

        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            // ask Compass for optimal submission to assess if diagram type is supported
            Set<Long> optimalModelSubmissions = compassService.getModelsWaitingForAssessment(exerciseId);
            if (optimalModelSubmissions.isEmpty()) {
                return ResponseEntity.ok(new Long[] {}); // empty
            }
            // TODO CZ: think about how to handle canceled assessments with Compass as I do not want to receive the same submission again, if I canceled the assessment
            return ResponseEntity.ok(optimalModelSubmissions.toArray(new Long[] {}));
        }
        else {
            // otherwise get a random (non-optimal) submission that is not assessed
            Random r = new Random();
            List<ModelingSubmission> submissionsWithoutResult = participationService.findByExerciseIdWithEagerSubmittedSubmissionsWithoutResults(modelingExercise.getId()).stream()
                    .map(Participation::findLatestModelingSubmission).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

            if (submissionsWithoutResult.isEmpty()) {
                return ResponseEntity.ok(new Long[] {}); // empty
            }
            return ResponseEntity.ok(new Long[] { submissionsWithoutResult.get(r.nextInt(submissionsWithoutResult.size())).getId() });
        }
    }

    @DeleteMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> resetOptimalModels(@PathVariable Long exerciseId) {
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        checkAuthorization(modelingExercise);
        if (compassService.isSupported(modelingExercise.getDiagramType())) {
            compassService.resetModelsWaitingForAssessment(exerciseId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Removes sensitive information (e.g. example solution) from the exercise. This should be called before sending an exercise to the client for a student. IMPORTANT: Do not call
     * this method from a transactional context as this would remove the sensitive information also from the entity in the database without explicitly saving it.
     */
    private void hideDetails(ModelingSubmission modelingSubmission) {
        // do not send old submissions or old results to the client
        if (modelingSubmission.getParticipation() != null) {
            modelingSubmission.getParticipation().setSubmissions(null);
            modelingSubmission.getParticipation().setResults(null);

            Exercise exercise = modelingSubmission.getParticipation().getExercise();
            if (exercise != null && !authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                // make sure that sensitive information is not sent to the client for students
                exercise.filterSensitiveInformation();
            }
            // remove information about the student from the submission for tutors to ensure a double-blind assessment
            if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
                modelingSubmission.getParticipation().setStudent(null);
            }
        }
    }

    private void checkAuthorization(ModelingExercise exercise) throws AccessForbiddenException {
        Course course = courseService.findOne(exercise.getCourse().getId());
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourse().getTitle());
        }
    }
}

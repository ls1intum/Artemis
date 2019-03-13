package de.tum.in.www1.artemis.web.rest;

import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.*;
import io.swagger.annotations.*;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing ModelingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ModelingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionResource.class);

    private static final String ENTITY_NAME = "modelingSubmission";
    private static final String GET_200_SUBMISSIONS_REASON = "";


    private final ModelingSubmissionService modelingSubmissionService;
    private final ModelingExerciseService modelingExerciseService;
    private final ParticipationService participationService;
    private final CourseService courseService;
    private final ResultService resultService;
    private final AuthorizationCheckService authCheckService;
    private final CompassService compassService;


    public ModelingSubmissionResource(ModelingSubmissionService modelingSubmissionService, ModelingExerciseService modelingExerciseService, ParticipationService participationService, CourseService courseService, ResultService resultService, AuthorizationCheckService authCheckService, CompassService compassService) {
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseService = modelingExerciseService;
        this.participationService = participationService;
        this.courseService = courseService;
        this.resultService = resultService;
        this.authCheckService = authCheckService;
        this.compassService = compassService;
    }


    /**
     * POST  /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Create a new modelingSubmission.
     * This is called when a student saves his model the first time after starting the exercise or starting a retry.
     *
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param principal          the current user principal
     * @param modelingSubmission the modelingSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    //TODO MJ return 201 CREATED with location header instead of ModelingSubmission
    //TODO MJ merge with update
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@PathVariable Long exerciseId,
                                                                       Principal principal,
                                                                       @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to create ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(exerciseId, principal.getName());
        checkAuthorization(modelingExercise);
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, participation);
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }


    /**
     * PUT  /courses/{courseId}/exercises/{exerciseId}/modeling-submissions : Updates an existing modelingSubmission.
     * This function is called by the modeling editor for saving and submitting modeling submissions.
     * The submit specific handling occurs in the ModelingSubmissionService.save() function.
     *
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param principal          the current user principal
     * @param modelingSubmission the modelingSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingSubmission,
     * or with status 400 (Bad Request) if the modelingSubmission is not valid,
     * or with status 500 (Internal Server Error) if the modelingSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@PathVariable Long exerciseId,
                                                                       Principal principal,
                                                                       @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() == null) {
            return createModelingSubmission(exerciseId, principal, modelingSubmission);
        }
        ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        Participation participation = participationService.findOneByExerciseIdAndStudentLoginAnyState(exerciseId, principal.getName());
        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, participation);
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }


    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({
        @ApiResponse(code = 200, message = GET_200_SUBMISSIONS_REASON, response = ModelingSubmission.class, responseContainer = "List"),
        @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON),
        @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON),
    })
    @GetMapping(value = "/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ModelingSubmission>> getAllModelingSubmissions(@PathVariable Long exerciseId,
                                                                              @RequestParam(defaultValue = "false") boolean submittedOnly) {
        log.debug("REST request to get all ModelingSubmissions");
        Exercise exercise = modelingExerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        List<ModelingSubmission> submissions = modelingSubmissionService.getModelingSubmissions(exerciseId, submittedOnly);
        return ResponseEntity.ok(submissions);
    }


    /**
     * GET  /modeling-submissions/{submissionId} : Gets an existing modelingSubmission with result. If no result
     * exists for this submission a new Result object is created and assigned to the submission.
     *
     * @param submissionId the id of the modelingSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission for the given id,
     * or with status 404 (Not Found) if the modelingSubmission could not be found
     */
    @GetMapping("/modeling-submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get ModelingSubmission with id: {}", submissionId);
        ModelingSubmission modelingSubmission = modelingSubmissionService.findOneWithEagerResultAndFeedback(submissionId);
        Exercise exercise = modelingSubmission.getParticipation().getExercise();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        if (modelingSubmission.getResult() == null) {
            modelingSubmissionService.setResult(modelingSubmission);
        }
        if (modelingSubmission.getResult().getAssessor() == null) {
            compassService.removeModelWaitingForAssessment(exercise.getId(), submissionId);
            //we set the assessor and save the result to soft lock the assessment (so that it cannot be edited by another tutor)
            resultService.setAssessor(modelingSubmission.getResult());
        }
        //Make sure the exercise is connected to the participation in the json response
        modelingSubmission.getParticipation().setExercise(exercise);
        hideDetails(modelingSubmission);
        return ResponseEntity.ok(modelingSubmission);
    }


    private void hideDetails(ModelingSubmission modelingSubmission) {
        //do not send old submissions or old results to the client
        if (modelingSubmission.getParticipation() != null) {
            modelingSubmission.getParticipation().setSubmissions(null);
            modelingSubmission.getParticipation().setResults(null);

            if (modelingSubmission.getParticipation().getExercise() != null && modelingSubmission.getParticipation().getExercise() instanceof ModelingExercise) {
                //make sure the solution is not sent to the client
                ModelingExercise modelingExerciseForClient = (ModelingExercise) modelingSubmission.getParticipation().getExercise();
                modelingExerciseForClient.setSampleSolutionExplanation(null);
                modelingExerciseForClient.setSampleSolutionModel(null);
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

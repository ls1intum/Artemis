package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GradingCriterion;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST controller for managing ModelingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ModelingSubmissionResource extends AbstractSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionResource.class);

    private static final String ENTITY_NAME = "modelingSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String GET_200_SUBMISSIONS_REASON = "";

    private final ModelingSubmissionService modelingSubmissionService;

    private final ModelingExerciseService modelingExerciseService;

    private final CompassService compassService;

    private final GradingCriterionService gradingCriterionService;

    private final ExamSubmissionService examSubmissionService;

    public ModelingSubmissionResource(SubmissionRepository submissionRepository, ResultService resultService, ModelingSubmissionService modelingSubmissionService,
            ModelingExerciseService modelingExerciseService, ParticipationService participationService, AuthorizationCheckService authCheckService, CompassService compassService,
            ExerciseService exerciseService, UserService userService, GradingCriterionService gradingCriterionService, ExamSubmissionService examSubmissionService) {
        super(submissionRepository, resultService, participationService, authCheckService, userService, exerciseService, modelingSubmissionService);
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseService = modelingExerciseService;
        this.compassService = compassService;
        this.gradingCriterionService = gradingCriterionService;
        this.examSubmissionService = examSubmissionService;
    }

    /**
     * POST /exercises/{exerciseId}/modeling-submissions : Create a new modelingSubmission. This is called when a student saves his model the first time after
     * starting the exercise or starting a retry.
     *
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param principal          the current user principal
     * @param modelingSubmission the modelingSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/modeling-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@PathVariable long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to create ModelingSubmission : {}", modelingSubmission.getModel());
        long start = System.currentTimeMillis();
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ResponseEntity<ModelingSubmission> response = handleModelingSubmission(exerciseId, principal, modelingSubmission);
        long end = System.currentTimeMillis();
        log.info("createModelingSubmission took " + (end - start) + "ms for exercise " + exerciseId + " and user " + principal.getName());
        return response;
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
        long start = System.currentTimeMillis();
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission.getModel());
        ResponseEntity<ModelingSubmission> response = handleModelingSubmission(exerciseId, principal, modelingSubmission);
        long end = System.currentTimeMillis();
        log.info("updateModelingSubmission took " + (end - start) + "ms for exercise " + exerciseId + " and user " + principal.getName());
        return response;
    }

    @NotNull
    private ResponseEntity<ModelingSubmission> handleModelingSubmission(Long exerciseId, Principal principal, ModelingSubmission modelingSubmission) {
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();

        // Apply further checks if it is an exam submission
        Optional<ResponseEntity<ModelingSubmission>> examSubmissionAllowanceFailure = examSubmissionService.checkSubmissionAllowance(modelingExercise, user);
        if (examSubmissionAllowanceFailure.isPresent()) {
            return examSubmissionAllowanceFailure.get();
        }

        // Prevent multiple submissions (currently only for exam submissions)
        modelingSubmission = (ModelingSubmission) examSubmissionService.preventMultipleSubmissions(modelingExercise, modelingSubmission, user);

        // Check if the user is allowed to submit
        Optional<ResponseEntity<ModelingSubmission>> submissionAllowanceFailure = modelingSubmissionService.checkSubmissionAllowance(modelingExercise, modelingSubmission, user);
        if (submissionAllowanceFailure.isPresent()) {
            return submissionAllowanceFailure.get();
        }

        modelingSubmission = modelingSubmissionService.save(modelingSubmission, modelingExercise, principal.getName());
        modelingSubmissionService.hideDetails(modelingSubmission, user);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /exercises/{exerciseId}/modeling-submissions: get all modeling submissions by exercise id and correction round.
     * If the parameter assessedByTutor is true, this method will return
     * only return all the modeling submissions where the tutor has a result associated.
     * In case of exam exercise, it filters out all test run submissions.
     *
     * @param exerciseId id of the exercise for which the modeling submission should be returned
     * @param correctionRound - correctionRound for which the submissions' results should be fetched
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
    public ResponseEntity<List<Submission>> getAllModelingSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {

        log.debug("REST request to get all modeling upload submissions");
        return super.getAllSubmissions(exerciseId, submittedOnly, assessedByTutor, correctionRound);
    }

    /**
     * GET /modeling-submissions/{submissionId} : Gets an existing modelingSubmission with result. If no result exists for this submission a new Result object is created and
     * assigned to the submission.
     *
     * @param submissionId the id of the modelingSubmission to retrieve
     * @param correctionRound correction round for which we prepare the submission
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission for the given id, or with status 404 (Not Found) if the modelingSubmission could not be
     *         found
     */
    @GetMapping("/modeling-submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get ModelingSubmission with id: {}", submissionId);
        // TODO CZ: include exerciseId in path to get exercise for auth check more easily?
        var modelingSubmission = modelingSubmissionService.findOne(submissionId);
        var studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        var modelingExercise = (ModelingExercise) studentParticipation.getExercise();
        var gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(modelingExercise.getId());
        modelingExercise.setGradingCriteria(gradingCriteria);
        final User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise, user)) {
            return forbidden();
        }
        modelingSubmission = modelingSubmissionService.lockAndGetModelingSubmission(submissionId, modelingExercise, correctionRound);
        // Make sure the exercise is connected to the participation in the json response
        studentParticipation.setExercise(modelingExercise);
        modelingSubmission.getParticipation().getExercise().setGradingCriteria(gradingCriteria);
        this.modelingSubmissionService.hideDetails(modelingSubmission, user);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /modeling-submission-without-assessment : get one modeling submission without assessment, for course exercises with first correction round and automatic
     * assessment enabled, this will consult compass for an optimal modeling submission
     *
     * @param exerciseId id of the exercise for which the modeling submission should be returned
     * @param lockSubmission optional value to define if the submission should be locked and has the value of false if not set manually
     * @param correctionRound correctionRound for which submissions without a result should be returned
     * @return the ResponseEntity with status 200 (OK) and a modeling submission without assessment in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/modeling-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> getModelingSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {

        log.debug("REST request to get a modeling submission without assessment");
        final var exercise = exerciseService.findOne(exerciseId);
        final var user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        if (!(exercise instanceof ModelingExercise)) {
            return badRequest();
        }

        final var modelingExercise = (ModelingExercise) exercise;
        final var isExamMode = modelingExercise.isExamExercise();

        // Check if tutors can start assessing the students submission
        this.modelingSubmissionService.checkIfExerciseDueDateIsReached(exercise);

        // Check if the limit of simultaneously locked submissions has been reached
        modelingSubmissionService.checkSubmissionLockLimit(exercise.getCourseViaExerciseGroupOrCourseMember().getId());

        var modelingSubmission = modelingSubmissionService.findRandomSubmissionWithoutExistingAssessment(lockSubmission, correctionRound, modelingExercise, isExamMode);

        // needed to show the grading criteria in the assessment view
        List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        modelingExercise.setGradingCriteria(gradingCriteria);
        // Make sure the exercise is connected to the participation in the json response
        modelingSubmission.getParticipation().setExercise(modelingExercise);
        this.modelingSubmissionService.hideDetails(modelingSubmission, user);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * Given an exerciseId, find a modeling submission for that exercise which still doesn't have a manual result. If the diagram type is supported by Compass we get an array of
     * ids of the next optimal submissions from Compass, i.e. the submissions for which an assessment means the most knowledge gain for the automatic assessment mechanism. If it's
     * not supported by Compass we just get an array with the id of a random submission without manual assessment.
     *
     * @param exerciseId the id of the modeling exercise for which we want to get a submission without manual result
     * @param correctionRound correction round for which we prepare the submission
     * @return an array of modeling submission id(s) without a manual result
     */
    @Deprecated(since = "4.9.0", forRemoval = true)
    @GetMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Long[]> getNextOptimalModelSubmissions(@PathVariable Long exerciseId, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        checkAuthorization(modelingExercise, user);
        // Check if the limit of simultaneously locked submissions has been reached
        modelingSubmissionService.checkSubmissionLockLimit(modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId());

        if (compassService.isSupported(modelingExercise)) {
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
    @Deprecated(since = "4.9.0", forRemoval = true)
    @DeleteMapping("/exercises/{exerciseId}/optimal-model-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> resetOptimalModels(@PathVariable Long exerciseId) {
        final ModelingExercise modelingExercise = modelingExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        checkAuthorization(modelingExercise, user);
        if (compassService.isSupported(modelingExercise)) {
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
    @GetMapping("/participations/{participationId}/latest-modeling-submission")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ModelingSubmission> getLatestSubmissionForModelingEditor(@PathVariable long participationId) {
        StudentParticipation participation = participationService.findOneWithEagerSubmissionsResultsFeedback(participationId);
        User user = userService.getUserWithGroupsAndAuthorities();
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

        // Exam exercises cannot be seen by students between the endDate and the publishResultDate
        if (!authCheckService.isAllowedToGetExamResult(modelingExercise, user)) {
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
        if (modelingSubmission.getLatestResult() != null
                && (modelingSubmission.getLatestResult().getCompletionDate() == null || modelingSubmission.getLatestResult().getAssessor() == null)) {
            modelingSubmission.setResults(new ArrayList<Result>());
        }

        if (modelingSubmission.getLatestResult() != null && !authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            modelingSubmission.getLatestResult().setAssessor(null);
        }

        return ResponseEntity.ok(modelingSubmission);
    }

    private void checkAuthorization(ModelingExercise exercise, User user) throws AccessForbiddenException {
        if (!authCheckService.isAtLeastStudentForExercise(exercise, user)) {
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
        }
    }
}

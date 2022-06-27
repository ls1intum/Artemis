package de.tum.in.www1.artemis.web.rest;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
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

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ExamSubmissionService examSubmissionService;

    private final PlagiarismService plagiarismService;

    public ModelingSubmissionResource(SubmissionRepository submissionRepository, ResultService resultService, ModelingSubmissionService modelingSubmissionService,
            ModelingExerciseRepository modelingExerciseRepository, AuthorizationCheckService authCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository,
            GradingCriterionRepository gradingCriterionRepository, ExamSubmissionService examSubmissionService, StudentParticipationRepository studentParticipationRepository,
            ModelingSubmissionRepository modelingSubmissionRepository, PlagiarismService plagiarismService) {
        super(submissionRepository, resultService, authCheckService, userRepository, exerciseRepository, modelingSubmissionService, studentParticipationRepository);
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.examSubmissionService = examSubmissionService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.plagiarismService = plagiarismService;
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
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@PathVariable long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to create ModelingSubmission : {}", modelingSubmission.getModel());
        long start = System.currentTimeMillis();
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ResponseEntity<ModelingSubmission> response = handleModelingSubmission(exerciseId, principal, modelingSubmission);
        long end = System.currentTimeMillis();
        log.info("createModelingSubmission took {}ms for exercise {} and user {}", end - start, exerciseId, principal.getName());
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
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@PathVariable long exerciseId, Principal principal, @RequestBody ModelingSubmission modelingSubmission) {
        long start = System.currentTimeMillis();
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission.getModel());
        ResponseEntity<ModelingSubmission> response = handleModelingSubmission(exerciseId, principal, modelingSubmission);
        long end = System.currentTimeMillis();
        log.debug("updateModelingSubmission took {}ms for exercise {} and user {}", end - start, exerciseId, principal.getName());
        return response;
    }

    @NotNull
    private ResponseEntity<ModelingSubmission> handleModelingSubmission(Long exerciseId, Principal principal, ModelingSubmission modelingSubmission) {
        final ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // Apply further checks if it is an exam submission
        examSubmissionService.checkSubmissionAllowanceElseThrow(modelingExercise, user);

        // Prevent multiple submissions (currently only for exam submissions)
        modelingSubmission = (ModelingSubmission) examSubmissionService.preventMultipleSubmissions(modelingExercise, modelingSubmission, user);

        // Check if the user is allowed to submit
        modelingSubmissionService.checkSubmissionAllowanceElseThrow(modelingExercise, modelingSubmission, user);

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
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Submission>> getAllModelingSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get all modeling upload submissions");
        return super.getAllSubmissions(exerciseId, submittedOnly, assessedByTutor, correctionRound);
    }

    /**
     * GET /modeling-submissions/{submissionId} : Gets an existing modelingSubmission with result. If no result exists for this submission a new Result object is created and
     * assigned to the submission.
     * In case an instructors calls, the resultId is used first. In case the resultId is not set, the correctionRound is used.
     * In case neither resultId nor correctionRound is set, the first correctionRound is used.
     *
     * @param submissionId the id of the modelingSubmission to retrieve
     * @param correctionRound correction round for which we prepare the submission
     * @param resultId the resultId for which we want to get the submission
     * @param withoutResults No result will be created or loaded and the exercise won't be locked when this is set so plagiarism detection doesn't lock results
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission for the given id, or with status 404 (Not Found) if the modelingSubmission could not be
     *         found
     */
    @GetMapping("/modeling-submissions/{submissionId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound, @RequestParam(value = "resultId", required = false) Long resultId,
            @RequestParam(value = "withoutResults", defaultValue = "false") boolean withoutResults) {
        log.debug("REST request to get ModelingSubmission with id: {}", submissionId);
        // TODO CZ: include exerciseId in path to get exercise for auth check more easily?
        var modelingSubmission = modelingSubmissionRepository.findByIdElseThrow(submissionId);
        var studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        var modelingExercise = (ModelingExercise) studentParticipation.getExercise();
        var gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(modelingExercise.getId());
        modelingExercise.setGradingCriteria(gradingCriteria);

        final User user = userRepository.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAllowedToAssessExercise(modelingExercise, user, resultId)) {
            // anonymize and throw exception if not authorized to view submission
            plagiarismService.anonymizeSubmissionForStudent(modelingSubmission, userRepository.getUser().getLogin());
            return ResponseEntity.ok(modelingSubmission);
        }

        if (!withoutResults) {
            // load submission with results either by resultId or by correctionRound
            if (resultId != null) {
                // load the submission with additional needed properties
                modelingSubmission = (ModelingSubmission) submissionRepository.findOneWithEagerResultAndFeedback(submissionId);
                // check if result exists
                Result result = modelingSubmission.getManualResultsById(resultId);
                if (result == null) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "ModelingSubmission", "ResultNotFound", "No Result was found for the given ID."))
                            .body(null);
                }
            }
            else {
                // load and potentially lock the submission with additional needed properties by correctionRound
                modelingSubmission = modelingSubmissionService.lockAndGetModelingSubmission(submissionId, modelingExercise, correctionRound);
            }
        }

        // Make sure the exercise is connected to the participation in the json response
        studentParticipation.setExercise(modelingExercise);
        modelingSubmission.getParticipation().getExercise().setGradingCriteria(gradingCriteria);
        modelingSubmissionService.setNumberOfAffectedSubmissionsPerElement(modelingSubmission);
        // prepare modelingSubmission for response
        modelingSubmissionService.hideDetails(modelingSubmission, user);
        // Don't remove results when they were not requested in the first place
        if (!withoutResults) {
            modelingSubmission.removeNotNeededResults(correctionRound, resultId);
        }

        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /modeling-submission-without-assessment : get one modeling submission without assessment, for course exercises with first correction round and automatic
     * assessment enabled
     *
     * @param exerciseId id of the exercise for which the modeling submission should be returned
     * @param lockSubmission optional value to define if the submission should be locked and has the value of false if not set manually
     * @param correctionRound correctionRound for which submissions without a result should be returned
     * @return the ResponseEntity with status 200 (OK) and a modeling submission without assessment in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/modeling-submission-without-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ModelingSubmission> getModelingSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {

        log.debug("REST request to get a modeling submission without assessment");
        final var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);

        if (!(exercise instanceof final ModelingExercise modelingExercise)) {
            throw new BadRequestAlertException("The exerciseId does not belong to a modeling exercise", ENTITY_NAME, "wrongExerciseType");
        }

        final var isExamMode = modelingExercise.isExamExercise();

        // Check if tutors can start assessing the students submission
        this.modelingSubmissionService.checkIfExerciseDueDateIsReached(exercise);

        // Check if the limit of simultaneously locked submissions has been reached
        modelingSubmissionService.checkSubmissionLockLimit(exercise.getCourseViaExerciseGroupOrCourseMember().getId());

        var modelingSubmission = modelingSubmissionService.findRandomSubmissionWithoutExistingAssessment(lockSubmission, correctionRound, modelingExercise, isExamMode);

        // needed to show the grading criteria in the assessment view
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        modelingExercise.setGradingCriteria(gradingCriteria);
        // Make sure the exercise is connected to the participation in the json response
        modelingSubmission.getParticipation().setExercise(modelingExercise);
        this.modelingSubmissionService.hideDetails(modelingSubmission, user);
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * Returns the submission with data needed for the modeling editor, which includes the participation, the model and the result (if the assessment was already submitted).
     *
     * @param participationId the participationId for which to find the submission and data for the modeling editor
     * @return the ResponseEntity with the submission as body
     */
    @GetMapping("/participations/{participationId}/latest-modeling-submission")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ModelingSubmission> getLatestSubmissionForModelingEditor(@PathVariable long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdWithLegalSubmissionsResultsFeedbackElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
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
            throw new AccessForbiddenException();
        }

        // Exam exercises cannot be seen by students between the endDate and the publishResultDate
        if (!authCheckService.isAllowedToGetExamResult(modelingExercise, user)) {
            throw new AccessForbiddenException();
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
            modelingSubmission.setResults(new ArrayList<>());
        }

        if (modelingSubmission.getLatestResult() != null && !authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise)) {
            modelingSubmission.getLatestResult().setAssessor(null);
        }

        return ResponseEntity.ok(modelingSubmission);
    }
}

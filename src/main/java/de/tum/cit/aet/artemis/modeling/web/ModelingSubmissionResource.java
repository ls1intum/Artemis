package de.tum.cit.aet.artemis.modeling.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.api.ExamAccessApi;
import de.tum.cit.aet.artemis.exam.api.ExamSubmissionApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.web.AbstractSubmissionResource;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingSubmissionService;

/**
 * REST controller for managing ModelingSubmission.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/modeling/")
public class ModelingSubmissionResource extends AbstractSubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(ModelingSubmissionResource.class);

    private static final String ENTITY_NAME = "modelingSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ModelingSubmissionService modelingSubmissionService;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final Optional<ExamAccessApi> examAccessApi;

    private final Optional<ExamSubmissionApi> examSubmissionApi;

    public ModelingSubmissionResource(SubmissionRepository submissionRepository, ModelingSubmissionService modelingSubmissionService,
            ModelingExerciseRepository modelingExerciseRepository, AuthorizationCheckService authCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository,
            GradingCriterionRepository gradingCriterionRepository, Optional<ExamSubmissionApi> examSubmissionApi, StudentParticipationRepository studentParticipationRepository,
            ModelingSubmissionRepository modelingSubmissionRepository, Optional<ExamAccessApi> examAccessApi) {
        super(submissionRepository, authCheckService, userRepository, exerciseRepository, modelingSubmissionService, studentParticipationRepository);
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.examSubmissionApi = examSubmissionApi;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.examAccessApi = examAccessApi;
    }

    /**
     * POST /exercises/{exerciseId}/modeling-submissions : Create a new modeling submission. This is called when a student saves his model the first time after
     * starting the exercise or starting a retry.
     *
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param modelingSubmission the modelingSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/modeling-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@PathVariable long exerciseId, @Valid @RequestBody ModelingSubmission modelingSubmission) {
        log.debug("REST request to create modeling submission: {}", modelingSubmission.getModel());
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modeling submission cannot already have an ID", ENTITY_NAME, "idExists");
        }
        return handleModelingSubmission(exerciseId, modelingSubmission);
    }

    /**
     * PUT /exercises/{exerciseId}/modeling-submissions : Updates an existing modeling submission or creates a new one.
     * This function is called by the modeling editor for saving and submitting modeling submissions.
     * Submit specific handling occurs in the ModelingSubmissionService.handleModelingSubmission() and save() methods.
     *
     * @param exerciseId         the id of the exercise for which to init a participation
     * @param modelingSubmission the modelingSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingSubmission, or with status 400 (Bad Request) if the modelingSubmission is not valid, or
     *         with status 500 (Internal Server Error) if the modelingSubmission couldn't be updated
     */
    @PutMapping("exercises/{exerciseId}/modeling-submissions")
    @EnforceAtLeastStudent
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@PathVariable long exerciseId, @Valid @RequestBody ModelingSubmission modelingSubmission) {
        if (modelingSubmission.getId() == null) {
            return createModelingSubmission(exerciseId, modelingSubmission);
        }
        return handleModelingSubmission(exerciseId, modelingSubmission);
    }

    @NotNull
    private ResponseEntity<ModelingSubmission> handleModelingSubmission(Long exerciseId, ModelingSubmission modelingSubmission) {
        long start = System.currentTimeMillis();
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var exercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);

        if (exercise.isExamExercise()) {
            ExamSubmissionApi api = examSubmissionApi.orElseThrow(() -> new ExamApiNotPresentException(ExamSubmissionApi.class));
            // Apply further checks if it is an exam submission
            api.checkSubmissionAllowanceElseThrow(exercise, user);

            // Prevent multiple submissions (currently only for exam submissions)
            modelingSubmission = (ModelingSubmission) api.preventMultipleSubmissions(exercise, modelingSubmission, user);
        }

        // Check if the user is allowed to submit
        modelingSubmissionService.checkSubmissionAllowanceElseThrow(exercise, modelingSubmission, user);

        modelingSubmission = modelingSubmissionService.handleModelingSubmission(modelingSubmission, exercise, user);
        modelingSubmissionService.hideDetails(modelingSubmission, user);
        long end = System.currentTimeMillis();
        log.info("save took {}ms for exercise {} and user {}", end - start, exerciseId, user.getLogin());
        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /exercises/{exerciseId}/modeling-submissions: get all modeling submissions by exercise id and correction round.
     * If the parameter assessedByTutor is true, this method will return
     * only return all the modeling submissions where the tutor has a result associated.
     * In case of exam exercise, it filters out all test run submissions.
     *
     * @param exerciseId      id of the exercise for which the modeling submission should be returned
     * @param correctionRound - correctionRound for which the submissions' results should be fetched
     * @param submittedOnly   if true, it returns only submission with submitted flag set to true
     * @param assessedByTutor if true, it returns only the submissions which are assessed by the current user as a tutor
     * @return a list of modeling submissions
     */
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("exercises/{exerciseId}/modeling-submissions")
    @EnforceAtLeastTutor
    public ResponseEntity<List<Submission>> getAllModelingSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get all modeling upload submissions");
        return super.getAllSubmissions(exerciseId, submittedOnly, assessedByTutor, correctionRound);
    }

    /**
     * GET /modeling-submissions/{submissionId} : Gets an existing modelingSubmission with result. If no result exists for this submission a new empty result object
     * might be created and assigned to the submission (when the user is at least a tutor).
     * In case an instructors calls, the resultId is used first. In case the resultId is not set, the correctionRound is used.
     * In case neither resultId nor correctionRound is set, the first correctionRound is used.
     *
     * @param submissionId    the id of the modelingSubmission to retrieve
     * @param correctionRound correction round for which we prepare the submission
     * @param resultId        the resultId for which we want to get the submission
     * @param withoutResults  No result will be created or loaded and the exercise won't be locked when this is set
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission for the given id, or with status 404 (Not Found) if the modelingSubmission could not be
     *         found
     */
    @GetMapping("modeling-submissions/{submissionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound, @RequestParam(value = "resultId", required = false) Long resultId,
            @RequestParam(value = "withoutResults", defaultValue = "false") boolean withoutResults) {
        log.debug("REST request to get ModelingSubmission with id: {}", submissionId);

        var modelingSubmission = modelingSubmissionRepository.findByIdWithExerciseAthenaConfigElseThrow(submissionId);
        var studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        var modelingExercise = (ModelingExercise) studentParticipation.getExercise();

        final User user = userRepository.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastStudentForExercise(modelingExercise, user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this modeling submission.");
        }

        if (!authCheckService.isAllowedToAssessExercise(modelingExercise, user, resultId)) {
            // only the owner of the participation can see the submission
            if (!authCheckService.isOwnerOfParticipation(studentParticipation, user)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this modeling submission.");
            }

            // anonymize the submission
            modelingSubmission.setParticipation(null);
            modelingSubmission.setResults(null);
            modelingSubmission.setSubmissionDate(null);
            return ResponseEntity.ok(modelingSubmission);
        }

        // now we can assume the user is at least a tutor for the underlying exercise
        var gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(modelingExercise.getId());
        modelingExercise.setGradingCriteria(gradingCriteria);

        if (!withoutResults) {
            // load submission with results either by resultId or by correctionRound
            if (resultId != null) {
                // load the submission with additional needed properties
                modelingSubmission = (ModelingSubmission) submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submissionId);
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
     * @param exerciseId      id of the exercise for which the modeling submission should be returned
     * @param lockSubmission  optional value to define if the submission should be locked and has the value of false if not set manually
     * @param correctionRound correctionRound for which submissions without a result should be returned
     * @return the ResponseEntity with status 200 (OK) and a modeling submission without assessment in body
     */
    @GetMapping("exercises/{exerciseId}/modeling-submission-without-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<ModelingSubmission> getModelingSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {

        log.debug("REST request to get a modeling submission without assessment");
        final var exercise = modelingExerciseRepository.findWithAthenaConfigByIdElseThrow(exerciseId);
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

        var submission = modelingSubmissionService.findRandomSubmissionWithoutExistingAssessment(lockSubmission, correctionRound, modelingExercise, isExamMode).orElse(null);

        if (submission != null) {
            // needed to show the grading criteria in the assessment view
            Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
            modelingExercise.setGradingCriteria(gradingCriteria);
            // Make sure the exercise is connected to the participation in the json response
            submission.getParticipation().setExercise(modelingExercise);
            this.modelingSubmissionService.hideDetails(submission, user);
        }

        return ResponseEntity.ok(submission);
    }

    /**
     * Encapsulates the result of participation validation.
     * <p>
     * This record holds:
     * <ul>
     * <li>The validated {@link StudentParticipation} instance.</li>
     * <li>The associated {@link ModelingExercise} instance (validated to be non-null and correct type).</li>
     * <li>A flag indicating if the current user has at least teaching assistant permissions for the exercise.</li>
     * </ul>
     *
     * @param studentParticipation the validated student participation
     * @param modelingExercise     the validated modeling exercise
     * @param isAtLeastTutor       true if the user has at least teaching assistant rights for the exercise, false otherwise
     */
    private record ValidationResult(StudentParticipation studentParticipation, ModelingExercise modelingExercise, boolean isAtLeastTutor) {
    }

    /**
     * Validates a student's participation in a modeling exercise and checks if the current user has the necessary permissions to access it.
     * <p>
     * This method performs the following validations:
     * <ul>
     * <li>Ensures the participation exists and has a non-null exercise.</li>
     * <li>Ensures the exercise is of type {@link ModelingExercise}; otherwise, throws an exception.</li>
     * <li>Checks if the current user is the owner of the participation or has at least teaching assistant permissions.</li>
     * <li>If the exercise is an exam exercise, checks if the user is allowed to access the result based on exam timing.</li>
     * </ul>
     * Upon successful validation, it returns a {@link ValidationResult} containing the participation, the modeling exercise, and the permission flag.
     *
     * @param participationId the ID of the student participation to validate
     * @return a {@link ValidationResult} containing the validated participation, modeling exercise, and permission flag
     * @throws BadRequestAlertException   if the participation has a null exercise or if the exercise is not a {@link ModelingExercise}
     * @throws AccessForbiddenException   if the user does not have the required access rights to view the participation
     * @throws ExamApiNotPresentException if the exam access API is required but not present
     */
    private ValidationResult validateParticipation(long participationId) {
        var studentParticipation = studentParticipationRepository.findByIdWithLatestSubmissionsResultsFeedbackElseThrow(participationId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var exercise = studentParticipation.getExercise();

        if (exercise == null) {
            throw new BadRequestAlertException("The exercise belonging to the student participation is null", ENTITY_NAME, "exerciseEmpty");
        }

        if (!(exercise instanceof ModelingExercise modelingExercise)) {
            throw new BadRequestAlertException("The exercise of the student participation is not a modeling exercise", ENTITY_NAME, "wrongExerciseType");
        }

        // Students can only see their own models (to prevent cheating). TAs, instructors and admins can see all models.
        boolean isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantForExercise(modelingExercise, user);
        if (!(authCheckService.isOwnerOfParticipation(studentParticipation) || isAtLeastTutor)) {
            throw new AccessForbiddenException("You are not allowed to access this modeling submission.");
        }

        // Exam exercises cannot be seen by students between the endDate and the publishResultDate
        if (modelingExercise.isExamExercise()) {
            ExamAccessApi api = examAccessApi.orElseThrow(() -> new ExamApiNotPresentException(ExamAccessApi.class));
            api.checkIfAllowedToGetExamResult(modelingExercise, studentParticipation, user);
        }
        return new ValidationResult(studentParticipation, modelingExercise, isAtLeastTutor);
    }

    /**
     * Returns the submission with data needed for the modeling editor, which includes the participation, the model and the result (if the assessment was already submitted).
     *
     * @param participationId the participationId for which to find the submission and data for the modeling editor
     * @return the ResponseEntity with the submission as body
     */
    @GetMapping("participations/{participationId}/latest-modeling-submission")
    @EnforceAtLeastStudent
    public ResponseEntity<ModelingSubmission> getLatestModelingSubmission(@PathVariable long participationId) {
        log.debug("REST request to get latest modeling submission for participation: {}", participationId);
        var validationResult = validateParticipation(participationId);
        var studentParticipation = validationResult.studentParticipation;
        var exercise = validationResult.modelingExercise;

        Optional<Submission> optionalLatestSubmission = studentParticipation.getSubmissions().stream().findFirst();
        ModelingSubmission modelingSubmission;
        if (optionalLatestSubmission.isEmpty()) {
            // this should never happen as the submission is initialized along with the participation when the exercise is started
            modelingSubmission = new ModelingSubmission();
            modelingSubmission.setParticipation(studentParticipation);
        }
        else {
            // only try to get and set the model if the modelingSubmission existed before
            modelingSubmission = (ModelingSubmission) optionalLatestSubmission.get();
        }

        // do not send the result to the client if the assessment is not finished
        Result latestResult = modelingSubmission.getLatestResult();
        if (latestResult != null && (latestResult.getCompletionDate() == null || latestResult.getAssessor() == null)) {
            modelingSubmission.setResults(List.of());
        }

        if (!ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            // We want to have the preliminary feedback before the assessment due date too
            List<Result> athenaResults = modelingSubmission.getResults().stream().filter(result -> result != null && result.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA)
                    .toList();
            modelingSubmission.setResults(athenaResults);
        }

        if (modelingSubmission.getLatestResult() != null && !authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            modelingSubmission.getLatestResult().filterSensitiveInformation();
        }

        // make sure sensitive information are not sent to the client
        exercise.filterSensitiveInformation();
        if (exercise.isExamExercise()) {
            exercise.getExerciseGroup().setExam(null);
        }

        return ResponseEntity.ok(modelingSubmission);
    }

    /**
     * GET /participations/{participationId}/submissions-with-results : get submissions with results for a particular student participation.
     * When the assessment period is not over yet, only submissions with Athena results are returned.
     * When the assessment period is over, both Athena and normal results are returned.
     *
     * @param participationId the id of the participation for which to get the submissions with results
     * @return the ResponseEntity with status 200 (OK) and with body the list of submissions with results and feedbacks, or with status 404 (Not Found) if the participation could
     *         not be found
     */
    @GetMapping("participations/{participationId}/submissions-with-results")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Submission>> getSubmissionsWithResultsForParticipation(@PathVariable long participationId) {
        log.debug("REST request to get submissions with results for participation: {}", participationId);

        var validationResult = validateParticipation(participationId);
        var studentParticipation = validationResult.studentParticipation;

        // Get the submissions associated with the participation
        Set<Submission> submissions = studentParticipation.getSubmissions();

        // Filter submissions to only include those with relevant results
        List<Submission> submissionsWithResults = submissions.stream().filter(submission -> {

            submission.setParticipation(studentParticipation);

            // Filter results within each submission based on assessment type and period
            List<Result> filteredResults = submission.getResults().stream().filter(result -> {
                if (!validationResult.isAtLeastTutor) {
                    if (ExerciseDateService.isAfterAssessmentDueDate(validationResult.modelingExercise)) {
                        return true; // Include all results if the assessment period is over
                    }
                    else {
                        return result.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA; // Only include Athena results if the assessment period is not over
                    }
                }
                else {
                    return true; // Tutors and above can see all results
                }
            }).peek(Result::filterSensitiveInformation).sorted(Comparator.comparing(Result::getCompletionDate).reversed()).toList();

            // Set filtered results back into the submission if any results remain after filtering
            if (!filteredResults.isEmpty()) {
                submission.setResults(filteredResults);
                return true; // Include submission as it has relevant results
            }
            return false;
        }).toList();

        return ResponseEntity.ok().body(submissionsWithResults);
    }
}

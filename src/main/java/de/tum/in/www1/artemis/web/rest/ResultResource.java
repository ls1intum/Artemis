package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Result.
 */
@RestController
@RequestMapping("/api")
public class ResultResource {

    private final Logger log = LoggerFactory.getLogger(ResultResource.class);

    private static final String ENTITY_NAME = "result";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    @Value("${artemis.continuous-integration.artemis-authentication-token-value}")
    private String ARTEMIS_AUTHENTICATION_TOKEN_VALUE = "";

    private final ResultRepository resultRepository;

    private final ParticipationService participationService;

    private final ResultService resultService;

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final WebsocketMessagingService messagingService;

    private final LtiService ltiService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final AssessmentService assessmentService;

    public ResultResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ParticipationService participationService, ResultService resultService,
            ExerciseService exerciseService, AuthorizationCheckService authCheckService, Optional<ContinuousIntegrationService> continuousIntegrationService, LtiService ltiService,
            ResultRepository resultRepository, WebsocketMessagingService messagingService, ProgrammingSubmissionService programmingSubmissionService, UserService userService,
            AssessmentService assessmentService) {
        this.resultRepository = resultRepository;
        this.participationService = participationService;
        this.resultService = resultService;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.messagingService = messagingService;
        this.ltiService = ltiService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.assessmentService = assessmentService;
        this.userService = userService;
    }

    /**
     * POST /participations/{participationId}/manual-results : Create a new manual result for a programming exercise (Do NOT use it for other exercise types)
     * NOTE: we deviate from the standard URL scheme to avoid conflicts with a different POST request on results
     *
     * @param participationId the id of the participation for which the new manual result is created
     * @param newResult the result to create
     * @return the ResponseEntity with status 201 (Created) and with body the new result, or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("participations/{participationId}/manual-results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> createProgrammingExerciseManualResult(@PathVariable Long participationId, @RequestBody Result newResult) throws URISyntaxException {
        log.debug("REST request to create a new result : {}", newResult);
        final var participation = participationService.findOneWithEagerResultsAndCourse(participationId);
        final var latestExistingResult = participation.findLatestResult();
        if (latestExistingResult != null && latestExistingResult.getAssessmentType() == AssessmentType.MANUAL) {
            // prevent that tutors create multiple manual results
            forbidden();
        }

        // make sure that the participation cannot be manipulated on the client side
        newResult.setParticipation(participation);
        final var exercise = (ProgrammingExercise) participation.getExercise();
        final var course = exercise.getCourse();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null) || !exercise.areManualResultsAllowed()) {
            return forbidden();
        }
        if (!(participation instanceof ProgrammingExerciseStudentParticipation)) {
            return badRequest();
        }

        if (newResult.getId() != null) {
            throw new BadRequestAlertException("A new result cannot already have an ID.", ENTITY_NAME, "idexists");
        }
        else if (newResult.getResultString() == null) {
            throw new BadRequestAlertException("Result string is required.", ENTITY_NAME, "resultStringNull");
        }
        else if (newResult.getScore() == null) {
            throw new BadRequestAlertException("Score is required.", ENTITY_NAME, "scoreNull");
        }
        else if (newResult.getScore() != 100 && newResult.isSuccessful()) {
            throw new BadRequestAlertException("Only result with score 100% can be successful.", ENTITY_NAME, "scoreAndSuccessfulNotMatching");
        }
        else if (!newResult.getFeedbacks().isEmpty() && newResult.getFeedbacks().stream().anyMatch(feedback -> feedback.getText() == null)) {
            throw new BadRequestAlertException("In case feedback is present, feedback text and detail text are mandatory.", ENTITY_NAME, "feedbackTextOrDetailTextNull");
        }

        // Create manual submission with last commit hash und current time stamp.
        ProgrammingSubmission submission = programmingSubmissionService.createSubmissionWithLastCommitHashForParticipation((ProgrammingExerciseStudentParticipation) participation,
                SubmissionType.MANUAL);
        newResult.setSubmission(submission);
        newResult = resultService.createNewRatedManualResult(newResult, true);

        return ResponseEntity.created(new URI("/api/participations/" + participation.getId() + "/manual-results/" + newResult.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, newResult.getId().toString())).body(newResult);
    }

    /**
     * PUT /participations/{participationId}/manual-results : Updates an existing result.
     *
     * @param participationId the id of the participation for which the manual result is updated
     * @param updatedResult the result to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated result, or with status 400 (Bad Request) if the result is not valid, or with status 500 (Internal
     *         Server Error) if the result couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("participations/{participationId}/manual-results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateProgrammingExerciseManualResult(@PathVariable Long participationId, @RequestBody Result updatedResult) throws URISyntaxException {
        log.debug("REST request to update Result : {}", updatedResult);
        final var participation = participationService.findOneWithEagerResultsAndCourse(participationId);
        User user = userService.getUserWithGroupsAndAuthorities();
        // make sure that the participation cannot be manipulated on the client side
        updatedResult.setParticipation(participation);
        // TODO: we should basically set the submission here to prevent possible manipulation of the submission
        if (updatedResult.getSubmission() == null) {
            throw new BadRequestAlertException("The submission is not connected to the result.", ENTITY_NAME, "submissionMissing");
        }

        final var exercise = (ProgrammingExercise) participation.getExercise();
        final var course = exercise.getCourse();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null) || !exercise.areManualResultsAllowed()) {
            return forbidden();
        }
        if (updatedResult.getId() == null) {
            return createProgrammingExerciseManualResult(participationId, updatedResult);
        }
        // get the original result with assessor for permission checks below, otherwise the client could override the assessor and the check would not make any sense
        Result originalResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(updatedResult.getId()).get();

        final var isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise);
        if (!isAllowedToOverrideExistingResult(originalResult, exercise, user, isAtLeastInstructor)) {
            return forbidden("assessment", "assessmentSaveNotAllowed", "The user is not allowed to override the assessment");
        }

        updatedResult = resultService.updateManualProgrammingExerciseResult(updatedResult);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedResult.getId().toString())).body(updatedResult);
    }

    protected boolean isAllowedToOverrideExistingResult(@NotNull Result existingResult, Exercise exercise, User user, boolean isAtLeastInstructor) {

        // if the assessor is null, the user is allowed to save / submit / override the existing result
        final var isAssessor = existingResult.getAssessor() == null || user.equals(existingResult.getAssessor());
        if (existingResult.getCompletionDate() == null) {
            // if the result exists, but was not yet submitted (i.e. completionDate not set), the tutor and the instructor can override, independent of the assessment due date
            return isAssessor || isAtLeastInstructor;
        }
        // if the result was already submitted, the tutor can only override before a potentially existing assessment due date
        var assessmentDueDate = exercise.getAssessmentDueDate();
        // NOTE: the following line deviates intentionally from assessmentService.isAllowedToOverrideExistingResult because currently we do not use assessmentDueDate
        // and tutors should be able to override the created results when the assessmentDueDate is null
        final var isBeforeAssessmentDueDate = assessmentDueDate == null || ZonedDateTime.now().isBefore(assessmentDueDate);
        return (isAssessor && isBeforeAssessmentDueDate) || isAtLeastInstructor;

        // TODO at the moment we use a different logic for migration and compatibility reasons, but basically we should invoke the following method in the future
        // return assessmentService.isAllowedToOverrideExistingResult(existingResult, exercise, user, isAtLeastInstructor);
    }

    /**
     * This method is used by the CI system to inform Artemis about a new programming exercise build result.
     * It will make sure to:
     * - Create a result from the build result including its feedbacks
     * - Assign the result to an existing submission OR create a new submission if needed
     * - Update the result's score based on the exercise's test cases (weights, etc.)
     * - Update the exercise's test cases if the build is from a solution participation
     *
     * @param token CI auth token
     * @param requestBody build result of CI system
     * @return a ResponseEntity to the CI system
     */
    @PostMapping(value = Constants.NEW_RESULT_RESOURCE_PATH)
    public ResponseEntity<?> notifyNewProgrammingExerciseResult(@RequestHeader("Authorization") String token, @RequestBody Object requestBody) {
        log.debug("Received result notify (NEW)");
        if (token == null || !token.equals(ARTEMIS_AUTHENTICATION_TOKEN_VALUE)) {
            log.info("Cancelling request with invalid token {}", token);
            return forbidden(); // Only allow endpoint when using correct token
        }

        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();

        // Retrieving the plan key can fail if e.g. the requestBody is malformated. In this case nothing else can be done.
        String planKey;
        try {
            planKey = continuousIntegrationService.get().getPlanKey(requestBody);
        }
        // TODO: How can we catch a more specific exception here? Because of the adapter pattern this is always just Exception...
        catch (Exception ex) {
            log.error("Exception encountered when trying to retrieve the plan key from a request a new programming exercise result: {}, {}", ex, requestBody);
            return badRequest();
        }
        log.info("Artemis received a new result from Bamboo for build plan {}", planKey);

        // Try to retrieve the participation with the build plan key.
        Optional<ProgrammingExerciseParticipation> optionalParticipation = getParticipationWithResults(planKey);
        if (optionalParticipation.isEmpty()) {
            log.warn("Participation is missing for notifyResultNew (PlanKey: {}).", planKey);
            return notFound();
        }

        ProgrammingExerciseParticipation participation = optionalParticipation.get();
        Optional<Result> result;
        // Process the new result from the build result.
        result = resultService.processNewProgrammingExerciseResult((Participation) participation, requestBody);

        // Only notify the user about the new result if the result was created successfully.
        if (result.isPresent()) {
            log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result.get(), result.get().getSubmission(),
                    result.get().getParticipation());
            // notify user via websocket
            messagingService.broadcastNewResult((Participation) participation, result.get());

            // TODO: can we avoid to invoke this code for non LTI students? (to improve performance)
            // if (participation.isLti()) {
            // }
            // handles new results and sends them to LTI consumers
            if (participation instanceof ProgrammingExerciseStudentParticipation) {
                ltiService.onNewBuildResult((ProgrammingExerciseStudentParticipation) participation);
            }
            log.info("The new result for {} was saved successfully", planKey);
        }
        return ResponseEntity.ok().build();
    }

    private Optional<ProgrammingExerciseParticipation> getParticipationWithResults(String planKey) {
        // we have to support template, solution and student build plans here
        if (planKey.contains(BuildPlanType.TEMPLATE.getName())) {
            Optional<TemplateProgrammingExerciseParticipation> templateParticipation = participationService.findTemplateParticipationByBuildPlanId(planKey);
            // we have to convert the optional type here to make Java happy
            if (templateParticipation.isPresent()) {
                return Optional.of(templateParticipation.get());
            }
            else {
                return Optional.empty();
            }
        }
        else if (planKey.contains(BuildPlanType.SOLUTION.getName())) {
            Optional<SolutionProgrammingExerciseParticipation> solutionParticipation = participationService.findSolutionParticipationByBuildPlanId(planKey);
            // we have to convert the optional type here to make Java happy
            if (solutionParticipation.isPresent()) {
                return Optional.of(solutionParticipation.get());
            }
            else {
                return Optional.empty();
            }
        }
        List<ProgrammingExerciseStudentParticipation> participations = participationService.findByBuildPlanIdWithEagerResults(planKey);
        Optional<ProgrammingExerciseStudentParticipation> participation = Optional.empty();
        if (participations.size() > 0) {
            participation = Optional.of(participations.get(0));
            if (participations.size() > 1) {
                // in the rare case of multiple participations, take the latest one.
                for (ProgrammingExerciseStudentParticipation otherParticipation : participations) {
                    if (otherParticipation.getInitializationDate().isAfter(participation.get().getInitializationDate())) {
                        participation = Optional.of(otherParticipation);
                    }
                }
            }
        }

        // we have to convert the optional type here to make Java happy
        if (participation.isPresent()) {
            return Optional.of(participation.get());
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * GET /exercises/:exerciseId/results : get the successful results for an exercise, ordered ascending by build completion date.
     *
     * @param exerciseId the id of the exercise for which to retrieve the results
     * @param withSubmissions defines if submissions are loaded from the database for the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping(value = "exercises/{exerciseId}/results")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Result>> getResultsForExercise(@PathVariable Long exerciseId, @RequestParam(defaultValue = "true") boolean withSubmissions) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get Results for Exercise : {}", exerciseId);

        Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);
        Course course = exercise.getCourse();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            return forbidden();
        }

        List<Result> results = new ArrayList<>();

        List<StudentParticipation> participations = participationService.findByExerciseIdWithEagerSubmissionsResultAssessor(exerciseId);
        for (StudentParticipation participation : participations) {
            // Filter out participations without Students
            if (participation.getStudent() == null) {
                continue;
            }

            Submission relevantSubmissionWithResult = exercise.findLatestSubmissionWithRatedResultWithCompletionDate(participation, true);
            if (relevantSubmissionWithResult == null || relevantSubmissionWithResult.getResult() == null) {
                continue;
            }

            relevantSubmissionWithResult.getResult().setSubmissionCount(participation.getSubmissions().size());
            if (withSubmissions) {
                relevantSubmissionWithResult.getResult().setSubmission(relevantSubmissionWithResult);
            }
            results.add(relevantSubmissionWithResult.getResult());
        }

        log.info("getResultsForExercise took " + (System.currentTimeMillis() - start) + "ms for " + results.size() + " results.");

        if (withSubmissions) {
            results = results.stream().filter(result -> result.getSubmission() != null && result.getSubmission().isSubmitted()).collect(Collectors.toList());
        }

        // remove unnecessary elements in the json response
        results.forEach(result -> {
            result.getParticipation().setResults(null);
            result.getParticipation().setSubmissions(null);
            result.getParticipation().setExercise(null);
        });

        return ResponseEntity.ok().body(results);
    }

    /**
     * GET /results/:id : get the "id" result.
     *
     * @param resultId the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping("/results/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getResult(@PathVariable Long resultId) {
        log.debug("REST request to get Result : {}", resultId);
        Optional<Result> result = resultRepository.findById(resultId);
        if (result.isPresent()) {
            Participation participation = result.get().getParticipation();
            Course course = participation.getExercise().getCourse();
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
                return forbidden();
            }
        }
        return result.map(foundResult -> new ResponseEntity<>(foundResult, HttpStatus.OK)).orElse(notFound());
    }

    /**
     * GET /participations/:participationId/latest-result : get the latest result with feedbacks for the given participation.
     * The order of results is determined by completionDate desc.
     *
     * @param participationId the id of the participation for which to retrieve the latest result.
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}/latest-result")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getLatestResultWithFeedbacks(@PathVariable Long participationId) {
        log.debug("REST request to get latest result for participation : {}", participationId);
        Participation participation = participationService.findOne(participationId);

        if (participation instanceof StudentParticipation && !participationService.canAccessParticipation((StudentParticipation) participation)
                || participation instanceof ProgrammingExerciseParticipation
                        && !programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation)) {
            return forbidden();
        }

        Optional<Result> result = resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDesc(participation.getId());
        return result.map(ResponseEntity::ok).orElse(notFound());
    }

    /**
     * GET /results/:id/details : get the build result details from Bamboo for the "id" result. This method is only invoked if the result actually includes details (e.g. feedback
     * or build errors)
     *
     * @param resultId the id of the result to retrieve. If the participation related to the result is not a StudentParticipation or ProgrammingExerciseParticipation, the endpoint will return forbidden!
     * @return the ResponseEntity with status 200 (OK) and with body the result, status 404 (Not Found) if the result does not exist or 403 (forbidden) if the user does not have permissions to access the participation.
     */
    @GetMapping(value = "/results/{resultId}/details")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Feedback>> getResultDetails(@PathVariable Long resultId) {
        log.debug("REST request to get Result : {}", resultId);
        Optional<Result> optionalResult = resultRepository.findByIdWithEagerFeedbacks(resultId);
        if (optionalResult.isEmpty()) {
            return notFound();
        }
        Result result = optionalResult.get();
        Participation participation = result.getParticipation();

        // The permission check depends on the participation type (normal participations vs. programming exercise participations).
        if (participation instanceof StudentParticipation) {
            if (!participationService.canAccessParticipation((StudentParticipation) participation)) {
                return forbidden();
            }
        }
        else if (participation instanceof ProgrammingExerciseParticipation) {
            if (!programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation)) {
                return forbidden();
            }
        }
        else {
            // This would be the case that a new participation type is introduced, without this the user would have access to it regardless of the permissions.
            return forbidden();
        }

        return new ResponseEntity<>(result.getFeedbacks(), HttpStatus.OK);
    }

    /**
     * DELETE /results/:id : delete the "id" result.
     *
     * @param resultId the id of the result to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/results/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteResult(@PathVariable Long resultId) {
        log.debug("REST request to delete Result : {}", resultId);
        Optional<Result> result = resultRepository.findById(resultId);
        if (result.isPresent()) {
            Participation participation = result.get().getParticipation();
            Course course = participation.getExercise().getCourse();
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
                return forbidden();
            }
            resultRepository.deleteById(resultId);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, resultId.toString())).build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * GET /results/submission/{submissionId} : get the result for a submission id
     *
     * @param submissionId the id of the submission
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping(value = "/results/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getResultForSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get Result for submission : {}", submissionId);
        Optional<Result> result = resultRepository.findDistinctBySubmissionId(submissionId);
        return ResponseUtil.wrapOrNotFound(result);
    }

    /**
     * Creates a new example result for the provided example submission ID.
     *
     * @param submissionId The submission ID for which an example result should get created
     * @param isProgrammingExerciseWithFeedback Whether the related exercise is a programming exercise with feedback
     * @return The newly created result
     */
    @PostMapping("/submissions/{submissionId}/example-result")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> createExampleResult(@PathVariable long submissionId,
            @RequestParam(defaultValue = "false", required = false) boolean isProgrammingExerciseWithFeedback) {
        log.debug("REST request to create a new example result for submission: {}", submissionId);
        final var result = resultService.createNewExampleResultForSubmissionWithExampleSubmission(submissionId, isProgrammingExerciseWithFeedback);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    /**
     * Creates a new result for the provided exercise and student (a participation and an empty submission will also be created if they do not exist yet)
     *
     * @param exerciseId The exercise ID for which a result should get created
     * @param studentLogin The student login (username) for which a result should get created
     * @param result The result to be created
     * @return The newly created result
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "/exercises/{exerciseId}/external-submission-results")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> createResultForExternalSubmission(@PathVariable Long exerciseId, @RequestParam String studentLogin, @RequestBody Result result)
            throws URISyntaxException {
        log.debug("REST request to create Result for External Submission for Exercise : {}", exerciseId);

        Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);
        if (exercise.getDueDate() == null || ZonedDateTime.now().isBefore(exercise.getDueDate())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "externalSubmissionBeforeDueDate",
                    "External submissions are not supported before the exercise due date.")).build();
        }
        if (exercise instanceof QuizExercise) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "externalSubmissionForQuizExercise",
                    "External submissions are not supported for Quiz exercises.")).build();
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<User> student = userService.getUserWithAuthoritiesByLogin(studentLogin);
        Course course = exercise.getCourse();
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }
        if (student.isEmpty() || !authCheckService.isAtLeastStudentInCourse(course, student.get())) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "studentNotFound", "The student could not be found in this course.")).build();
        }

        // Check if a result exists already for this exercise and student. If so, do nothing and just inform the instructor.
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseIdAndStudentLoginAnyStateWithEagerResults(exerciseId, studentLogin);
        Optional<Result> optionalResult = optionalParticipation.map(Participation::findLatestResult);
        if (optionalResult.isPresent()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "resultAlreadyExists", "A result already exists for this student in this exercise."))
                    .build();
        }

        // Create a participation and a submitted empty submission if they do not exist yet
        StudentParticipation participation = participationService.createParticipationWithEmptySubmissionIfNotExisting(exercise, student.get(), SubmissionType.EXTERNAL);
        Submission submission = participationService.findOneWithEagerSubmissions(participation.getId()).findLatestSubmission().get();
        result.setParticipation(participation);
        result.setSubmission(submission);

        // Create a new manual result which can be rated or unrated depending on what was specified in the create form
        Result savedResult = resultService.createNewManualResult(result, exercise instanceof ProgrammingExercise, result.isRated());

        return ResponseEntity.created(new URI("/api/results/" + savedResult.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, savedResult.getId().toString())).body(savedResult);
    }
}

package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.LtiService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.web.rest.dto.ResultWithPointsPerGradingCriterionDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

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
    private String artemisAuthenticationTokenValue = "";

    private final ResultRepository resultRepository;

    private final ParticipationService participationService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final ResultService resultService;

    private final ExamDateService examDateService;

    private final ExerciseRepository exerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final WebsocketMessagingService messagingService;

    private final LtiService ltiService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ParticipationRepository participationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public ResultResource(ProgrammingExerciseParticipationService programmingExerciseParticipationService, ParticipationService participationService,
            ExampleSubmissionRepository exampleSubmissionRepository, ResultService resultService, ExerciseRepository exerciseRepository, AuthorizationCheckService authCheckService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, LtiService ltiService, ResultRepository resultRepository,
            WebsocketMessagingService messagingService, UserRepository userRepository, ExamDateService examDateService,
            ProgrammingExerciseGradingService programmingExerciseGradingService, ParticipationRepository participationRepository,
            StudentParticipationRepository studentParticipationRepository, TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.exerciseRepository = exerciseRepository;
        this.resultRepository = resultRepository;
        this.participationService = participationService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.resultService = resultService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.messagingService = messagingService;
        this.ltiService = ltiService;
        this.userRepository = userRepository;
        this.examDateService = examDateService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.participationRepository = participationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
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
    @PostMapping(Constants.NEW_RESULT_RESOURCE_PATH)
    public ResponseEntity<?> processNewProgrammingExerciseResult(@RequestHeader("Authorization") String token, @RequestBody Object requestBody) {
        log.debug("Received result notify (NEW)");
        if (token == null || !token.equals(artemisAuthenticationTokenValue)) {
            log.info("Cancelling request with invalid token {}", token);
            throw new AccessForbiddenException(); // Only allow endpoint when using correct token
        }

        // No 'user' is properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();

        // Retrieving the plan key can fail if e.g. the requestBody is malformed. In this case nothing else can be done.
        String planKey;
        try {
            planKey = continuousIntegrationService.get().getPlanKey(requestBody);
        }
        catch (ContinuousIntegrationException cISException) {
            log.error("Exception encountered when trying to retrieve the plan key from a request a new programming exercise result: {}, {} :"
                    + "Your CIS encountered an Exception while trying to retrieve the build plan ", cISException, requestBody);
            throw new BadRequestAlertException(
                    "The continuous integration server encountered an exception when trying to retrieve the plan key from a request a new programming exercise result", "BuildPlan",
                    "ciExceptionForBuildPlanKey");
        }
        log.info("Artemis received a new result for build plan {}", planKey);

        // Try to retrieve the participation with the build plan key.
        var participation = getParticipationWithResults(planKey);
        if (participation == null) {
            log.warn("Participation is missing for notifyResultNew (PlanKey: {}).", planKey);
            throw new EntityNotFoundException("Participation for build plan " + planKey + " does not exist");
        }

        // Process the new result from the build result.
        Optional<Result> optResult = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, requestBody);

        // Only notify the user about the new result if the result was created successfully.
        if (optResult.isPresent()) {
            Result result = optResult.get();
            log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, result.getSubmission(), result.getParticipation());
            // notify user via websocket
            messagingService.broadcastNewResult((Participation) participation, result);
            if (participation instanceof StudentParticipation) {
                // do not try to report results for template or solution participations
                ltiService.onNewResult((ProgrammingExerciseStudentParticipation) participation);
            }
            log.info("The new result for {} was saved successfully", planKey);
        }
        return ResponseEntity.ok().build();
    }

    @Nullable
    private ProgrammingExerciseParticipation getParticipationWithResults(String planKey) {
        // we have to support template, solution and student build plans here
        if (planKey.endsWith("-" + BuildPlanType.TEMPLATE.getName())) {
            return templateProgrammingExerciseParticipationRepository.findByBuildPlanIdWithResults(planKey).orElse(null);
        }
        else if (planKey.endsWith("-" + BuildPlanType.SOLUTION.getName())) {
            return solutionProgrammingExerciseParticipationRepository.findByBuildPlanIdWithResults(planKey).orElse(null);
        }
        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository.findByBuildPlanId(planKey);
        ProgrammingExerciseStudentParticipation participation = null;
        if (!participations.isEmpty()) {
            participation = participations.get(0);
            if (participations.size() > 1) {
                // in the rare case of multiple participations, take the latest one.
                for (ProgrammingExerciseStudentParticipation otherParticipation : participations) {
                    if (otherParticipation.getInitializationDate().isAfter(participation.getInitializationDate())) {
                        participation = otherParticipation;
                    }
                }
            }
        }
        return participation;
    }

    /**
     * GET /exercises/:exerciseId/results : get the successful results for an exercise, ordered ascending by build completion date.
     *
     * @param exerciseId the id of the exercise for which to retrieve the results
     * @param withSubmissions defines if submissions are loaded from the database for the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping("exercises/{exerciseId}/results")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Result>> getResultsForExercise(@PathVariable Long exerciseId, @RequestParam(defaultValue = "true") boolean withSubmissions) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get Results for Exercise : {}", exerciseId);

        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        final List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(exerciseId, false);

        List<Result> results = resultsForExercise(exercise, participations, withSubmissions);
        log.info("getResultsForExercise took {}ms for {} results.", System.currentTimeMillis() - start, results.size());

        return ResponseEntity.ok().body(results);
    }

    /**
     * GET /exercises/:exerciseId/results-with-points-per-criterion : get the successful results for an exercise, ordered ascending by build completion date.
     * Also contains for each result the points the student achieved with manual feedback. Those points are grouped as sum for each grading criterion.
     *
     * @param exerciseId of the exercise for which to retrieve the results.
     * @param withSubmissions defines if submissions are loaded from the database for the results.
     * @return the ResponseEntity with status 200 (OK) and the list of results with points in body.
     */
    @GetMapping("exercises/{exerciseId}/results-with-points-per-criterion")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ResultWithPointsPerGradingCriterionDTO>> getResultsForExerciseWithPointsPerCriterion(@PathVariable Long exerciseId,
            @RequestParam(defaultValue = "true") boolean withSubmissions) {
        final Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        final List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessorFeedbacks(exerciseId, false);

        final Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        final List<Result> results = resultsForExercise(exercise, participations, withSubmissions);
        final List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = results.stream().map(result -> resultRepository.calculatePointsPerGradingCriterion(result, course))
                .toList();

        return ResponseEntity.ok().body(resultsWithPoints);
    }

    /**
     * Get the successful results for an exercise, ordered ascending by build completion date.
     *
     * @param exercise which the results belong to.
     * @param withSubmissions true, if each result should also contain the submissions.
     * @return a list of results as described above for the given exercise.
     */
    private List<Result> resultsForExercise(Exercise exercise, List<StudentParticipation> participations, boolean withSubmissions) {
        final List<Result> results = new ArrayList<>();

        for (StudentParticipation participation : participations) {
            // Filter out participations without students / teams
            if (participation.getParticipant() == null) {
                continue;
            }

            Submission relevantSubmissionWithResult = exercise.findLatestSubmissionWithRatedResultWithCompletionDate(participation, true);
            if (relevantSubmissionWithResult == null || relevantSubmissionWithResult.getLatestResult() == null) {
                continue;
            }

            participation.setSubmissionCount(participation.getSubmissions().size());
            if (withSubmissions) {
                relevantSubmissionWithResult.getLatestResult().setSubmission(relevantSubmissionWithResult);
            }
            results.add(relevantSubmissionWithResult.getLatestResult());
        }

        if (withSubmissions) {
            results.removeIf(result -> result.getSubmission() == null || !result.getSubmission().isSubmitted());
        }

        // remove unnecessary elements in the json response
        results.forEach(result -> {
            result.getParticipation().setResults(null);
            result.getParticipation().setSubmissions(null);
            result.getParticipation().setExercise(null);
        });

        return results;
    }

    /**
     * GET /participations/:participationId/results/:resultId : get the "id" result.
     *
     * @param participationId the id of the participation to the result
     * @param resultId the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}/results/{resultId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> getResult(@PathVariable Long participationId, @PathVariable Long resultId) {
        log.debug("REST request to get Result : {}", resultId);
        Result result = getResultForParticipationAndCheckAccess(participationId, resultId, Role.TEACHING_ASSISTANT);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    private Result getResultForParticipationAndCheckAccess(Long participationId, Long resultId, Role role) {
        Result result = resultRepository.findByIdElseThrow(resultId);
        Participation participation = result.getParticipation();
        if (!participation.getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId of the path doesnt match the participationId of the participation corresponding to the result " + resultId + "!",
                    "Participation", "400");
        }
        Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, null);
        return result;
    }

    /**
     * GET /participations/:participationId/latest-result : get the latest result with feedbacks for the given participation.
     * The order of results is determined by completionDate desc.
     *
     * @param participationId the id of the participation for which to retrieve the latest result.
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}/latest-result")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> getLatestResultWithFeedbacks(@PathVariable Long participationId) {
        log.debug("REST request to get latest result for participation : {}", participationId);
        Participation participation = participationRepository.findByIdElseThrow(participationId);

        if (participation instanceof StudentParticipation && !authCheckService.canAccessParticipation((StudentParticipation) participation)
                || participation instanceof ProgrammingExerciseParticipation
                        && !programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation)) {
            throw new AccessForbiddenException();
        }

        Result result = resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDescElseThrow(participation.getId());
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * GET /participations/:participationId/results/:resultId/details : get the build result details from CI service for the "id" result.
     * This method is only invoked if the result actually includes details (e.g. feedback or build errors)
     *
     * @param participationId  the id of the participation to the result
     * @param resultId the id of the result to retrieve. If the participation related to the result is not a StudentParticipation or ProgrammingExerciseParticipation, the endpoint will return forbidden!
     * @return the ResponseEntity with status 200 (OK) and with body the result, status 404 (Not Found) if the result does not exist or 403 (forbidden) if the user does not have permissions to access the participation.
     */
    @GetMapping("participations/{participationId}/results/{resultId}/details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Feedback>> getResultDetails(@PathVariable Long participationId, @PathVariable Long resultId) {
        log.debug("REST request to get Result : {}", resultId);
        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(resultId);
        Participation participation = result.getParticipation();
        if (!participation.getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId of the path doesnt match the participationId of the participation corresponding to the result " + resultId + " !",
                    "participationId", "400");
        }

        // The permission check depends on the participation type (normal participations vs. programming exercise participations).
        if (participation instanceof StudentParticipation) {
            if (!authCheckService.canAccessParticipation((StudentParticipation) participation)) {
                throw new AccessForbiddenException("participation", participationId);
            }
        }
        else if (participation instanceof ProgrammingExerciseParticipation) {
            if (!programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation)) {
                throw new AccessForbiddenException("participation", participationId);
            }
        }
        else {
            // This would be the case that a new participation type is introduced, without this the user would have access to it regardless of the permissions.
            throw new AccessForbiddenException("participation", participationId);
        }

        return new ResponseEntity<>(resultService.getFeedbacksForResult(result), HttpStatus.OK);
    }

    /**
     * DELETE /participations/:participationId/results/:resultId : delete the "id" result.
     *
     * @param participationId the id of the participation to the result
     * @param resultId the id of the result to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("participations/{participationId}/results/{resultId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> deleteResult(@PathVariable Long participationId, @PathVariable Long resultId) {
        log.debug("REST request to delete Result : {}", resultId);
        Result result = getResultForParticipationAndCheckAccess(participationId, resultId, Role.TEACHING_ASSISTANT);
        resultRepository.delete(result);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, resultId.toString())).build();
    }

    /**
     * POST exercises/:exerciseId/example-submissions/:submissionId/example-results : Creates a new example result for the provided example submission ID.
     *
     * @param exerciseId id of the exercise to the submission
     * @param exampleSubmissionId The example submission ID for which an example result should get created
     * @param isProgrammingExerciseWithFeedback Whether the related exercise is a programming exercise with feedback
     * @return The newly created result
     */
    @PostMapping("exercises/{exerciseId}/example-submissions/{exampleSubmissionId}/example-results")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Result> createExampleResult(@PathVariable long exerciseId, @PathVariable long exampleSubmissionId,
            @RequestParam(defaultValue = "false", required = false) boolean isProgrammingExerciseWithFeedback) {
        log.debug("REST request to create a new example result for submission: {}", exampleSubmissionId);
        ExampleSubmission exampleSubmission = exampleSubmissionRepository.findBySubmissionIdWithResultsElseThrow(exampleSubmissionId);
        if (!exampleSubmission.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("exerciseId of the path doesnt match the exerciseId of the exercise corresponding to the submission " + exampleSubmissionId + "!",
                    "Exercise", "400");
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exampleSubmission.getExercise(), null);
        final var result = resultService.createNewExampleResultForSubmissionWithExampleSubmission(exampleSubmissionId, isProgrammingExerciseWithFeedback);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    /**
     * POST exercises/:exerciseId/external-submission-results : Creates a new result for the provided exercise and student (a participation and an empty submission will also be created if they do not exist yet)
     *
     * @param exerciseId The exercise ID for which a result should get created
     * @param studentLogin The student login (username) for which a result should get created
     * @param result The result to be created
     * @return The newly created result
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("exercises/{exerciseId}/external-submission-results")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Result> createResultForExternalSubmission(@PathVariable Long exerciseId, @RequestParam String studentLogin, @RequestBody Result result)
            throws URISyntaxException {
        log.debug("REST request to create Result for External Submission for Exercise : {}", exerciseId);
        if (result.getParticipation() != null && result.getParticipation().getExercise() != null && !result.getParticipation().getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("exerciseId in RequestBody doesnt match exerciseId in path!", "Exercise", "400");
        }
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        if (!exercise.isExamExercise()) {
            if (exercise.getDueDate() == null || ZonedDateTime.now().isBefore(exercise.getDueDate())) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "externalSubmissionBeforeDueDate",
                        "External submissions are not supported before the exercise due date.")).build();
            }
        }
        else {
            Exam exam = exercise.getExerciseGroup().getExam();
            ZonedDateTime latestIndividualExamEndDate = examDateService.getLatestIndividualExamEndDate(exam);
            if (latestIndividualExamEndDate == null || ZonedDateTime.now().isBefore(latestIndividualExamEndDate)) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "externalSubmissionBeforeDueDate",
                        "External submissions are not supported before the end of the exam.")).build();
            }
        }

        if (exercise instanceof QuizExercise) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "externalSubmissionForQuizExercise",
                    "External submissions are not supported for Quiz exercises.")).build();
        }

        Optional<User> student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(studentLogin);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        if (student.isEmpty() || !authCheckService.isAtLeastStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), student.get())) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "studentNotFound", "The student could not be found in this course.")).build();
        }

        // Check if a result exists already for this exercise and student. If so, do nothing and just inform the instructor.
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyStateWithEagerResults(exercise, studentLogin);
        if (optionalParticipation.isPresent() && optionalParticipation.get().getResults() != null && !optionalParticipation.get().getResults().isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "result", "resultAlreadyExists", "A result already exists for this student in this exercise."))
                    .build();
        }

        // Create a participation and a submitted empty submission if they do not exist yet
        StudentParticipation participation = participationService.createParticipationWithEmptySubmissionIfNotExisting(exercise, student.get(), SubmissionType.EXTERNAL);
        Submission submission = participationRepository.findByIdWithLegalSubmissionsElseThrow(participation.getId()).findLatestSubmission().get();
        result.setParticipation(participation);
        result.setSubmission(submission);

        // Create a new manual result which can be rated or unrated depending on what was specified in the create form
        Result savedResult = resultService.createNewManualResult(result, exercise instanceof ProgrammingExercise, result.isRated());

        return ResponseEntity.created(new URI("/api/results/" + savedResult.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, savedResult.getId().toString())).body(savedResult);
    }
}

package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.dto.ResultWithPointsPerGradingCriterionDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
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

    private final ResultRepository resultRepository;

    private final ParticipationService participationService;

    private final ResultService resultService;

    private final ExamDateService examDateService;

    private final ExerciseRepository exerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final UserRepository userRepository;

    private final ParticipationRepository participationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public ResultResource(ResultRepository resultRepository, ParticipationService participationService, ResultService resultService, ExamDateService examDateService,
            ExerciseRepository exerciseRepository, AuthorizationCheckService authCheckService, ParticipationAuthorizationCheckService participationAuthCheckService,
            UserRepository userRepository, ParticipationRepository participationRepository, StudentParticipationRepository studentParticipationRepository) {
        this.resultRepository = resultRepository;
        this.participationService = participationService;
        this.resultService = resultService;
        this.examDateService = examDateService;
        this.exerciseRepository = exerciseRepository;
        this.authCheckService = authCheckService;
        this.participationAuthCheckService = participationAuthCheckService;
        this.userRepository = userRepository;
        this.participationRepository = participationRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * GET /exercises/:exerciseId/results-with-points-per-criterion : get the successful results for an exercise, ordered ascending by build completion date.
     * Also contains for each result the points the student achieved with manual feedback. Those points are grouped as sum for each grading criterion.
     *
     * @param exerciseId      of the exercise for which to retrieve the results.
     * @param withSubmissions defines if submissions are loaded from the database for the results.
     * @return the ResponseEntity with status 200 (OK) and the list of results with points in body.
     */
    @GetMapping("exercises/{exerciseId}/results-with-points-per-criterion")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<ResultWithPointsPerGradingCriterionDTO>> getResultsForExerciseWithPointsPerCriterion(@PathVariable Long exerciseId,
            @RequestParam(defaultValue = "true") boolean withSubmissions) {
        final Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        final List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessorFeedbacks(exerciseId, false);

        final Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        final List<Result> results = resultService.resultsForExercise(exercise, participations, withSubmissions);
        final List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = results.stream().map(result -> resultRepository.calculatePointsPerGradingCriterion(result, course))
                .toList();

        return ResponseEntity.ok().body(resultsWithPoints);
    }

    /**
     * GET /participations/:participationId/results/:resultId : get the "id" result.
     *
     * @param participationId the id of the participation to the result
     * @param resultId        the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}/results/{resultId}")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> getResult(@PathVariable Long participationId, @PathVariable Long resultId) {
        log.debug("REST request to get Result : {}", resultId);
        Result result = resultService.getResultForParticipationAndCheckAccess(participationId, resultId, Role.TEACHING_ASSISTANT);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * GET /participations/:participationId/results/:resultId/details : get the build result details from CI service for the "id" result.
     * This method is only invoked if the result actually includes details (e.g. feedback or build errors)
     *
     * @param participationId the id of the participation to the result
     * @param resultId        the id of the result to retrieve. If the participation related to the result is not a StudentParticipation or ProgrammingExerciseParticipation, the
     *                            endpoint will return forbidden!
     * @return the ResponseEntity with status 200 (OK) and with body the result, status 404 (Not Found) if the result does not exist or 403 (forbidden) if the user does not have
     *         permissions to access the participation.
     */
    @GetMapping("participations/{participationId}/results/{resultId}/details")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Feedback>> getResultDetails(@PathVariable Long participationId, @PathVariable Long resultId) {
        log.debug("REST request to get Result : {}", resultId);
        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(resultId);
        Participation participation = result.getParticipation();
        if (!participation.getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId of the path does not match the participationId of the participation corresponding to the result " + resultId + " !",
                    "participationId", "400");
        }

        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        return new ResponseEntity<>(resultService.filterFeedbackForClient(result), HttpStatus.OK);
    }

    /**
     * DELETE /participations/:participationId/results/:resultId : delete the "id" result.
     *
     * @param participationId the id of the participation to the result
     * @param resultId        the id of the result to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("participations/{participationId}/results/{resultId}")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> deleteResult(@PathVariable Long participationId, @PathVariable Long resultId) {
        log.debug("REST request to delete Result : {}", resultId);
        Result result = resultService.getResultForParticipationAndCheckAccess(participationId, resultId, Role.TEACHING_ASSISTANT);
        resultService.deleteResult(result, true);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, resultId.toString())).build();
    }

    /**
     * POST exercises/:exerciseId/external-submission-results : Creates a new result for the provided exercise and student (a participation and an empty submission will also be
     * created if they do not exist yet)
     *
     * @param exerciseId   The exercise ID for which a result should get created
     * @param studentLogin The student login (username) for which a result should get created
     * @param result       The result to be created
     * @return The newly created result
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("exercises/{exerciseId}/external-submission-results")
    @EnforceAtLeastInstructor
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
        Submission submission = participationRepository.findByIdWithLegalSubmissionsElseThrow(participation.getId()).findLatestSubmission().orElseThrow();
        result.setParticipation(participation);
        result.setSubmission(submission);

        // Create a new manual result which can be rated or unrated depending on what was specified in the create form
        Result savedResult = resultService.createNewManualResult(result, result.isRated());

        return ResponseEntity.created(new URI("/api/results/" + savedResult.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, savedResult.getId().toString())).body(savedResult);
    }
}

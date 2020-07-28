package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.SubmittedAnswer;
import de.tum.in.www1.artemis.exception.QuizSubmissionException;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing QuizSubmission.
 */
@RestController
@RequestMapping("/api")
public class QuizSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(QuizSubmissionResource.class);

    private static final String ENTITY_NAME = "quizSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final QuizExerciseService quizExerciseService;

    private final QuizSubmissionService quizSubmissionService;

    private final ParticipationService participationService;

    private final WebsocketMessagingService messagingService;

    private final AuthorizationCheckService authCheckService;

    private final ExamSubmissionService examSubmissionService;

    public QuizSubmissionResource(QuizExerciseService quizExerciseService, QuizSubmissionService quizSubmissionService, ParticipationService participationService,
            WebsocketMessagingService messagingService, UserService userService, AuthorizationCheckService authCheckService, ExamSubmissionService examSubmissionService) {
        this.quizExerciseService = quizExerciseService;
        this.quizSubmissionService = quizSubmissionService;
        this.participationService = participationService;
        this.messagingService = messagingService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.examSubmissionService = examSubmissionService;
    }

    /**
     * POST /exercises/:exerciseId/submissions/live : Submit a new quizSubmission for live mode.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @param principal      refers to the user who initiated the request
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/submissions/live")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSubmission> submitForLiveMode(@PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission, Principal principal) {
        log.debug("REST request to submit QuizSubmission for live mode : {}", quizSubmission);
        try {
            // we set the submitted flag to true on the server side
            quizSubmission.setSubmitted(true);
            QuizSubmission updatedQuizSubmission = quizSubmissionService.saveSubmissionForLiveMode(exerciseId, quizSubmission, principal.getName(), true);
            return ResponseEntity.ok(updatedQuizSubmission);
        }
        catch (QuizSubmissionException e) {
            log.warn("QuizSubmissionException :" + e.getMessage() + " for user " + principal.getName() + " in quiz " + exerciseId);
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizSubmissionError", e.getMessage())).body(null);
        }
    }

    /**
     * POST /exercises/:exerciseId/submissions/practice : Submit a new quizSubmission for practice mode.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/submissions/practice")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> submitForPractice(@PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for practice : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID.")).body(null);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(exerciseId);
        if (quizExercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, user)) {
            return ResponseEntity.status(403)
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "Forbidden", "You are not allowed to participate in this exercise.")).body(null);
        }

        if (!quizExercise.isEnded() || !quizExercise.isIsOpenForPractice()) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotOpenForPractice", "The exercise is not open for practice or hasn't ended yet."))
                    .body(null);
        }

        // the following method either reuses an existing participation or creates a new one
        StudentParticipation participation = participationService.startExercise(quizExercise, user, false);
        // we set the exercise again to prevent issues with lazy loaded quiz questions
        participation.setExercise(quizExercise);

        // update and save submission
        Result result = quizSubmissionService.submitForPractice(quizSubmission, quizExercise, participation);

        // remove some redundant or unnecessary data that is not needed on client side
        for (SubmittedAnswer answer : quizSubmission.getSubmittedAnswers()) {
            answer.getQuizQuestion().setQuizQuestionStatistic(null);
        }

        quizExercise.setQuizPointStatistic(null);
        quizExercise.setCourse(null);

        messagingService.broadcastNewResult(result.getParticipation(), result);
        // return result with quizSubmission, participation and quiz exercise (including the solution)
        return ResponseEntity.ok(result);
    }

    /**
     * POST /exercises/:exerciseId/submissions/preview : Submit a new quizSubmission for preview mode. Note that in this case, nothing will be saved in database.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 and body the result or the appropriate error code.
     */
    @PostMapping("exercises/{exerciseId}/submissions/preview")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> submitForPreview(@PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for preview : {}", quizSubmission);

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID.")).body(null);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(exerciseId);
        if (quizExercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        if (!quizExerciseService.userHasTAPermissions(quizExercise)) {
            return forbidden();
        }

        // update submission
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.calculateAndUpdateScores(quizExercise);

        // create Participation stub
        StudentParticipation participation = new StudentParticipation().exercise(quizExercise);

        // create result
        Result result = new Result().participation(participation).submission(quizSubmission);
        result.setRated(false);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        // calculate score and update result accordingly
        result.evaluateSubmission();

        return ResponseEntity.ok(result);
    }

    /**
     * PUT /exercises/:exerciseId/submissions/exam : Update a QuizSubmission for exam mode
     *
     * @param exerciseId        the id of the exercise for which to update the submission
     * @param quizSubmission    the quizSubmission to update
     * @return                  the ResponseEntity with status 200 and body the result or the appropriate error code.
     */
    @PutMapping("exercises/{exerciseId}/submissions/exam")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizSubmission> submitQuizForExam(@PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        long start = System.currentTimeMillis();
        log.debug("REST request to submit QuizSubmission for exam : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(exerciseId);
        if (quizExercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();

        // Apply further checks if it is an exam submission
        Optional<ResponseEntity<QuizSubmission>> examSubmissionAllowanceFailure = examSubmissionService.checkSubmissionAllowance(quizExercise, user);
        if (examSubmissionAllowanceFailure.isPresent()) {
            return examSubmissionAllowanceFailure.get();
        }

        // Prevent multiple submissions (currently only for exam submissions)
        quizSubmission = (QuizSubmission) examSubmissionService.preventMultipleSubmissions(quizExercise, quizSubmission, user);

        QuizSubmission updatedQuizSubmission = quizSubmissionService.saveSubmissionForExamMode(quizExercise, quizSubmission, user.getLogin());
        long end = System.currentTimeMillis();
        log.info("submitQuizForExam took " + (end - start) + "ms for exercise " + exerciseId + " and user " + user.getLogin());
        return ResponseEntity.ok(updatedQuizSubmission);
    }
}

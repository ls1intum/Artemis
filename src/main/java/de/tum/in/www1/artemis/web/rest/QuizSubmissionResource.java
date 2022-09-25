package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.SubmittedAnswer;
import de.tum.in.www1.artemis.exception.QuizSubmissionException;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.QuizSubmissionService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
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

    private final UserRepository userRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizSubmissionService quizSubmissionService;

    private final ParticipationService participationService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final WebsocketMessagingService messagingService;

    private final AuthorizationCheckService authCheckService;

    private final ExamSubmissionService examSubmissionService;

    public QuizSubmissionResource(QuizExerciseRepository quizExerciseRepository, QuizSubmissionService quizSubmissionService, ParticipationService participationService,
            WebsocketMessagingService messagingService, UserRepository userRepository, AuthorizationCheckService authCheckService, ExamSubmissionService examSubmissionService,
            StudentParticipationRepository studentParticipationRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizSubmissionService = quizSubmissionService;
        this.participationService = participationService;
        this.messagingService = messagingService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.examSubmissionService = examSubmissionService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * POST /exercises/:exerciseId/submissions/live : Submit a new quizSubmission for live mode.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/submissions/live")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<QuizSubmission> submitForLiveMode(@PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for live mode : {}", quizSubmission);
        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow();
        try {
            // we set the submitted flag to true on the server side
            quizSubmission.setSubmitted(true);
            QuizSubmission updatedQuizSubmission = quizSubmissionService.saveSubmissionForLiveMode(exerciseId, quizSubmission, userLogin, true);
            return ResponseEntity.ok(updatedQuizSubmission);
        }
        catch (QuizSubmissionException e) {
            log.warn("QuizSubmissionException: {} for user {} in quiz {}", e.getMessage(), userLogin, exerciseId);
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
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Result> submitForPractice(@PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for practice : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "idExists", "A new quizSubmission cannot already have an ID.")).body(null);
        }

        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, user)) {
            return ResponseEntity.status(403)
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "Forbidden", "You are not allowed to participate in this exercise.")).body(null);
        }

        // Note that exam quiz exercises do not have an end date, so we need to check in that order
        if (!Boolean.TRUE.equals(quizExercise.isIsOpenForPractice()) || !quizExercise.isQuizEnded()) {
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
        // The quizScheduler is usually responsible for updating the participation to FINISHED in the database. If quizzes where the student did not participate are used for
        // practice, the QuizScheduler does not update the participation, that's why we update it manually here
        participation.setInitializationState(InitializationState.FINISHED);
        studentParticipationRepository.saveAndFlush(participation);

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
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> submitForPreview(@PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for preview : {}", quizSubmission);

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "idExists", "A new quizSubmission cannot already have an ID.")).body(null);
        }

        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, quizExercise, null);

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
        result.evaluateQuizSubmission();

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
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<QuizSubmission> submitQuizForExam(@PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        long start = System.currentTimeMillis();
        log.debug("REST request to submit QuizSubmission for exam : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        // Apply further checks if it is an exam submission
        examSubmissionService.checkSubmissionAllowanceElseThrow(quizExercise, user);

        // Prevent multiple submissions (currently only for exam submissions)
        quizSubmission = (QuizSubmission) examSubmissionService.preventMultipleSubmissions(quizExercise, quizSubmission, user);

        QuizSubmission updatedQuizSubmission = quizSubmissionService.saveSubmissionForExamMode(quizExercise, quizSubmission, user);
        long end = System.currentTimeMillis();
        log.info("submitQuizForExam took {}ms for exercise {} and user {}", end - start, exerciseId, user.getLogin());
        return ResponseEntity.ok(updatedQuizSubmission);
    }
}

package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.security.Principal;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.QuizSubmissionService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
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

    private final QuizExerciseService quizExerciseService;

    private final QuizSubmissionService quizSubmissionService;

    private final ParticipationService participationService;

    private final WebsocketMessagingService messagingService;

    private final SimpMessageSendingOperations messagingTemplate;

    public QuizSubmissionResource(QuizExerciseService quizExerciseService, QuizSubmissionService quizSubmissionService, ParticipationService participationService,
            SimpMessageSendingOperations messagingTemplate, WebsocketMessagingService messagingService) {
        this.quizExerciseService = quizExerciseService;
        this.quizSubmissionService = quizSubmissionService;
        this.participationService = participationService;
        this.messagingTemplate = messagingTemplate;
        this.messagingService = messagingService;
    }

    /**
     * POST /exercises/:exerciseId/submissions/practice : Submit a new quizSubmission for practice mode.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/submissions/practice")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> submitForPractice(@PathVariable Long exerciseId, Principal principal, @RequestBody QuizSubmission quizSubmission) {
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

        if (!quizExerciseService.userIsAllowedToSeeExercise(quizExercise)) {
            return ResponseEntity.status(403)
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "Forbidden", "You are not allowed to participate in this exercise.")).body(null);
        }

        if (!quizExercise.isEnded() || !quizExercise.isIsOpenForPractice()) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotOpenForPractice", "The exercise is not open for practice or hasn't ended yet."))
                    .body(null);
        }

        StudentParticipation participation = participationService.startExercise(quizExercise, principal.getName());
        participation.setExercise(quizExercise);

        // update and save submission
        Result result = quizSubmissionService.submitForPractice(quizSubmission, quizExercise, participation);

        // remove some redundant or unnecessary data that is not needed on client side
        for (SubmittedAnswer answer : quizSubmission.getSubmittedAnswers()) {
            answer.getQuizQuestion().setQuizQuestionStatistic(null);
        }

        quizExercise.setQuizPointStatistic(null);
        quizExercise.setCourse(null);

        participation = participationService.findOneWithEagerResults(participation.getId());

        // TODO: document the following logic and potentially improve it, it looks weird
        if (participation.getResults().size() == 0) {
            // the user has not participated before
            participation.addResult(result);
            messagingTemplate.convertAndSendToUser(principal.getName(), "/topic/exercise/" + quizExercise.getId() + "/participation", participation);
        }
        else {
            messagingService.broadcastNewResult(result.getParticipation(), result);
        }
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
}

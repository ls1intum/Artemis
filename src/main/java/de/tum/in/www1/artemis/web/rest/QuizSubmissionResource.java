package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.QuizSubmissionService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.ZonedDateTime;

/**
 * REST controller for managing QuizSubmission.
 */
@RestController
@RequestMapping("/api")
public class QuizSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(QuizSubmissionResource.class);

    private static final String ENTITY_NAME = "quizSubmission";

    private final QuizExerciseService quizExerciseService;
    private final QuizSubmissionService quizSubmissionService;
    private final ParticipationService participationService;

    public QuizSubmissionResource(QuizExerciseService quizExerciseService,
                                  QuizSubmissionService quizSubmissionService,
                                  ParticipationService participationService) {
        this.quizExerciseService = quizExerciseService;
        this.quizSubmissionService = quizSubmissionService;
        this.participationService = participationService;
    }

    /**
     * POST  /courses/:courseId/exercises/:exerciseId/submissions/practice : Submit a new quizSubmission for practice mode.
     *
     * @param courseId       only included for API consistency, not actually used
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/courses/{courseId}/exercises/{exerciseId}/submissions/practice")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> submitForPractice(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for practice : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID.")).body(null);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(exerciseId);
        if (quizExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        if (!quizExerciseService.userIsAllowedToSeeExercise(quizExercise)) {
            return ResponseEntity.status(403).headers(HeaderUtil.createFailureAlert("submission", "Forbidden", "You are not allowed to participate in this exercise.")).body(null);
        }

        Participation participation = participationService.init(quizExercise, principal.getName());
        participation.setExercise(quizExercise);
        if (!quizExercise.isEnded() || !quizExercise.isIsOpenForPractice()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotOpenForPractice", "The exercise is not open for practice or hasn't ended yet.")).body(null);
        }

        // update and save submission
        Result result = quizSubmissionService.submitForPractice(quizSubmission, quizExercise, participation);

        // return quizSubmission
        quizSubmission.setSubmissionDate(result.getCompletionDate());
        return ResponseEntity.ok(result);
    }

    /**
     * POST  /courses/:courseId/exercises/:exerciseId/submissions/preview : Submit a new quizSubmission for preview mode.
     * Nothing will be saved in database.
     *
     * @param courseId       only included for API consistency, not actually used
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 and body the result or the appropriate error code.
     */
    @GetMapping("/courses/{courseId}/exercises/{exerciseId}/submissions/preview")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> submitForPreview(@PathVariable Long courseId, @PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for preview : {}", quizSubmission);

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID.")).body(null);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(exerciseId);
        if (quizExercise == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        if (!quizExerciseService.userHasTAPermissions(quizExercise)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // update submission
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.calculateAndUpdateScores(quizExercise);

        // create Participation stub
        Participation participation = new Participation().exercise(quizExercise);

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

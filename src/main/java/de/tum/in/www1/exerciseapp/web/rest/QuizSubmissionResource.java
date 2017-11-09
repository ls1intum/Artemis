package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.QuizExerciseRepository;
import de.tum.in.www1.exerciseapp.repository.QuizSubmissionRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing QuizSubmission.
 */
@RestController
@RequestMapping("/api")
public class QuizSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(QuizSubmissionResource.class);

    private static final String ENTITY_NAME = "quizSubmission";

    private final QuizSubmissionRepository quizSubmissionRepository;
    private final QuizExerciseRepository quizExerciseRepository;
    private final ResultRepository resultRepository;
    private final ParticipationService participationService;

    public QuizSubmissionResource(QuizSubmissionRepository quizSubmissionRepository, QuizExerciseRepository quizExerciseRepository, ResultRepository resultRepository, ParticipationService participationService) {
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.resultRepository = resultRepository;
        this.participationService = participationService;
    }

    /**
     * GET  /courses/{courseId}/exercises/{exerciseId}/submissions/my-latest : Get the latest quizSubmission for the given course.
     * This endpoint is used when a user starts or resumes a quiz exercise, so that they can get the latest submission for that quiz exercise.
     * If no submission exists yet, a participation, result, and submission are created so that the user can use PUT with the given submission id to submit.
     * TODO: As we decided to change the logic of when a result is created, it might be better to change this workflow as well.
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to init a participation
     * @param principal  the current user principal
     * @return the ResponseEntity with status 200 (OK) and the quizSubmission in body, or with status 400 (Bad Request) if the exercise doesn't exist
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @GetMapping("/courses/{courseId}/exercises/{exerciseId}/submissions/my-latest")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizSubmission> getLatestQuizSubmissionForExercise(@PathVariable Long courseId,
                                                                             @PathVariable Long exerciseId,
                                                                             Principal principal) throws URISyntaxException {
        log.debug("REST request to get QuizSubmission for QuizExercise: {}", exerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findOne(exerciseId);
        if (Optional.ofNullable(quizExercise).isPresent()) {
            // TODO: Valentin: check if user is allowed to take part in this exercise (is this necessary?)
            Participation participation = participationService.init(quizExercise, principal.getName());
            Result result = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participation.getId()).orElse(null);
            if (result == null) {
                // no result exists yet => create a new one
                QuizSubmission newSubmission = new QuizSubmission().submittedAnswers(new HashSet<>());
                newSubmission = quizSubmissionRepository.save(newSubmission);
                result = new Result().participation(participation).submission(newSubmission);
                result = resultRepository.save(result);
            }
            QuizSubmission submission = (QuizSubmission) result.getSubmission();
            submission.setSubmissionDate(result.getCompletionDate());
            return ResponseEntity.ok(submission);
        } else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID")).body(null);
        }
    }

    /**
     * POST  /quiz-submissions : Create a new quizSubmission.
     *
     * @param quizSubmission the quizSubmission to create
     * @return the ResponseEntity with status 201 (Created) and with body the new quizSubmission, or with status 400 (Bad Request) if the quizSubmission has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/quiz-submissions")
    @Timed
    public ResponseEntity<QuizSubmission> createQuizSubmission(@RequestBody QuizSubmission quizSubmission) throws URISyntaxException {
        log.debug("REST request to save QuizSubmission : {}", quizSubmission);
        return ResponseEntity.notFound().headers(HeaderUtil.createAlert("Unsupported Operation", "")).build();
        // TODO: Valentin: implement for starting practice quiz
//        if (quizSubmission.getId() != null) {
//            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID")).body(null);
//        }
//        QuizSubmission result = quizSubmissionRepository.save(quizSubmission);
//        return ResponseEntity.created(new URI("/api/quiz-submissions/" + result.getId()))
//            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
//            .body(result);
    }

    /**
     * PUT  /quiz-submissions : Updates an existing quizSubmission.
     *
     * @param quizSubmission the quizSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated quizSubmission,
     * or with status 400 (Bad Request) if the quizSubmission is not valid,
     * or with status 500 (Internal Server Error) if the quizSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/quiz-submissions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizSubmission> updateQuizSubmission(@RequestBody QuizSubmission quizSubmission) throws URISyntaxException {
        log.debug("REST request to update QuizSubmission : {}", quizSubmission);

        //TODO: Valentin: check if submission belongs to user, so that a malicious user can't change random submissions by guessing a submission id (Which would be easy to do because submission ids are sequential)

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        if (quizSubmission.getId() == null) {
            return createQuizSubmission(quizSubmission);
        }

        // save changes to submission
        quizSubmission = quizSubmissionRepository.save(quizSubmission);

        // update corresponding result
        Optional<Result> resultOptional = resultRepository.findDistinctBySubmissionId(quizSubmission.getId());
        if (resultOptional.isPresent()) {
            Result result = resultOptional.get();
            Exercise exercise = result.getParticipation().getExercise();
            // only update if exercise hasn't ended already
            if (exercise.getDueDate().isAfter(ZonedDateTime.now())) {
                // update completion date (which also functions as submission date for now)
                result.setCompletionDate(ZonedDateTime.now());
                resultRepository.save(result);
                // TODO Valentin: calculate score and update result accordingly (do this only when actual submission is made <- "Final Submission" or "Out of time")
                quizSubmission.setSubmissionDate(result.getCompletionDate());

                return ResponseEntity.ok()
                    .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, quizSubmission.getId().toString()))
                    .body(quizSubmission);
            } else {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseHasEnded", "The exercise for this submission has already ended.")).body(null);
            }
        } else {
            return ResponseEntity.status(500).headers(HeaderUtil.createFailureAlert("submission", "resultNotFound", "No result was found for the given submission")).body(null);
        }
    }

    /**
     * GET  /quiz-submissions : get all the quizSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of quizSubmissions in body
     */
    @GetMapping("/quiz-submissions")
    @Timed
    public List<QuizSubmission> getAllQuizSubmissions() {
        log.debug("REST request to get all QuizSubmissions");
        return quizSubmissionRepository.findAll();
    }

    /**
     * GET  /quiz-submissions/:id : get the "id" quizSubmission.
     *
     * @param id the id of the quizSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-submissions/{id}")
    @Timed
    public ResponseEntity<QuizSubmission> getQuizSubmission(@PathVariable Long id) {
        log.debug("REST request to get QuizSubmission : {}", id);
        QuizSubmission quizSubmission = quizSubmissionRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizSubmission));
    }

    /**
     * DELETE  /quiz-submissions/:id : delete the "id" quizSubmission.
     *
     * @param id the id of the quizSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-submissions/{id}")
    @Timed
    public ResponseEntity<Void> deleteQuizSubmission(@PathVariable Long id) {
        log.debug("REST request to delete QuizSubmission : {}", id);
        quizSubmissionRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}

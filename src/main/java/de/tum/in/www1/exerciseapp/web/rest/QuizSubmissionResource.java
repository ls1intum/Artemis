package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.config.Constants;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import de.tum.in.www1.exerciseapp.domain.enumeration.SubmissionType;
import de.tum.in.www1.exerciseapp.repository.*;
import de.tum.in.www1.exerciseapp.service.*;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * REST controller for managing QuizSubmission.
 */
@RestController
@RequestMapping("/api")
public class QuizSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(QuizSubmissionResource.class);

    private static final String ENTITY_NAME = "quizSubmission";

    private static ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

    static {
        threadPoolTaskScheduler.setPoolSize(5);
        threadPoolTaskScheduler.setThreadNamePrefix("QuizEndScheduler");
        threadPoolTaskScheduler.initialize();
    }

    private final QuizSubmissionRepository quizSubmissionRepository;
    private final QuizSubmissionService quizSubmissionService;
    private final QuizExerciseService quizExerciseService;
    private final ResultRepository resultRepository;
    private final ResultService resultService;
    private final ParticipationService participationService;
    private final UserService userService;
    private final StatisticService statisticService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final AuthorizationCheckService authCheckService;

    public QuizSubmissionResource(QuizSubmissionRepository quizSubmissionRepository,
                                  QuizSubmissionService quizSubmissionService,
                                  QuizExerciseService quizExerciseService,
                                  ResultRepository resultRepository,
                                  ResultService resultService,
                                  ParticipationService participationService,
                                  UserService userService,
                                  SimpMessageSendingOperations messagingTemplate,
                                  StatisticService statisticService,
                                  AuthorizationCheckService authCheckService) {
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.quizSubmissionService = quizSubmissionService;
        this.quizExerciseService = quizExerciseService;
        this.resultRepository = resultRepository;
        this.resultService = resultService;
        this.participationService = participationService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
        this.statisticService = statisticService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET  /courses/{courseId}/exercises/{exerciseId}/submissions/my-latest : Get the latest rated quizSubmission for the given course.
     * This endpoint is used when a user starts or resumes a quiz exercise, so that they can get the latest submission for that quiz exercise.
     * If no rated submission exists yet, a participation, rated result, and submission are created so that the user can use PUT with the given submission id to submit.
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to init a participation
     * @param principal  the current user principal
     * @return the ResponseEntity with status 200 (OK) and the quizSubmission in body, or with status 400 (Bad Request) if the exercise doesn't exist
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @GetMapping("/courses/{courseId}/exercises/{exerciseId}/submissions/my-latest")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizSubmission> getLatestQuizSubmissionForExercise(@PathVariable Long courseId,
                                                                             @PathVariable Long exerciseId,
                                                                             Principal principal) throws URISyntaxException {
        log.debug("REST request to get QuizSubmission for QuizExercise: {}", exerciseId);
        long start = System.currentTimeMillis();
        QuizExercise quizExercise = quizExerciseService.findOne(exerciseId);
        log.info("    loaded quiz exercise after {} ms", System.currentTimeMillis() - start);
        if (Optional.ofNullable(quizExercise).isPresent()) {
            User user = userService.getUserWithGroupsAndAuthorities();
            // check if user is allowed to take part in this exercise
            if (user.getGroups().contains(quizExercise.getCourse().getStudentGroupName())) {
                log.info("    checked permissions after {} ms", System.currentTimeMillis() - start);
                final Participation participation = participationService.init(quizExercise, principal.getName());
                log.info("    loaded participation after {} ms", System.currentTimeMillis() - start);
                Result result = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participation.getId(), true).orElse(null);
                log.info("    loaded result after {} ms", System.currentTimeMillis() - start);
                if (quizExercise.isSubmissionAllowed() && result == null) {
                    // no result exists yet => create a new one
                    QuizSubmission newSubmission = new QuizSubmission().submittedAnswers(new HashSet<>());
                    result = new Result().participation(participation).submission(newSubmission);
                    result.setRated(true);
                    final Result savedResult = resultRepository.save(result);
                    log.info("    saved result after {} ms", System.currentTimeMillis() - start);

                    // create timer to score this submission when exercise times out.
                    threadPoolTaskScheduler.schedule(() -> {
                        Participation currentParticipation = participationService.findOne(participation.getId());
                        if (currentParticipation.getInitializationState() == ParticipationState.INITIALIZED) {
                            submitSubmission(currentParticipation, null, savedResult);
                        }
                        // notify user about new result
                        messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", true);
                    }, new Date(System.currentTimeMillis() + (quizExercise.getRemainingTime() + Constants.QUIZ_AUTOMATIC_SUBMISSION_DELAY_IN_SECONDS) * 1000));
                }
                if (result != null) {
                    QuizSubmission submission = quizSubmissionRepository.findOne(result.getSubmission().getId());
                    log.info("    loaded submission after {} ms", System.currentTimeMillis() - start);
                    // get submission from cache, if it exists and submission is not submitted already
                    QuizSubmission cachedSubmission = null;
                    if (!submission.isSubmitted()) {
                        cachedSubmission = quizSubmissionService.getCachedSubmission(principal.getName(), submission.getId());
                        if (cachedSubmission != null) {
                            submission = cachedSubmission;
                        }
                    }

                    // remove scores from submission if quiz hasn't ended yet
                    if (submission.isSubmitted() && quizExercise.shouldFilterForStudents()) {
                        submission.removeScores();
                    }

                    // set submission date for response (only necessary if submission is not from cache)
                    if (cachedSubmission == null) {
                        submission.setSubmissionDate(result.getCompletionDate());
                    }

                    log.info("    filtered submission after {} ms", System.currentTimeMillis() - start);
                    // return submission
                    return ResponseEntity.ok(submission);
                } else {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "noSubmission", "The exercise is over and you haven't participated.")).body(null);
                }
            } else {
                return ResponseEntity.status(403).headers(HeaderUtil.createFailureAlert("submission", "Forbidden", "You are not part of the students group for this course")).body(null);
            }
        } else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID")).body(null);
        }
    }

    /**
     * POST  /courses/:courseId/exercises/:exerciseId/submissions/practice : Submit a new quizSubmission for practice mode.
     *
     * @param courseId       only included for API consistency, not actually used
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param principal      the current user principal
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 201 (Created) and with body the new quizSubmission, or with status 400 (Bad Request) if the quizSubmission has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/exercises/{exerciseId}/submissions/practice")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizSubmission> submitQuizSubmissionForPractice(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal, @RequestBody QuizSubmission quizSubmission) throws URISyntaxException {
        log.debug("REST request to submit QuizSubmission for practice : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID")).body(null);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(exerciseId);
        if (Optional.ofNullable(quizExercise).isPresent()) {
            User user = userService.getUserWithGroupsAndAuthorities();
            // check if user is allowed to take part in this exercise
            if (user.getGroups().contains(quizExercise.getCourse().getStudentGroupName())) {
                Participation participation = participationService.init(quizExercise, principal.getName());
                participation.setExercise(quizExercise);
                if (quizExercise.isEnded() && quizExercise.isIsOpenForPractice()) {
                    // update and save submission
                    quizSubmission.setSubmitted(true);
                    quizSubmission.setType(SubmissionType.MANUAL);
                    quizSubmission.calculateAndUpdateScores(quizExercise);

                    // create and save result
                    Result result = new Result().participation(participation).submission(quizSubmission);
                    result.setRated(false);
                    result.setCompletionDate(ZonedDateTime.now());
                    // calculate score and update result accordingly
                    result.evaluateSubmission();
                    // save result
                    resultRepository.save(result);
                    // replace proxy with submission, because of Lazy-fetching
                    result.setSubmission(quizSubmission);
                    // return quizSubmission
                    quizSubmission.setSubmissionDate(result.getCompletionDate());
                    return ResponseEntity.created(new URI("/api/quiz-submissions/" + quizSubmission.getId())).body(quizSubmission);
                } else {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotOpenForPractice", "The exercise is not open for practice or hasn't ended yet.")).body(null);
                }
            } else {
                return ResponseEntity.status(403).headers(HeaderUtil.createFailureAlert("submission", "Forbidden", "You are not part of the students group for this course")).body(null);
            }
        } else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID")).body(null);
        }
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
    @PostMapping("/courses/{courseId}/exercises/{exerciseId}/submissions/preview")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> getResultForSubmission(@PathVariable Long courseId, @PathVariable Long exerciseId, @RequestBody QuizSubmission quizSubmission) {
        log.debug("REST request to submit QuizSubmission for preview : {}", quizSubmission);

        if (quizSubmission.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizSubmission cannot already have an ID")).body(null);
        }

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(exerciseId);
        if (Optional.ofNullable(quizExercise).isPresent()) {
            Course course = quizExercise.getCourse();
            User user = userService.getUserWithGroupsAndAuthorities();
            if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
                !authCheckService.isInstructorInCourse(course, user) &&
                !authCheckService.isAdmin()) {
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
            result.setCompletionDate(ZonedDateTime.now());
            // calculate score and update result accordingly
            result.evaluateSubmission();

            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseNotFound", "No exercise was found for the given ID")).body(null);
        }
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
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizSubmission> updateQuizSubmission(@RequestBody QuizSubmission quizSubmission, Principal principal) throws URISyntaxException {
        log.debug("REST request to update QuizSubmission : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        if (quizSubmission.getId() == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "missingId", "The submission has no ID. Use GET /courses/:courseId/exercises/:exerciseId/submissions/my-latest to get a submission and use its Id to update it.")).body(null);
        }

        // update corresponding result
        Optional<Result> resultOptional = resultRepository.findDistinctBySubmissionId(quizSubmission.getId());
        if (resultOptional.isPresent()) {
            Result result = resultOptional.get();
            Participation participation = result.getParticipation();
            QuizExercise quizExercise = (QuizExercise) participation.getExercise();
            User user = participation.getStudent();
            // check if participation (and thus submission) actually belongs to the user who sent this message
            if (principal.getName().equals(user.getLogin())) {
                // only update if quizExercise hasn't ended and user hasn't made final submission yet
                if (quizExercise.isSubmissionAllowed() && participation.getInitializationState() == ParticipationState.INITIALIZED) {
                    // save changes to submission
                    quizSubmission = submitSubmission(participation, quizSubmission, result);
                    // send response
                    return ResponseEntity.ok()
                        .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, quizSubmission.getId().toString()))
                        .body(quizSubmission);
                } else {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("submission", "exerciseHasEnded", "The quiz has ended or you have already submitted your answers for this quiz.")).body(null);
                }
            } else {
                return ResponseEntity.status(403).headers(HeaderUtil.createFailureAlert("submission", "Forbidden", "The submission belongs to a different user.")).body(null);
            }
        } else {
            return ResponseEntity.status(500).headers(HeaderUtil.createFailureAlert("submission", "resultNotFound", "No result was found for the given submission")).body(null);
        }
    }

    /**
     * 1. Overwrite current submission with quizSubmission (if quizSubmission is not null and participation state is not FINISHED)
     * 2. Mark the submission as final (submitted), calculate the score and save the result.
     * 3. Notify socket subscriptions for new result in participation and changed submission
     *
     * @param participation  the participation object that the submission belongs to
     * @param quizSubmission (optional) the new submission to overwrite the existing one with
     * @param result the result object that the submission belongs to
     * @return The updated QuizSubmission (submitted is true; submissionDate and type are updated)
     */
    private QuizSubmission submitSubmission(Participation participation, QuizSubmission quizSubmission, Result result) {
        String username = participation.getStudent().getLogin();
        Long submissionId = result.getSubmission().getId();

        // determine submission type
        SubmissionType submissionType = SubmissionType.MANUAL;
        if (quizSubmission == null) {
            submissionType = SubmissionType.TIMEOUT;
            // get the most up to date submission
            quizSubmission = quizSubmissionService.getActiveQuizSubmissionAndRemoveFromCache(username, submissionId);
        } else {
            quizSubmissionService.removeCachedSubmission(username, submissionId);
        }
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(participation.getExercise().getId());

        // update participation state => no further rated submissions allowed
        participation.setInitializationState(ParticipationState.FINISHED);
        participation = participationService.save(participation);
        participation.setExercise(quizExercise);
        // update submission
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(submissionType);
        quizSubmission.calculateAndUpdateScores(quizExercise);
        quizSubmission = quizSubmissionRepository.save(quizSubmission);
        // update result
        result.setParticipation(participation);
        result.setSubmission(quizSubmission);
        result.setCompletionDate(ZonedDateTime.now());
        // calculate score and update result accordingly
        result.evaluateSubmission();
        // save result
        result = resultRepository.save(result);
        // replace proxy with submission, because of Lazy-fetching
        result.setSubmission(quizSubmission);


        // remove scores from submission if quiz hasn't ended yet
        if (quizSubmission.isSubmitted() && quizExercise.shouldFilterForStudents()) {
            quizSubmission.removeScores();
        }
        // set submission date for response
        quizSubmission.setSubmissionDate(result.getCompletionDate());
        // notify user about changed submission
        messagingTemplate.convertAndSend("/topic/quizSubmissions/" + quizSubmission.getId(),
            "{\"saved\": \"" + quizSubmission.getSubmissionDate().toString().substring(0, 23) + "\"}");
        return quizSubmission;
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

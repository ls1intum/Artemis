package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Question;
import de.tum.in.www1.artemis.domain.QuizExercise;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing QuizExercise.
 */
@RestController
@RequestMapping("/api")
public class QuizExerciseResource {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    private final QuizExerciseService quizExerciseService;
    private final CourseService courseService;
    private final StatisticService statisticService;
    private final AuthorizationCheckService authCheckService;
    private final QuizScheduleService quizScheduleService;

    public QuizExerciseResource(QuizExerciseService quizExerciseService,
                                CourseService courseService,
                                StatisticService statisticService,
                                AuthorizationCheckService authCheckService,
                                QuizScheduleService quizScheduleService) {
        this.quizExerciseService = quizExerciseService;
        this.courseService = courseService;
        this.statisticService = statisticService;
        this.authCheckService = authCheckService;
        this.quizScheduleService = quizScheduleService;
    }

    /**
     * POST  /quiz-exercises : Create a new quizExercise.
     *
     * @param quizExercise the quizExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new quizExercise, or with status 400 (Bad Request) if the quizExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/quiz-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> createQuizExercise(@RequestBody QuizExercise quizExercise) throws URISyntaxException {
        log.debug("REST request to save QuizExercise : {}", quizExercise);
        if (quizExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new quizExercise cannot already have an ID")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(quizExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this quiz exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        quizExercise.setMaxScore(quizExercise.getMaxTotalScore().doubleValue());
        QuizExercise result = quizExerciseService.save(quizExercise);
        quizScheduleService.scheduleQuizStart(result);

        return ResponseEntity.created(new URI("/api/quiz-exercises/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /quiz-exercises : Updates an existing quizExercise.
     *
     * @param quizExercise the quizExercise to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated quizExercise,
     * or with status 400 (Bad Request) if the quizExercise is not valid,
     * or with status 500 (Internal Server Error) if the quizExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/quiz-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> updateQuizExercise(@RequestBody QuizExercise quizExercise) throws URISyntaxException {
        log.debug("REST request to update QuizExercise : {}", quizExercise);
        if (quizExercise.getId() == null) {
            return createQuizExercise(quizExercise);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(quizExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this quiz exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        // check if quiz is has already started
        QuizExercise originalQuiz = quizExerciseService.findOne(quizExercise.getId());
        if (originalQuiz == null) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "quizExerciseNotFound", "The quiz exercise does not exist yet. Use POST to create a new quizExercise.")).build();

        }
        if (originalQuiz.isStarted()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "quizHasStarted", "The quiz has already started. Use the re-evaluate endpoint to make retroactive corrections.")).body(null);
        }

        quizExercise.reconnectJSONIgnoreAttributes();

        // reset Released-Flag in all statistics if they are released but the quiz hasn't ended yet
        if (!quizExercise.isStarted() || quizExercise.getRemainingTime() > 0) {
            quizExercise.getQuizPointStatistic().setReleased(false);
            for (Question question : quizExercise.getQuestions()) {
                if (question.getQuestionStatistic() != null) {
                    question.getQuestionStatistic().setReleased(false);
                }
            }
        }

        quizExercise.setMaxScore(quizExercise.getMaxTotalScore().doubleValue());
        QuizExercise result = quizExerciseService.save(quizExercise);
        quizScheduleService.scheduleQuizStart(result);

        // notify websocket channel of changes to the quiz exercise
        quizExerciseService.sendQuizExerciseToSubscribedClients(result);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, quizExercise.getId().toString()))
            .body(result);
    }

    /**
     * GET  /quiz-exercises : get all the quizExercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of quizExercises in body
     */
    @GetMapping("/quiz-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public List<QuizExercise> getAllQuizExercises() {
        log.debug("REST request to get all QuizExercises");
        return quizExerciseService.findAll();
    }

    /**
     * GET  /courses/:courseId/quiz-exercises : get all the exercises.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/quiz-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    @Transactional(readOnly = true)
    public List<QuizExercise> getQuizExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all QuizExercises for the course with id : {}", courseId);
        List<QuizExercise> result = quizExerciseService.findByCourseId(courseId);
        for (QuizExercise quizExercise : result) {
            quizExercise.setQuestions(null);
        }
        return result;
    }

    /**
     * GET  /quiz-exercises/:quizExerciseId : get the quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{quizExerciseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> getQuizExercise(@PathVariable Long quizExerciseId) {
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizExercise));
    }

    /**
     * GET  /quiz-exercises/:quizExerciseId/recalculate-statistics : recalculate all statistics in case something went wrong with them
     *
     * @param quizExerciseId the id of the quizExercise for which the statistics should be recalculated
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{quizExerciseId}/recalculate-statistics")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> recalculateStatistics(@PathVariable Long quizExerciseId) {
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        statisticService.recalculateStatistics(quizExercise);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizExercise));
    }

    /**
     * GET  /quiz-exercises/:id/for-student : get the "id" quizExercise.
     * (information filtered for students)
     *
     * @param id the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{id}/for-student")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> getQuizExerciseForStudent(@PathVariable Long id) {
        log.debug("REST request to get QuizExercise : {}", id);
        long start = System.currentTimeMillis();

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(id);
        if (quizExercise == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.debug("    checked permissions after {} ms", System.currentTimeMillis() - start);

        // filter out information depending on quiz state
        quizExercise.applyAppropriateFilterForStudents();

        // filter out the statistic information
        quizExercise.setQuizPointStatistic(null);

        log.debug("    filtered info after {} ms", System.currentTimeMillis() - start);

        return ResponseEntity.ok(quizExercise);
    }

    /**
     * POST /quiz-exercises/:id/:action : perform the specified action for the quiz now
     *
     * @param id     the id of the quiz exercise to start
     * @param action the action to perform on the quiz (allowed actions: "start-now", "set-visible", "open-for-practice")
     * @return the response entity with status 204 if quiz was started, appropriate error code otherwise
     */
    @PostMapping("/quiz-exercises/{id}/{action}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<String> performActionForQuizExercise(@PathVariable Long id, @PathVariable String action) {
        log.debug("REST request to immediately start QuizExercise : {}", id);

        // find quiz exercise
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(id);
        if (quizExercise == null) {
            return ResponseEntity.notFound().build();
        }

        // check permissions
        Course course = quizExercise.getCourse();
        if (!courseService.userHasAtLeastTAPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        switch (action) {
            case "start-now":
                // check if quiz hasn't already started
                if (quizExercise.isStarted()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\": \"Quiz has already started.\"}");
                }

                // set release date to now
                quizExercise.setReleaseDate(ZonedDateTime.now());
                quizExercise.setIsPlannedToStart(true);
                break;
            case "set-visible":
                // check if quiz is already visible
                if (quizExercise.isVisibleToStudents()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\": \"Quiz is already visible to students.\"}");
                }

                // set quiz to visible
                quizExercise.setIsVisibleBeforeStart(true);
                break;
            case "open-for-practice":
                // check if quiz has ended
                if (!quizExercise.isStarted() || quizExercise.getRemainingTime() > 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\": \"Quiz hasn't ended yet.\"}");
                }
                // check if quiz is already open for practice
                if (quizExercise.isIsOpenForPractice()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\": \"Quiz is already open for practice.\"}");
                }

                // set quiz to open for practice
                quizExercise.setIsOpenForPractice(true);
                break;
            case "release-statistics":
                // release statistics
                if (quizExercise.isEnded()) {
                    quizExercise.getQuizPointStatistic().setReleased(true);
                    for ( Question question : quizExercise.getQuestions()){
                        question.getQuestionStatistic().setReleased(true);
                    }
                    //notify clients via websocket about the release state of the statistics.
                    statisticService.releaseStatistic(quizExercise, quizExercise.getQuizPointStatistic().isReleased());
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\": \"Quiz hasn't ended yet.\"}");
                }
                break;
            case "revoke-statistics":
                // revoke statistics
                quizExercise.getQuizPointStatistic().setReleased(false);
                for (Question question : quizExercise.getQuestions()) {
                    question.getQuestionStatistic().setReleased(false);
                }
                //notify clients via websocket about the release state of the statistics.
                statisticService.releaseStatistic(quizExercise, quizExercise.getQuizPointStatistic().isReleased());
                break;
            default:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\": \"Unknown action: " + action + "\"}");
        }

        // save quiz exercise
        quizExercise = quizExerciseService.save(quizExercise);
        quizScheduleService.scheduleQuizStart(quizExercise);

        // notify websocket channel of changes to the quiz exercise
        quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise);

        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE  /quiz-exercises/:id : delete the "id" quizExercise.
     *
     * @param id the id of the quizExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-exercises/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long id) {
        log.debug("REST request to delete QuizExercise : {}", id);

        QuizExercise quizExercise = quizExerciseService.findOne(id);
        if (quizExercise == null) {
            return ResponseEntity.notFound().build();
        }

        Course course = quizExercise.getCourse();
        if (!courseService.userHasAtLeastInstructorPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        quizExerciseService.delete(id);
        quizScheduleService.cancelScheduledQuizStart(id);
        quizScheduleService.clearQuizData(id);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }

    /**
     * PUT  /quiz-exercises-re-evaluate : Re-evaluates an existing quizExercise.
     *
     * 1. reset not allowed changes and set flag updateResultsAndStatistics if a recalculation of results and statistics is necessary
     * 2. save changed quizExercise
     * 3. if flag is set: -> change results if an answer or a question is set invalid
     *                    -> recalculate statistics and results and save them.
     *
     * @param quizExercise the quizExercise to re-evaluate
     * @return the ResponseEntity with status 200 (OK) and with body the re-evaluated quizExercise,
     * or with status 400 (Bad Request) if the quizExercise is not valid,
     * or with status 500 (Internal Server Error) if the quizExercise couldn't be re-evaluated
     */
    @PutMapping("/quiz-exercises-re-evaluate")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> reEvaluateQuizExercise(@RequestBody QuizExercise quizExercise) {
        log.debug("REST request to re-evaluate QuizExercise : {}", quizExercise);
        if (quizExercise.getId() == null) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "quizExerciseWithoutId", "The quiz exercise doesn't have an ID.")).build();
        }
        QuizExercise originalQuizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId());
        if (originalQuizExercise == null) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "quizExerciseNotFound", "The quiz exercise does not exist yet. Use POST to create a new quizExercise.")).build();
        }
        if (!originalQuizExercise.isEnded()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "quizExerciseNotEnded", "The quiz exercise has not ended yet. Re-evaluation is only allowed after a quiz has ended.")).build();
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(quizExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this quiz exercise does not exist")).body(null);
        }
        if (!courseService.userHasAtLeastInstructorPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        quizExercise.undoUnallowedChanges(originalQuizExercise);
        boolean updateOfResultsAndStatisticsNecessary = quizExercise.checkIfRecalculationIsNecessary(originalQuizExercise);

        //update QuizExercise
        quizExercise.reconnectJSONIgnoreAttributes();

        //adjust existing results if an answer or and question was deleted and recalculate them
        quizExerciseService.adjustResultsOnQuizChanges(quizExercise);

        QuizExercise result = quizExerciseService.saveWithNoNewEntities(quizExercise);

        if (updateOfResultsAndStatisticsNecessary) {
            // update Statistics
            statisticService.recalculateStatistics(quizExercise);
        }


        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, quizExercise.getId().toString()))
            .body(result);
    }

}

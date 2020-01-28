package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/** REST controller for managing QuizExercise. */
@RestController
@RequestMapping("/api")
public class QuizExerciseResource {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizExerciseService quizExerciseService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserService userService;

    private final CourseService courseService;

    private final ExerciseService exerciseService;

    private final QuizStatisticService quizStatisticService;

    private final AuthorizationCheckService authCheckService;

    private final QuizScheduleService quizScheduleService;

    private final GroupNotificationService groupNotificationService;

    public QuizExerciseResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, CourseService courseService,
            QuizStatisticService quizStatisticService, AuthorizationCheckService authCheckService, GroupNotificationService groupNotificationService,
            QuizScheduleService quizScheduleService, ExerciseService exerciseService, UserService userService) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.quizStatisticService = quizStatisticService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
        this.quizScheduleService = quizScheduleService;
        this.exerciseService = exerciseService;
    }

    /**
     * POST /quiz-exercises : Create a new quizExercise.
     *
     * @param quizExercise the quizExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new quizExercise, or with status 400 (Bad Request) if the quizExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/quiz-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizExercise> createQuizExercise(@RequestBody QuizExercise quizExercise) throws URISyntaxException {
        log.debug("REST request to save QuizExercise : {}", quizExercise);
        if (quizExercise.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "idexists", "A new quizExercise cannot already have an ID")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(quizExercise.getCourse().getId());
        if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see below in update Quiz)
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        quizExercise.setMaxScore(quizExercise.getMaxTotalScore().doubleValue());
        quizExercise = quizExerciseService.save(quizExercise);
        quizScheduleService.scheduleQuizStart(quizExercise);

        groupNotificationService.notifyTutorGroupAboutExerciseCreated(quizExercise);

        return ResponseEntity.created(new URI("/api/quiz-exercises/" + quizExercise.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, quizExercise.getId().toString())).body(quizExercise);
    }

    /**
     * PUT /quiz-exercises : Updates an existing quizExercise.
     *
     * @param quizExercise the quizExercise to update
     * @param notificationText about the quiz exercise update that should be displayed to the student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid, or with status 500
     *         (Internal Server Error) if the quizExercise couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/quiz-exercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizExercise> updateQuizExercise(@RequestBody QuizExercise quizExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update QuizExercise : {}", quizExercise);
        if (quizExercise.getId() == null) {
            return createQuizExercise(quizExercise);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(quizExercise.getCourse().getId());
        if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        // check if quiz is has already started
        Optional<QuizExercise> originalQuiz = quizExerciseService.findById(quizExercise.getId());
        if (originalQuiz.isEmpty()) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizExerciseNotFound",
                    "The quiz exercise does not exist yet. Use POST to create a new quizExercise.")).build();
        }
        if (originalQuiz.get().isStarted()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizHasStarted",
                    "The quiz has already started. Use the re-evaluate endpoint to make retroactive corrections.")).body(null);
        }

        quizExercise.reconnectJSONIgnoreAttributes();

        quizExercise.setMaxScore(quizExercise.getMaxTotalScore().doubleValue());
        quizExercise = quizExerciseService.save(quizExercise);
        quizScheduleService.scheduleQuizStart(quizExercise);

        // notify websocket channel of changes to the quiz exercise
        quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise);

        // NOTE: it does not make sense to notify students here!
        if (notificationText != null) {
            groupNotificationService.notifyStudentGroupAboutExerciseUpdate(quizExercise, notificationText);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, quizExercise.getId().toString())).body(quizExercise);
    }

    /**
     * GET /courses/:courseId/quiz-exercises : get all the exercises.
     *
     * @param courseId id of the course of which all exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(value = "/courses/{courseId}/quiz-exercises")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public List<QuizExercise> getQuizExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all QuizExercises for the course with id : {}", courseId);
        List<QuizExercise> result = quizExerciseService.findByCourseId(courseId);

        for (QuizExercise quizExercise : result) {
            quizExercise.setQuizQuestions(null);
            // not required in the returned json body
            quizExercise.setStudentParticipations(null);
            quizExercise.setCourse(null);
        }
        return result;
    }

    /**
     * GET /quiz-exercises/:quizExerciseId : get the quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{quizExerciseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizExercise> getQuizExercise(@PathVariable Long quizExerciseId) {
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return forbidden();
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizExercise));
    }

    /**
     * GET /quiz-exercises/:quizExerciseId/recalculate-statistics : recalculate all statistics in case something went wrong with them
     *
     * @param quizExerciseId the id of the quizExercise for which the statistics should be recalculated
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{quizExerciseId}/recalculate-statistics")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizExercise> recalculateStatistics(@PathVariable Long quizExerciseId) {
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return forbidden();
        }
        quizStatisticService.recalculateStatistics(quizExercise);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizExercise));
    }

    /**
     * GET /quiz-exercises/:quizExerciseId/for-student : get the "id" quizExercise. (information filtered for students)
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{quizExerciseId}/for-student")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizExercise> getQuizExerciseForStudent(@PathVariable Long quizExerciseId) {
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);

        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(quizExerciseId);
        if (quizExercise == null) {
            return notFound();
        }
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return forbidden();
        }
        quizExercise.applyAppropriateFilterForStudents();

        // filter out information depending on quiz state
        quizExercise.filterForStudentsDuringQuiz();
        return ResponseEntity.ok(quizExercise);
    }

    /**
     * POST /quiz-exercises/:quizExerciseId/:action : perform the specified action for the quiz now
     *
     * @param quizExerciseId     the id of the quiz exercise to start
     * @param action the action to perform on the quiz (allowed actions: "start-now", "set-visible", "open-for-practice")
     * @return the response entity with status 200 if quiz was started, appropriate error code otherwise
     */
    @PutMapping("/quiz-exercises/{quizExerciseId}/{action}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizExercise> performActionForQuizExercise(@PathVariable Long quizExerciseId, @PathVariable String action) {
        log.debug("REST request to immediately start QuizExercise : {}", quizExerciseId);

        // find quiz exercise
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(quizExerciseId);
        if (quizExercise == null) {
            return ResponseEntity.notFound().build();
        }

        // check permissions
        Course course = quizExercise.getCourse();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            return forbidden();
        }

        switch (action) {
        case "start-now":
            // check if quiz hasn't already started
            if (quizExercise.isStarted()) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyStarted", "Quiz has already started."))
                        .build();
            }

            // set release date to now
            quizExercise.setReleaseDate(ZonedDateTime.now());
            quizExercise.setIsPlannedToStart(true);
            groupNotificationService.notifyStudentGroupAboutExerciseStart(quizExercise);
            break;
        case "set-visible":
            // check if quiz is already visible
            if (quizExercise.isVisibleToStudents()) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyVisible", "Quiz is already visible to students.")).build();
            }

            // set quiz to visible
            quizExercise.setIsVisibleBeforeStart(true);
            break;
        case "open-for-practice":
            // check if quiz has ended
            if (!quizExercise.isStarted() || quizExercise.getRemainingTime() > 0) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizNotEndedYet", "Quiz hasn't ended yet."))
                        .build();
            }
            // check if quiz is already open for practice
            if (quizExercise.isIsOpenForPractice()) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyOpenForPractice", "Quiz is already open for practice.")).build();
            }

            // set quiz to open for practice
            quizExercise.setIsOpenForPractice(true);
            groupNotificationService.notifyStudentGroupAboutExercisePractice(quizExercise);
            break;
        default:
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "unknownAction", "Unknown action: " + action)).build();
        }

        // save quiz exercise
        quizExercise = quizExerciseRepository.save(quizExercise);
        quizScheduleService.scheduleQuizStart(quizExercise);

        // notify websocket channel of changes to the quiz exercise
        quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise);
        return new ResponseEntity<>(quizExercise, HttpStatus.OK);
    }

    /**
     * DELETE /quiz-exercises/:quizExerciseId : delete the "id" quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-exercises/{quizExerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long quizExerciseId) {
        log.info("REST request to delete QuizExercise : {}", quizExerciseId);
        Optional<QuizExercise> quizExercise = quizExerciseService.findById(quizExerciseId);
        if (quizExercise.isEmpty()) {
            return notFound();
        }
        Course course = quizExercise.get().getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(quizExercise.get(), course, user);
        exerciseService.delete(quizExerciseId, false, false);
        quizScheduleService.cancelScheduledQuizStart(quizExerciseId);
        quizScheduleService.clearQuizData(quizExerciseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, quizExercise.get().getTitle())).build();
    }

    /**
     * PUT /quiz-exercises-re-evaluate : Re-evaluates an existing quizExercise.
     * <p>
     * 1. reset not allowed changes and set flag updateResultsAndStatistics if a recalculation of results and statistics is necessary 2. save changed quizExercise 3. if flag is
     * set: -> change results if an answer or a question is set invalid -> recalculate statistics and results and save them.
     *
     * @param quizExercise the quizExercise to re-evaluate
     * @return the ResponseEntity with status 200 (OK) and with body the re-evaluated quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid, or with
     *         status 500 (Internal Server Error) if the quizExercise couldn't be re-evaluated
     */
    @PutMapping("/quiz-exercises-re-evaluate")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizExercise> reEvaluateQuizExercise(@RequestBody QuizExercise quizExercise) {
        log.debug("REST request to re-evaluate QuizExercise : {}", quizExercise);
        if (quizExercise.getId() == null) {
            return ResponseEntity.notFound()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizExerciseWithoutId", "The quiz exercise doesn't have an ID.")).build();
        }
        QuizExercise originalQuizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId());
        if (originalQuizExercise == null) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizExerciseNotFound",
                    "The quiz exercise does not exist yet. Use POST to create a new quizExercise.")).build();
        }
        if (!originalQuizExercise.isEnded()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizExerciseNotEnded",
                    "The quiz exercise has not ended yet. Re-evaluation is only allowed after a quiz has ended.")).build();
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(quizExercise.getCourse().getId());
        if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }

        quizExercise.undoUnallowedChanges(originalQuizExercise);
        boolean updateOfResultsAndStatisticsNecessary = quizExercise.checkIfRecalculationIsNecessary(originalQuizExercise);

        // update QuizExercise
        quizExercise.setMaxScore(quizExercise.getMaxTotalScore().doubleValue());
        quizExercise.reconnectJSONIgnoreAttributes();

        // adjust existing results if an answer or and question was deleted and recalculate them
        quizExerciseService.adjustResultsOnQuizChanges(quizExercise);

        quizExercise = quizExerciseService.save(quizExercise);

        if (updateOfResultsAndStatisticsNecessary) {
            // update Statistics
            quizStatisticService.recalculateStatistics(quizExercise);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, quizExercise.getId().toString())).body(quizExercise);
    }
}

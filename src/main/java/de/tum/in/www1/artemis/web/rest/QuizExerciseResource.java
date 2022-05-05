package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/** REST controller for managing QuizExercise. */
@RestController
@RequestMapping("/api")
public class QuizExerciseResource {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizExerciseService quizExerciseService;

    private final QuizMessagingService quizMessagingService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final CourseRepository courseRepository;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ExamDateService examDateService;

    private final QuizScheduleService quizScheduleService;

    private final QuizStatisticService quizStatisticService;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    private final StudentParticipationRepository studentParticipationRepository;

    public QuizExerciseResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, CourseService courseService, UserRepository userRepository,
            ExerciseDeletionService exerciseDeletionServiceService, QuizScheduleService quizScheduleService, QuizStatisticService quizStatisticService,
            AuthorizationCheckService authCheckService, CourseRepository courseRepository, GroupNotificationService groupNotificationService, ExerciseService exerciseService,
            ExamDateService examDateService, QuizMessagingService quizMessagingService, StudentParticipationRepository studentParticipationRepository) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.exerciseDeletionService = exerciseDeletionServiceService;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.quizScheduleService = quizScheduleService;
        this.quizStatisticService = quizStatisticService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseService = exerciseService;
        this.examDateService = examDateService;
        this.courseRepository = courseRepository;
        this.quizMessagingService = quizMessagingService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * POST /quiz-exercises : Create a new quizExercise.
     *
     * @param quizExercise the quizExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new quizExercise, or with status 400 (Bad Request) if the quizExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/quiz-exercises")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<QuizExercise> createQuizExercise(@RequestBody QuizExercise quizExercise) throws URISyntaxException {
        log.debug("REST request to create QuizExercise : {}", quizExercise);
        if (quizExercise.getId() != null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "idexists", "A new quizExercise cannot already have an ID")).body(null);
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see below in update Quiz)
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        quizExercise.validateScoreSettings();
        // Valid exercises have set either a course or an exerciseGroup
        quizExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(quizExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        quizExercise = quizExerciseService.save(quizExercise);

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
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<QuizExercise> updateQuizExercise(@RequestBody QuizExercise quizExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) throws URISyntaxException {
        log.debug("REST request to update QuizExercise : {}", quizExercise);
        if (quizExercise.getId() == null) {
            return createQuizExercise(quizExercise);
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see above in create Quiz)
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        quizExercise.validateScoreSettings();

        // Valid exercises have set either a course or an exerciseGroup
        quizExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(quizExercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        // Forbid conversion between normal course exercise and exam exercise
        final var originalQuiz = quizExerciseRepository.findByIdElseThrow(quizExercise.getId());
        exerciseService.checkForConversionBetweenExamAndCourseExercise(quizExercise, originalQuiz, ENTITY_NAME);

        // check if quiz is has already started

        if (originalQuiz.isStarted()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizHasStarted",
                    "The quiz has already started. Use the re-evaluate endpoint to make retroactive corrections.")).body(null);
        }

        quizExercise.reconnectJSONIgnoreAttributes();

        quizExercise = quizExerciseService.save(quizExercise);
        exerciseService.logUpdate(quizExercise, quizExercise.getCourseViaExerciseGroupOrCourseMember(), user);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, quizExercise.getId().toString())).body(quizExercise);
    }

    /**
     * GET /courses/:courseId/quiz-exercises : get all the exercises.
     *
     * @param courseId id of the course of which all exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of quiz exercises in body
     */
    @GetMapping(value = "/courses/{courseId}/quiz-exercises")
    @PreAuthorize("hasRole('TA')")
    public List<QuizExercise> getQuizExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all QuizExercises for the course with id : {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            throw new AccessForbiddenException();
        }
        var quizExercises = quizExerciseRepository.findByCourseIdWithCategories(courseId);

        for (QuizExercise quizExercise : quizExercises) {
            quizExercise.setQuizQuestions(null);
            // not required in the returned json body
            quizExercise.setStudentParticipations(null);
            quizExercise.setCourse(null);
        }
        return quizExercises;
    }

    /**
     * GET /:examId/quiz-exercises : get all the quiz exercises of an exam.
     *
     * @param examId id of the exam of which all exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of quiz exercises in body
     */
    @GetMapping("/{examId}/quiz-exercises")
    @PreAuthorize("hasRole('EDITOR')")
    public List<QuizExercise> getQuizExercisesForExam(@PathVariable Long examId) {
        List<QuizExercise> quizExercises = quizExerciseRepository.findByExamId(examId);
        Course course = quizExercises.get(0).getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        for (QuizExercise quizExercise : quizExercises) {
            quizExercise.setQuizQuestions(null);
            // not required in the returned json body
            quizExercise.setStudentParticipations(null);
            quizExercise.setCourse(null);
            quizExercise.setExerciseGroup(null);
        }
        return quizExercises;
    }

    /**
     * GET /quiz-exercises/:quizExerciseId : get the quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{quizExerciseId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<QuizExercise> getQuizExercise(@PathVariable Long quizExerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        if (quizExercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, quizExercise, null);
            studentParticipationRepository.checkTestRunsExist(quizExercise);
        }
        else if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            throw new AccessForbiddenException();
        }
        return ResponseEntity.ok(quizExercise);
    }

    /**
     * GET /quiz-exercises/:quizExerciseId/recalculate-statistics : recalculate all statistics in case something went wrong with them
     *
     * @param quizExerciseId the id of the quizExercise for which the statistics should be recalculated
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{quizExerciseId}/recalculate-statistics")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<QuizExercise> recalculateStatistics(@PathVariable Long quizExerciseId) {
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            throw new AccessForbiddenException();
        }
        quizStatisticService.recalculateStatistics(quizExercise);
        // fetch the quiz exercise again to make sure the latest changes are included
        return ResponseEntity.ok(quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId()));
    }

    /**
     * GET /quiz-exercises/:quizExerciseId/for-student : get the "id" quizExercise. (information filtered for students)
     *
     * @param quizExerciseId the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{quizExerciseId}/for-student")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<QuizExercise> getQuizExerciseForStudent(@PathVariable Long quizExerciseId) {
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExerciseId);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            throw new AccessForbiddenException();
        }
        quizExercise.applyAppropriateFilterForStudents();

        // filter out information depending on quiz state
        quizExercise.filterForStudentsDuringQuiz();
        return ResponseEntity.ok(quizExercise);
    }

    /**
     * PUT /quiz-exercises/:quizExerciseId/:action : perform the specified action for the quiz now
     *
     * @param quizExerciseId     the id of the quiz exercise to start
     * @param action the action to perform on the quiz (allowed actions: "start-now", "set-visible", "open-for-practice")
     * @return the response entity with status 200 if quiz was started, appropriate error code otherwise
     */
    @PutMapping("/quiz-exercises/{quizExerciseId}/{action}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<QuizExercise> performActionForQuizExercise(@PathVariable Long quizExerciseId, @PathVariable String action) {
        log.debug("REST request to immediately start QuizExercise : {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, quizExercise, null);

        switch (action) {
            case "start-now" -> {
                // check if quiz hasn't already started
                if (quizExercise.isStarted()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyStarted", "Quiz has already started.")).build();
                }

                // set release date to now, truncated to seconds because the database only stores seconds
                quizExercise.setReleaseDate(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                quizExercise.setIsPlannedToStart(true);
            }
            case "set-visible" -> {
                // check if quiz is already visible
                if (quizExercise.isVisibleToStudents()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyVisible", "Quiz is already visible to students.")).build();
                }

                // set quiz to visible
                quizExercise.setIsVisibleBeforeStart(true);
            }
            case "open-for-practice" -> {
                // check if quiz has ended
                if (!quizExercise.isStarted() || quizExercise.getRemainingTime() > 0) {
                    return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizNotEndedYet", "Quiz hasn't ended yet."))
                            .build();
                }
                // check if quiz is already open for practice
                if (quizExercise.isIsOpenForPractice()) {
                    return ResponseEntity.badRequest()
                            .headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizAlreadyOpenForPractice", "Quiz is already open for practice."))
                            .build();
                }

                // set quiz to open for practice
                quizExercise.setIsOpenForPractice(true);
                groupNotificationService.notifyStudentGroupAboutExercisePractice(quizExercise);
            }
            default -> {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "unknownAction", "Unknown action: " + action))
                        .build();
            }
        }

        // save quiz exercise
        quizExercise = quizExerciseRepository.saveAndFlush(quizExercise);
        // reload the quiz exercise with questions and statistics to prevent problems with proxy objects
        quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        quizScheduleService.updateQuizExercise(quizExercise);

        // notify websocket channel of changes to the quiz exercise
        quizMessagingService.sendQuizExerciseToSubscribedClients(quizExercise, action);
        return new ResponseEntity<>(quizExercise, HttpStatus.OK);
    }

    /**
     * DELETE /quiz-exercises/:quizExerciseId : delete the "id" quizExercise.
     *
     * @param quizExerciseId the id of the quizExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-exercises/{quizExerciseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long quizExerciseId) {
        log.info("REST request to delete QuizExercise : {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdElseThrow(quizExerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, quizExercise, user);

        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(quizExercise, quizExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(quizExerciseId, false, false);
        quizExerciseService.cancelScheduledQuiz(quizExerciseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, quizExercise.getTitle())).build();
    }

    /**
     * PUT /quiz-exercises/:quizExerciseId/re-evaluate : Re-evaluates an existing quizExercise.
     *
     * 1. reset not allowed changes and set flag updateResultsAndStatistics if a recalculation of results and statistics is necessary
     * 2. save changed quizExercise
     * 3. if flag is set: -> change results if an answer or a question is set invalid -> recalculate statistics and results and save them.
     *
     * @param quizExerciseId the quiz id for the quiz that should be re-evaluated
     * @param quizExercise the quizExercise to re-evaluate
     * @return the ResponseEntity with status 200 (OK) and with body the re-evaluated quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid, or with
     *         status 500 (Internal Server Error) if the quizExercise couldn't be re-evaluated
     */
    @PutMapping("/quiz-exercises/{quizExerciseId}/re-evaluate")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<QuizExercise> reEvaluateQuizExercise(@PathVariable Long quizExerciseId, @RequestBody QuizExercise quizExercise) {
        log.debug("REST request to re-evaluate QuizExercise : {}", quizExercise);
        QuizExercise originalQuizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);

        if (originalQuizExercise.isExamExercise()) {
            // Re-evaluation of an exam quiz is only possible if all students finished their exam
            ZonedDateTime latestIndividualExamEndDate = examDateService.getLatestIndividualExamEndDate(originalQuizExercise.getExerciseGroup().getExam());
            if (latestIndividualExamEndDate == null || latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "examOfQuizExerciseNotEnded",
                        "The exam of the quiz exercise has not ended yet. Re-evaluation is only allowed after an exam has ended.")).build();
            }
        }
        else if (!originalQuizExercise.isEnded()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizExerciseNotEnded",
                    "The quiz exercise has not ended yet. Re-evaluation is only allowed after a quiz has ended.")).build();
        }

        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, quizExercise, user);

        quizExercise = quizExerciseService.reEvaluate(quizExercise, originalQuizExercise);
        exerciseService.logUpdate(quizExercise, quizExercise.getCourseViaExerciseGroupOrCourseMember(), user);

        quizExercise.validateScoreSettings();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, quizExercise.getId().toString())).body(quizExercise);
    }
}

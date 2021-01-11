package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.scheduled.quiz.QuizScheduleService;
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

    private final ExamService examService;

    private final QuizScheduleService quizScheduleService;

    private final QuizStatisticService quizStatisticService;

    private final AuthorizationCheckService authCheckService;

    private final GroupNotificationService groupNotificationService;

    public QuizExerciseResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, CourseService courseService,
            QuizScheduleService quizScheduleService, QuizStatisticService quizStatisticService, AuthorizationCheckService authCheckService,
            GroupNotificationService groupNotificationService, ExerciseService exerciseService, UserService userService, ExamService examService) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userService = userService;
        this.courseService = courseService;
        this.quizScheduleService = quizScheduleService;
        this.quizStatisticService = quizStatisticService;
        this.authCheckService = authCheckService;
        this.groupNotificationService = groupNotificationService;
        this.exerciseService = exerciseService;
        this.examService = examService;
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

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see below in update Quiz)
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        // Validate score settings
        Optional<ResponseEntity<QuizExercise>> optionalScoreSettingsError = validateScoreSettings(quizExercise);
        if (optionalScoreSettingsError.isPresent()) {
            return optionalScoreSettingsError.get();
        }

        // Valid exercises have set either a course or an exerciseGroup
        exerciseService.checkCourseAndExerciseGroupExclusivity(quizExercise, ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(quizExercise);

        // Check that the user is authorized to create the exercise
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        quizExercise = quizExerciseService.save(quizExercise);

        // Only notify students and tutors if the exercise is created for a course
        if (quizExercise.hasCourse()) {
            // notify websocket channel of changes to the quiz exercise
            quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise, "change");
            groupNotificationService.notifyTutorGroupAboutExerciseCreated(quizExercise);
        }

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

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see above in create Quiz)
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        // Validate score settings
        Optional<ResponseEntity<QuizExercise>> optionalScoreSettingsError = validateScoreSettings(quizExercise);
        if (optionalScoreSettingsError.isPresent()) {
            return optionalScoreSettingsError.get();
        }

        // Valid exercises have set either a course or an exerciseGroup
        exerciseService.checkCourseAndExerciseGroupExclusivity(quizExercise, ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(quizExercise);

        // Check that the user is authorized to update the exercise
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        // Forbid conversion between normal course exercise and exam exercise
        QuizExercise quizExerciseBeforeUpdate = quizExerciseService.findOne(quizExercise.getId());
        exerciseService.checkForConversionBetweenExamAndCourseExercise(quizExercise, quizExerciseBeforeUpdate, ENTITY_NAME);

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

        quizExercise = quizExerciseService.save(quizExercise);

        // TODO: it does not really make sense to notify students here because the quiz is not visible yet when it is edited!
        // Only notify students about changes if a regular exercise in a course was updated
        if (notificationText != null && quizExercise.hasCourse()) {
            // notify websocket channel of changes to the quiz exercise
            quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise, "change");
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
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get QuizExercise : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);

        if (quizExercise.hasExerciseGroup()) {
            // Get the course over the exercise group
            Course course = quizExercise.getExerciseGroup().getExam().getCourse();

            if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
                return forbidden();
            }
        }
        else if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
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
        if (quizExercise == null) {
            return notFound();
        }
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return forbidden();
        }
        quizStatisticService.recalculateStatistics(quizExercise);
        // fetch the quiz exercise again to make sure the latest changes are included
        return ResponseEntity.ok(quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId()));
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
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);
        if (quizExercise == null) {
            return ResponseEntity.notFound().build();
        }

        // check permissions
        Course course = quizExercise.getCourseViaExerciseGroupOrCourseMember();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            return forbidden();
        }

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
        quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId());
        quizScheduleService.updateQuizExercise(quizExercise);

        // notify websocket channel of changes to the quiz exercise
        quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise, action);
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
        Optional<QuizExercise> quizExerciseOptional = quizExerciseService.findById(quizExerciseId);
        if (quizExerciseOptional.isEmpty()) {
            return notFound();
        }
        Course course = quizExerciseOptional.get().getCourseViaExerciseGroupOrCourseMember();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        // note: we use the exercise service here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(quizExerciseOptional.get(), course, user);
        exerciseService.delete(quizExerciseId, false, false);
        quizExerciseService.cancelScheduledQuiz(quizExerciseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, quizExerciseOptional.get().getTitle())).build();
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
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<QuizExercise> reEvaluateQuizExercise(@PathVariable Long quizExerciseId, @RequestBody QuizExercise quizExercise) {
        log.debug("REST request to re-evaluate QuizExercise : {}", quizExercise);
        QuizExercise originalQuizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);
        if (originalQuizExercise == null) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizExerciseNotFound",
                    "The quiz exercise does not exist yet. Use POST to create a new quizExercise.")).build();
        }

        if (originalQuizExercise.hasExerciseGroup()) {
            // Re-evaluation of an exam quiz is only possible if all students finished their exam
            ZonedDateTime latestIndividualExamEndDate = examService.getLatestIndividualExamEndDate(originalQuizExercise.getExerciseGroup().getExam());
            if (latestIndividualExamEndDate == null || latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "examOfQuizExerciseNotEnded",
                        "The exam of the quiz exercise has not ended yet. Re-evaluation is only allowed after an exam has ended.")).build();
            }
        }
        else if (!originalQuizExercise.isEnded()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizExerciseNotEnded",
                    "The quiz exercise has not ended yet. Re-evaluation is only allowed after a quiz has ended.")).build();
        }

        if (!authCheckService.isAtLeastInstructorForExercise(originalQuizExercise, null)) {
            return forbidden();
        }

        quizExercise = quizExerciseService.reEvaluate(quizExercise, originalQuizExercise);

        // Validate score settings
        Optional<ResponseEntity<QuizExercise>> optionalScoreSettingsError = validateScoreSettings(quizExercise);
        if (optionalScoreSettingsError.isPresent()) {
            return optionalScoreSettingsError.get();
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, quizExercise.getId().toString())).body(quizExercise);
    }

    /**
     * Validates score settings
     * 1. The maxScore needs to be greater than 0
     * 2. If the IncludedInOverallScore enum is either INCLUDED_AS_BONUS or NOT_INCLUDED, no bonus points are allowed
     *
     * @param quizExercise exercise to validate
     * @return Optional validation error response
     */
    private Optional<ResponseEntity<QuizExercise>> validateScoreSettings(QuizExercise quizExercise) {
        // Check if max score is set
        if (quizExercise.getMaxScore() == null || quizExercise.getMaxScore() == 0) {
            return Optional
                    .of(ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The max score needs to be greater than 0", "maxscoreInvalid")).body(null));
        }

        // Check IncludedInOverallScore
        if ((quizExercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_AS_BONUS
                || quizExercise.getIncludedInOverallScore() == IncludedInOverallScore.NOT_INCLUDED) && quizExercise.getBonusPoints() > 0) {
            return Optional.of(ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "Bonus points are not allowed", "bonusPointsInvalid")).body(null));
        }

        if (quizExercise.getBonusPoints() == null) {
            // make sure the default value is set properly
            quizExercise.setBonusPoints(0.0);
        }
        return Optional.empty();
    }

}

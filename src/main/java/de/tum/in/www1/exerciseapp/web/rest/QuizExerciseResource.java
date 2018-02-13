package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.*;
import de.tum.in.www1.exerciseapp.service.*;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
    private final ParticipationRepository participationRepository;
    private final UserService userService;
    private final CourseService courseService;
    private final StatisticService statisticService;
    private final AuthorizationCheckService authCheckService;
    private final SimpMessageSendingOperations messagingTemplate;

    public QuizExerciseResource(UserService userService,
                                QuizExerciseService quizExerciseService,
                                ParticipationRepository participationRepository,
                                CourseService courseService,
                                StatisticService statisticService,
                                AuthorizationCheckService authCheckService,
                                SimpMessageSendingOperations messagingTemplate) {
        this.userService = userService;
        this.quizExerciseService = quizExerciseService;
        this.participationRepository = participationRepository;
        this.courseService = courseService;
        this.statisticService = statisticService;
        this.authCheckService = authCheckService;
        this.messagingTemplate = messagingTemplate;
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
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "invalidQuiz", "The quiz exercise is invalid")).body(null);
        }

        QuizExercise result = quizExerciseService.save(quizExercise);

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
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
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
        if (originalQuiz.hasStarted()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "quizHasStarted", "The quiz has already started. Use the re-evaluate endpoint to make retroactive corrections.")).body(null);
        }

        reconnectJSONIgnoreAttributes(quizExercise);

        // reset Released-Flag in all statistics if they are released but the quiz hasn't ended yet
        if (!quizExercise.hasStarted() || quizExercise.getRemainingTime() > 0) {
            quizExercise.getQuizPointStatistic().setReleased(false);
            for (Question question : quizExercise.getQuestions()) {
                if (question.getQuestionStatistic() != null) {
                    question.getQuestionStatistic().setReleased(false);
                }
            }
        }

        QuizExercise result = quizExerciseService.save(quizExercise);

        // notify websocket channel of changes to the quiz exercise
        messagingTemplate.convertAndSend("/topic/quizExercise/" + quizExercise.getId(), true);

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
     * GET  /courses/:courseId/exercises : get all the exercises.
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
     * GET  /quiz-exercises/:id : get the "id" quizExercise.
     *
     * @param id the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> getQuizExercise(@PathVariable Long id) {
        log.debug("REST request to get QuizExercise : {}", id);
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(id);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizExercise));
    }

    /**
     * GET  /quiz-exercises/:id : get the "id" quizExercise.
     *
     * @param id the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{id}/for-student")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> getQuizExerciseForStudent(@PathVariable Long id) {
        log.debug("REST request to get QuizExercise : {}", id);
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(id);
        if (quizExercise == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!authCheckService.isAllowedToSeeExercise(quizExercise, null)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // filter out all questions, if quiz hasn't started yet
        if (!quizExercise.hasStarted()) {
            quizExercise.setQuestions(new ArrayList<>());
        }

        // only filter out information if quiz hasn't ended yet
        if (quizExercise.shouldFilterForStudents()) {
            // filter out "explanation" and "questionStatistic" field from all questions (so students can't see explanation and questionStatistic while answering quiz)
            for (Question question : quizExercise.getQuestions()) {
                question.setExplanation(null);
                if (question.getQuestionStatistic() != null && !question.getQuestionStatistic().isReleased()) {
                    question.setQuestionStatistic(null);
                }

                // filter out "isCorrect" and "explanation" fields from answerOptions in all MC questions (so students can't see correct options in JSON)
                if (question instanceof MultipleChoiceQuestion) {
                    MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) question;
                    for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                        answerOption.setIsCorrect(null);
                        answerOption.setExplanation(null);
                    }
                }

                // filter out "correctMappings" from DragAndDropQuestions
                if (question instanceof DragAndDropQuestion) {
                    DragAndDropQuestion dndQuestion = (DragAndDropQuestion) question;
                    dndQuestion.setCorrectMappings(null);
                }
            }
        }
        // filter out the statistic information if the statistic is not released
        if (quizExercise.getQuizPointStatistic() != null && !quizExercise.getQuizPointStatistic().isReleased()) {
            // filter out all statistical-Data of "quizPointStatistic" if the statistic is not released(so students can't see quizPointStatistic while answering quiz)
            quizExercise.getQuizPointStatistic().setPointCounters(null);
            quizExercise.getQuizPointStatistic().setParticipantsRated(null);
            quizExercise.getQuizPointStatistic().setParticipantsUnrated(null);
        }

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
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        switch (action) {
            case "start-now":
                // check if quiz hasn't already started
                if (quizExercise.hasStarted()) {
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
                if (!quizExercise.hasStarted() || quizExercise.getRemainingTime() > 0) {
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
                if (quizExercise.hasStarted() && quizExercise.getRemainingTime() < 0) {
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
        quizExerciseService.save(quizExercise);

        // notify websocket channel of changes to the quiz exercise
        messagingTemplate.convertAndSend("/topic/quizExercise/" + quizExercise.getId(), true);

        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE  /quiz-exercises/:id : delete the "id" quizExercise.
     *
     * @param id the id of the quizExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long id) {
        log.debug("REST request to delete QuizExercise : {}", id);

        QuizExercise quizExercise = quizExerciseService.findOne(id);
        if (quizExercise == null) {
            return ResponseEntity.notFound().build();
        }

        Course course = quizExercise.getCourse();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isTeachingAssistantInCourse(course, user) &&
            !authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Participation> participationsToDelete = participationRepository.findByExerciseId(id);

        for (Participation participation : participationsToDelete) {
            participationRepository.delete(participation.getId());
        }

        quizExerciseService.delete(id);
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
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/quiz-exercises-re-evaluate")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> reEvaluateQuizExercise(@RequestBody QuizExercise quizExercise) throws URISyntaxException {
        log.debug("REST request to re-evaluate QuizExercise : {}", quizExercise);
        if (quizExercise.getId() == null) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "quizExerciseWithoutId", "The quiz exercise doesn't have an ID.")).build();
        }
        QuizExercise originalQuizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId());
        if (originalQuizExercise == null) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "quizExerciseNotFound", "The quiz exercise does not exist yet. Use POST to create a new quizExercise.")).build();
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(quizExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "courseNotFound", "The course belonging to this quiz exercise does not exist")).body(null);
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        quizExercise.undoUnallowedChanges(originalQuizExercise);
        boolean updateOfResultsAndStatisticsNecessary = quizExercise.checkIfRecalculationIsNecessary(originalQuizExercise);

        //adjust existing results if an answer or and question was deleted
        quizExerciseService.adjustResultsOnQuizDeletions(quizExercise);

        //update QuizExercise
        reconnectJSONIgnoreAttributes(quizExercise);
        QuizExercise result = quizExerciseService.save(quizExercise);

        // update Statistics and Results
        if (updateOfResultsAndStatisticsNecessary) {
            statisticService.updateStatisticsAndResults(quizExercise);
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, quizExercise.getId().toString()))
            .body(result);
    }

    /**
     * Recreate missing pointers from children to parents that were removed by @JSONIgnore
     *
     * @param quizExercise the quiz exercise for which the pointers should be recreated
     */
    private void reconnectJSONIgnoreAttributes(QuizExercise quizExercise) {
        // iterate through questions to add missing pointer back to quizExercise
        // Note: This is necessary because of the @IgnoreJSON in question and answerOption
        //       that prevents infinite recursive JSON serialization.
        for (Question question : quizExercise.getQuestions()) {
            if (question.getId() != null) {
                question.setExercise(quizExercise);
                //reconnect QuestionStatistics
                if (question.getQuestionStatistic() != null) {
                    question.getQuestionStatistic().setQuestion(question);
                }
                // do the same for answerOptions (if question is multiple choice)
                if (question instanceof MultipleChoiceQuestion) {
                    MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) question;
                    MultipleChoiceQuestionStatistic mcStatistic = (MultipleChoiceQuestionStatistic) mcQuestion.getQuestionStatistic();
                    //reconnect answerCounters
                    for (AnswerCounter answerCounter : mcStatistic.getAnswerCounters()) {
                        if (answerCounter.getId() != null) {
                            answerCounter.setMultipleChoiceQuestionStatistic(mcStatistic);
                        }
                    }
                    // reconnect answerOptions
                    for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                        if (answerOption.getId() != null) {
                            answerOption.setQuestion(mcQuestion);
                        }
                    }
                }
                if (question instanceof DragAndDropQuestion) {
                    DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                    DragAndDropQuestionStatistic dragAndDropStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuestionStatistic();
                    //reconnect dropLocationCounters
                    for (DropLocationCounter dropLocationCounter : dragAndDropStatistic.getDropLocationCounters()) {
                        if (dropLocationCounter.getId() != null) {
                            dropLocationCounter.setDragAndDropQuestionStatistic(dragAndDropStatistic);
                        }
                    }
                    // reconnect dropLocations
                    for (DropLocation dropLocation : dragAndDropQuestion.getDropLocations()) {
                        if (dropLocation.getId() != null) {
                            dropLocation.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect dragItems
                    for (DragItem dragItem : dragAndDropQuestion.getDragItems()) {
                        if (dragItem.getId() != null) {
                            dragItem.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect correctMappings
                    for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
                        if (mapping.getId() != null) {
                            mapping.setQuestion(dragAndDropQuestion);
                        }
                    }
                }
            }
        }
        //reconnect quizPointStatistic
        quizExercise.getQuizPointStatistic().setQuiz(quizExercise);
        //reconnect pointCounters
        for (PointCounter pointCounter : quizExercise.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getId() != null) {
                pointCounter.setQuizPointStatistic(quizExercise.getQuizPointStatistic());
            }
        }
    }

}

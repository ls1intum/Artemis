package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.sun.org.apache.regexp.internal.RE;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.*;
import de.tum.in.www1.exerciseapp.service.AuthorizationCheckService;
import de.tum.in.www1.exerciseapp.service.StatisticService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
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
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * REST controller for managing QuizExercise.
 */
@RestController
@RequestMapping("/api")
public class QuizExerciseResource {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    private final QuizExerciseRepository quizExerciseRepository;
    private final ParticipationRepository participationRepository;
    private final StatisticService statisticService;
    private final ResultRepository resultRepository;
    private final QuizPointStatisticRepository quizPointStatisticRepository;
    private final QuestionStatisticRepository questionStatisticRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final DragAndDropMappingRepository dragAndDropMappingRepository;
    private final AuthorizationCheckService authCheckService;

    public QuizExerciseResource(QuizExerciseRepository quizExerciseRepository,
                                ParticipationRepository participationRepository,
                                StatisticService statisticService,
                                ResultRepository resultRepository,
                                QuizPointStatisticRepository quizPointStatisticRepository,
                                QuestionStatisticRepository questionStatisticRepository,
                                QuizSubmissionRepository quizSubmissionRepository,
                                DragAndDropMappingRepository dragAndDropMappingRepository,
                                AuthorizationCheckService authCheckService) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.participationRepository = participationRepository;
        this.statisticService = statisticService;
        this.resultRepository = resultRepository;
        this.quizPointStatisticRepository = quizPointStatisticRepository;
        this.questionStatisticRepository = questionStatisticRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.dragAndDropMappingRepository = dragAndDropMappingRepository;
        this.authCheckService = authCheckService;
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

        Course course = quizExercise.getCourse();
        // NOTE (Valentin): I don't think this check is secure, because the course object is parsed from JSON,
        // not fetched from the Database, so the client can put whatever they want as the TA or instructor group
        // TODO: fetch course from Database using its courseId instead of taking it directly from the Exercise
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
            !authCheckService.isInstructorInCourse(course) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // fix references in all drag and drop questions (step 1/2)
        for (Question question : quizExercise.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // save references as index to prevent Hibernate Persistence problem
                saveCorrectMappingsInIndices(dragAndDropQuestion);
            }
        }

        QuizExercise result = quizExerciseRepository.save(quizExercise);

        // fix references in all drag and drop questions (step 2/2)
        for (Question question : result.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // restore references from index after save
                restoreCorrectMappingsFromIndices(dragAndDropQuestion);
            }
        }

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

        Course course = quizExercise.getCourse();
        // NOTE (Valentin): I don't think this check is secure, because the course object is parsed from JSON,
        // not fetched from the Database, so the client can put whatever they want as the TA or instructor group
        // TODO: fetch course from Database using its courseId instead of taking it directly from the Exercise
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
            !authCheckService.isInstructorInCourse(course) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // iterate through questions to add missing pointer back to quizExercise
        // Note: This is necessary because of the @IgnoreJSON in question and answerOption
        //       that prevents infinite recursive JSON serialization.
        for (Question question : quizExercise.getQuestions()) {
            if (question.getId() != null) {
                question.setExercise(quizExercise);
                //reconnect QuestionStatistics
                if (question.getQuestionStatistic() != null){
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
                    // TODO: @Moritz: Reconnect whatever needs to be reconnected

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

        // reset Released-Flag in all statistics if they are released but the quiz hasn't ended yet
        if (!quizExercise.isIsPlannedToStart() || quizExercise.getRemainingTime() > 0) {
            quizExercise.getQuizPointStatistic().setReleased(false);
            for (Question question : quizExercise.getQuestions()) {
                // TODO: @Moritz: fix this for DragAndDropQuestions (getQuestionStatistic() returns null)
                if (question.getQuestionStatistic() != null) {
                    question.getQuestionStatistic().setReleased(false);
                }
            }
        }
        //notify clients via websocket about the release state of the statistics.
        statisticService.releaseStatistic(quizExercise, quizExercise.getQuizPointStatistic().isReleased());

        // fix references in all drag and drop questions (step 1/2)
        for (Question question : quizExercise.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // save references as index to prevent Hibernate Persistence problem
                saveCorrectMappingsInIndices(dragAndDropQuestion);
            }
        }

        // save result
        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from the questions
        //       and delete the now orphaned entries from the database
        QuizExercise result = quizExerciseRepository.save(quizExercise);

        // fix references in all drag and drop questions (step 2/2)
        for (Question question : result.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // restore references from index after save
                restoreCorrectMappingsFromIndices(dragAndDropQuestion);
            }
        }

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
        List<QuizExercise> quizExercises = quizExerciseRepository.findAll();
        Stream<QuizExercise> authorizedExercises = quizExercises.stream().filter(
            exercise -> {
                Course course = exercise.getCourse();
                return authCheckService.isTeachingAssistantInCourse(course) ||
                    authCheckService.isInstructorInCourse(course) ||
                    authCheckService.isAdmin();
            }
        );
        return authorizedExercises.collect(Collectors.toList());
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
        List<QuizExercise> quizExercises = quizExerciseRepository.findByCourseId(courseId);
        Stream<QuizExercise> authorizedExercises = quizExercises.stream().filter(
            exercise -> {
                Course course = exercise.getCourse();
                return authCheckService.isTeachingAssistantInCourse(course) ||
                    authCheckService.isInstructorInCourse(course) ||
                    authCheckService.isAdmin();
            }
        );
        return authorizedExercises.collect(Collectors.toList());
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
        QuizExercise quizExercise = quizExerciseRepository.findOne(id);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise)) {
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
        QuizExercise quizExercise = quizExerciseRepository.findOne(id);
        if (!authCheckService.isAllowedToSeeExercise(quizExercise)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (quizExercise == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
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

        QuizExercise quizExercise = quizExerciseRepository.findOne(id);
        if (quizExercise == null) {
            return ResponseEntity.notFound().build();
        }

        Course course = quizExercise.getCourse();
        if (!authCheckService.isTeachingAssistantInCourse(course) &&
            !authCheckService.isInstructorInCourse(course) &&
            !authCheckService.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Participation> participationsToDelete = participationRepository.findByExerciseId(id);

        for (Participation participation : participationsToDelete) {
            participationRepository.delete(participation.getId());
        }

        quizExerciseRepository.delete(id);
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
            return createQuizExercise(quizExercise);
        }
        QuizExercise originalQuizExercise = quizExerciseRepository.findOne(quizExercise.getId());
        boolean updateOfResultsAndStatisticsNecessary = undoUnallowedChangesAndCheckIfRecalculationIsNecessary(quizExercise, originalQuizExercise);

        //change existing results if an answer or and question was deleted
        for (Result result : resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId())) {

            Set<SubmittedAnswer> submittedAnswersToDelete = new HashSet<>();

            for (SubmittedAnswer submittedAnswer : ((QuizSubmission) result.getSubmission()).getSubmittedAnswers()) {
                if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                    // Delete all references to question and answers if the question was deleted
                    if (!quizExercise.getQuestions().contains(submittedAnswer.getQuestion())) {
                        submittedAnswer.setQuestion(null);
                        ((MultipleChoiceSubmittedAnswer) submittedAnswer).setSelectedOptions(null);
                        submittedAnswersToDelete.add(submittedAnswer);
                    } else {
                        // find same question in quizExercise
                        Question question = quizExercise.findQuestionById(submittedAnswer.getQuestion().getId());

                        // Check if an answerOption was deleted and delete reference to in selectedOptions
                        ((MultipleChoiceSubmittedAnswer) submittedAnswer).checkForDeletedAnswerOptions((MultipleChoiceQuestion) question);
                    }
                    // TODO: @Moritz: DragAndDrop Question
                }
            }
            ((QuizSubmission) result.getSubmission()).getSubmittedAnswers().removeAll(submittedAnswersToDelete);
            quizSubmissionRepository.save((QuizSubmission) result.getSubmission());
        }

        //update QuizExercise
        ResponseEntity<QuizExercise> methodResult = updateQuizExercise(quizExercise);

        // update Statistics and Results
        if (updateOfResultsAndStatisticsNecessary) {
            updateStatisticsAndResults(quizExercise);
        }

        return methodResult;
    }

    /**
     * 1. undo all changes which are not allowed ( dueDate, releaseDate, question.points, adding Questions and Answers)
     * 2. check if an update of the Results and Statistics is necessary
     *
     * @param quizExercise the changed QuizExercise object
     * @param originalQuizExercise the original QuizExercise object, which will be compared with the new changed quizExercise
     *
     * @return a boolean which is true if an update is necessary and false if not
     */
    private boolean undoUnallowedChangesAndCheckIfRecalculationIsNecessary (QuizExercise quizExercise, QuizExercise originalQuizExercise){

        boolean updateOfResultsAndStatisticsNecessary = false;

        //reset unchangeable attributes: ( dueDate, releaseDate, question.points)
        quizExercise.setDueDate(originalQuizExercise.getDueDate());
        quizExercise.setReleaseDate(originalQuizExercise.getReleaseDate());

        //remove added Questions, which are not allowed to be added
        // and check the changes -> updates of statistics and results necessary?
        Set<Question> addedQuestions = new HashSet<>();

        //check every question
        for (Question question : quizExercise.getQuestions()) {
            //check if the question were already in the originalQuizExercise -> if not it's an added question
            if (originalQuizExercise.getQuestions().contains(question)) {
                // find original unchanged question
                Question originalQuestion = originalQuizExercise.findQuestionById(question.getId());
                //reset score (not allowed to change)
                question.setScore(originalQuestion.getScore());
                //reset invalid if the question is already invalid;
                question.setInvalid(question.isInvalid() || originalQuestion.isInvalid());

                // check if a question is  set invalid or if the scoringType has changed
                // if true an update of the Statistics and Results is necessary
                updateOfResultsAndStatisticsNecessary = updateOfResultsAndStatisticsNecessary ||
                    (question.isInvalid() && !originalQuestion.isInvalid()) ||
                    !question.getScoringType().equals(originalQuestion.getScoringType());

                //undo all not allowed changes in the answers of the MultipleChoiceQuestion
                // and check if the answers effect make an update of the statistics and results necessary
                if (question instanceof MultipleChoiceQuestion) {
                    updateOfResultsAndStatisticsNecessary = updateOfResultsAndStatisticsNecessary ||
                        undoUnallowedChangesAndCheckMultipleChoiceQuestionAnswers((MultipleChoiceQuestion) question, (MultipleChoiceQuestion) originalQuestion);
                }

                if (question instanceof DragAndDropQuestion) {
                    // TODO: @Moritz: check changes in DragAndDropQuestions
                }

            } else {
                // question is added (not allowed), mark question for remove
                addedQuestions.add(question);
            }
        }
        // remove all added questions
        quizExercise.getQuestions().removeAll(addedQuestions);

        // check if an question was deleted (not allowed added quistions are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (quizExercise.getQuestions().size() != originalQuizExercise.getQuestions().size()) {
            updateOfResultsAndStatisticsNecessary = true;
        }
        return updateOfResultsAndStatisticsNecessary;
    }

    /**
     * 1. undo all changes which are not allowed ( adding Answers)
     * 2. check if an update of the Results and Statistics is necessary
     *
     * @param question the changed MultipleChoiceQuestion-object
     * @param originalQuestion the original MultipleChoiceQuestion-object, which will be compared with the new changed question
     *
     * @return a boolean which is true if the answer-changes make an update necessary and false if not
     */
    private boolean undoUnallowedChangesAndCheckMultipleChoiceQuestionAnswers (MultipleChoiceQuestion question, MultipleChoiceQuestion originalQuestion){

        boolean updateNecessary = false;

        //find added Answers, which are not allowed to be added
        Set<AnswerOption> notAllowedAddedAnswers = new HashSet<>();
        //check every answer of the question
        for (AnswerOption answer : question.getAnswerOptions()) {
            //check if the answer were already in the originalQuizExercise -> if not it's an added answer
            if (originalQuestion.getAnswerOptions().contains(answer)) {
                //find original answer
                AnswerOption originalAnswer = originalQuestion.findAnswerOptionById(answer.getId());
                //reset invalid answer if it already set to true (it's not possible to set an answer valid again)
                answer.setInvalid(answer.isInvalid() || originalAnswer.isInvalid());

                // check if an answer is set invalid or if the correctness has changed
                // if true an update of the Statistics and Results is necessary
                if ((answer.isInvalid() && !originalAnswer.isInvalid() && !question.isInvalid()) ||
                    (!(answer.isIsCorrect().equals(originalAnswer.isIsCorrect())))) {
                    updateNecessary = true;
                }
            } else {
                //mark the added Answers (adding questions is not allowed)
                notAllowedAddedAnswers.add(answer);
            }
        }
        //remove the added Answers
        question.getAnswerOptions().removeAll(notAllowedAddedAnswers);

        // check if an answer was deleted (not allowed added answers are not relevant)
        // if true an update of the Statistics and Results is necessary
        if ( question.getAnswerOptions().size() < originalQuestion.getAnswerOptions().size()) {
            updateNecessary = true;
        }
        return updateNecessary;
    }

    /**
     * 1. Go through all Results in the Participation and recalculate the score
     * 2. recalculate the statistic the given quizExercise
     *
     * @param quizExercise the changed QuizExercise object which will be used to recalculate the existing Results and Statistics
     */
    private void updateStatisticsAndResults(QuizExercise quizExercise){

        //reset all statistic
        quizExercise.getQuizPointStatistic().resetStatistic();
        for (Question question : quizExercise.getQuestions()) {
            question.getQuestionStatistic().resetStatistic();
        }

        // update the Results in every participation of the given quizExercise
        for (Participation participation : participationRepository.findByExerciseId(quizExercise.getId())) {

            Result latestRatedResult = null;
            Result latestUnratedResult = null;

            // update all Results of a participation
            for (Result result : resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId())) {

                //recalculate existing score
                ((QuizSubmission) result.getSubmission()).calculateAndUpdateScores(quizExercise);
                //update Successful-Flag in Result
                result.setScore(Math.round(((QuizSubmission) result.getSubmission()).getScoreInPoints() / quizExercise.getMaxTotalScore() * 100));
                if (result.getScore() == 100) {
                    result.setSuccessful(true);
                } else {
                    result.setSuccessful(false);
                }
                // save the updated Result and its Submission
                resultRepository.save(result);
                quizSubmissionRepository.save((QuizSubmission) result.getSubmission());

                //save latest rated Result for Statistic update
                if (result.getCompletionDate().isBefore(quizExercise.getDueDate())
                    && (latestRatedResult == null || latestRatedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestRatedResult = result;
                }
                //save latest unrated Result for Statistic update
                if (result.getCompletionDate().isAfter(quizExercise.getDueDate())
                    && (latestUnratedResult == null || latestUnratedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestUnratedResult = result;
                }
            }

            // update QuizPointStatistic with the latest rated Result
            if (latestRatedResult != null) {
                quizExercise.getQuizPointStatistic().addResult(latestRatedResult.getScore(), true);
            }
            // update QuizPointStatistic with the latest unrated Result
            if (latestUnratedResult != null) {
                quizExercise.getQuizPointStatistic().addResult(latestUnratedResult.getScore(), false);
            }
            for (Question question : quizExercise.getQuestions()) {
                // update QuestionStatistics with the latest rated Result
                if (latestRatedResult != null) {
                    question.getQuestionStatistic().addResult(((QuizSubmission) latestRatedResult.getSubmission()).getSubmittedAnswerForQuestion(question), true);
                }
                // update QuestionStatistics with the latest unrated Result
                if (latestUnratedResult != null) {
                    question.getQuestionStatistic().addResult(((QuizSubmission) latestUnratedResult.getSubmission()).getSubmittedAnswerForQuestion(question), false);
                }
            }
        }
        //save changed Statistics
        quizPointStatisticRepository.save(quizExercise.getQuizPointStatistic());
        for (Question question : quizExercise.getQuestions()) {
            questionStatisticRepository.save(question.getQuestionStatistic());
        }
    }

    /**
     * remove dragItem and dropLocation from correct mappings and set dragItemIndex and dropLocationIndex instead
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void saveCorrectMappingsInIndices(DragAndDropQuestion dragAndDropQuestion) {
        List<DragAndDropMapping> mappingsToBeRemoved = new ArrayList<>();
        for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
            // check for NullPointers
            if (mapping.getDragItem() == null || mapping.getDropLocation() == null) {
                mappingsToBeRemoved.add(mapping);
                continue;
            }

            // drag item index
            DragItem dragItem = mapping.getDragItem();
            boolean dragItemFound = false;
            for (DragItem questionDragItem : dragAndDropQuestion.getDragItems()) {
                if (dragItem.equals(questionDragItem)) {
                    dragItemFound = true;
                    mapping.setDragItemIndex(dragAndDropQuestion.getDragItems().indexOf(questionDragItem));
                    mapping.setDragItem(null);
                    break;
                }
            }

            // replace drop location
            DropLocation dropLocation = mapping.getDropLocation();
            boolean dropLocationFound = false;
            for (DropLocation questionDropLocation : dragAndDropQuestion.getDropLocations()) {
                if (dropLocation.equals(questionDropLocation)) {
                    dropLocationFound = true;
                    mapping.setDropLocationIndex(dragAndDropQuestion.getDropLocations().indexOf(questionDropLocation));
                    mapping.setDropLocation(null);
                    break;
                }
            }

            // if one of them couldn't be found, remove the mapping entirely
            if (!dragItemFound || !dropLocationFound) {
                mappingsToBeRemoved.add(mapping);
            }
        }

        for (DragAndDropMapping mapping : mappingsToBeRemoved) {
            dragAndDropQuestion.removeCorrectMappings(mapping);
        }
    }

    /**
     * restore dragItem and dropLocation for correct mappings using dragItemIndex and dropLocationIndex
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void restoreCorrectMappingsFromIndices(DragAndDropQuestion dragAndDropQuestion) {
        for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
            // drag item
            mapping.setDragItem(dragAndDropQuestion.getDragItems().get(mapping.getDragItemIndex()));
            // drop location
            mapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(mapping.getDropLocationIndex()));
            // set question
            mapping.setQuestion(dragAndDropQuestion);
            // save mapping
            dragAndDropMappingRepository.save(mapping);
        }
    }
}

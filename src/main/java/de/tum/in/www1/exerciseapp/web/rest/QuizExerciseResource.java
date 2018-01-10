package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.sun.org.apache.regexp.internal.RE;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.*;
import de.tum.in.www1.exerciseapp.service.StatisticService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    public QuizExerciseResource(QuizExerciseRepository quizExerciseRepository,
                                ParticipationRepository participationRepository,
                                StatisticService statisticService,
                                ResultRepository resultRepository,
                                QuizPointStatisticRepository quizPointStatisticRepository,
                                QuestionStatisticRepository questionStatisticRepository,
                                QuizSubmissionRepository quizSubmissionRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.participationRepository = participationRepository;
        this.statisticService = statisticService;
        this.resultRepository = resultRepository;
        this.quizPointStatisticRepository = quizPointStatisticRepository;
        this.questionStatisticRepository = questionStatisticRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
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
        QuizExercise result = quizExerciseRepository.save(quizExercise);
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

        // iterate through questions to add missing pointer back to quizExercise
        // Note: This is necessary because of the @IgnoreJSON in question and answerOption
        //       that prevents infinite recursive JSON serialization.
        for (Question question : quizExercise.getQuestions()) {
            if (question.getId() != null) {
                question.setExercise(quizExercise);
                //reconnect QuestionStatistics
                question.getQuestionStatistic().setQuestion(question);
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
            }
            // TODO: Valentin: do the same for dragItems and dropLocations (if question is drag and drop)
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
        if (quizExercise != null && (!quizExercise.isIsPlannedToStart() || quizExercise.getRemainingTime() > 0)) {
            quizExercise.getQuizPointStatistic().setReleased(false);
            for (Question question : quizExercise.getQuestions()) {
                question.getQuestionStatistic().setReleased(false);
            }
        }
        //notify clients via websocket about the release state of the statistics.
        statisticService.releaseStatistic(quizExercise, quizExercise.getQuizPointStatistic().isReleased());

        //change existing results if an answer or and question was deleted
        for (Result result : resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId())) {

            Set<SubmittedAnswer> submittedAnswersToDelete = new HashSet<>();

            for (SubmittedAnswer submittedAnswer : ((QuizSubmission) result.getSubmission()).getSubmittedAnswers()) {
                if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                    // Delete all references to question an answers if the question was deleted
                    if (!quizExercise.getQuestions().contains(submittedAnswer.getQuestion())) {
                        submittedAnswer.setQuestion(null);
                        ((MultipleChoiceSubmittedAnswer) submittedAnswer).setSelectedOptions(null);
                        submittedAnswersToDelete.add(submittedAnswer);
                    } else {
                        // find same question in quizExercise
                        for (Question question : quizExercise.getQuestions()) {
                            if (question.getId().equals(submittedAnswer.getQuestion().getId())) {
                                // Check if an answerOption was deleted and delete reference to in selectedOptions
                                Set<AnswerOption> selectedOptionsToDelete = new HashSet<>();
                                for (AnswerOption answerOption : ((MultipleChoiceSubmittedAnswer) submittedAnswer).getSelectedOptions()) {
                                    if (!((MultipleChoiceQuestion) question).getAnswerOptions().contains(answerOption)) {
                                        selectedOptionsToDelete.add(answerOption);
                                    }
                                }
                                ((MultipleChoiceSubmittedAnswer) submittedAnswer).getSelectedOptions().removeAll(selectedOptionsToDelete);

                            }
                        }
                    }
                }
                // TO-DO DragAndDrop Question
            }
            ((QuizSubmission) result.getSubmission()).getSubmittedAnswers().removeAll(submittedAnswersToDelete);
            quizSubmissionRepository.save((QuizSubmission) result.getSubmission());
        }

        // save result
        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from the questions
        //       and delete the now orphaned entries from the database
        QuizExercise result = quizExerciseRepository.save(quizExercise);
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
        return quizExerciseRepository.findAll();
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
        //TODO Valentin: apply the same filtering as in getProgrammingExercisesForCourse(...),
        //this call is only used in the admin interface and there, tutors should not see exercise of courses in which they are only students
        return quizExerciseRepository.findByCourseId(courseId);
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

        // only filter out information if quiz hasn't ended yet
        if (quizExercise != null && quizExercise.shouldFilterForStudents()) {
            // filter out "explanation" and "questionStatistic" field from all questions (so students can't see explanation and questionStatistic while answering quiz)
            for (Question question : quizExercise.getQuestions()) {
                question.setExplanation(null);
                if (!question.getQuestionStatistic().isReleased()) {
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
            }
        }
        // filter out the statistic information if the statistic is not released
        if (!quizExercise.getQuizPointStatistic().isReleased()) {
            // filter out all statistical-Data of "quizPointStatistic" if the statistic is not released(so students can't see quizPointStatistic while answering quiz)
            quizExercise.getQuizPointStatistic().setPointCounters(null);
            quizExercise.getQuizPointStatistic().setParticipantsRated(null);
            quizExercise.getQuizPointStatistic().setParticipantsUnrated(null);
        }

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizExercise));
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
        boolean updateResultsAndStatistics = false;

        //reset unchangeable attributes: ( dueDate, releaseDate, question.points)

        quizExercise.setDueDate(originalQuizExercise.getDueDate());
        quizExercise.setReleaseDate(originalQuizExercise.getReleaseDate());

        //remove added Questions, which are not allowed to be added
        // and check the changes -> updates of statistics and results necessary?
        Set<Question> addedQuestions = new HashSet<>();

        for (Question question : quizExercise.getQuestions()) {
            if (originalQuizExercise.getQuestions().contains(question)) {
                // find original unchanged question
                for (Question originalQuestion : originalQuizExercise.getQuestions()) {
                    if (originalQuestion.getId().equals(question.getId())) {
                        //reset score (not allowed to change)
                        question.setScore(originalQuestion.getScore());
                        //reset invalid if the question is already invalid;
                        question.setInvalid(question.isInvalid() || originalQuestion.isInvalid());


                        // check in a question is  set invalid or if the scoringType has changed
                        if ((question.isInvalid() && !originalQuestion.isInvalid()) ||
                            !question.getScoringType().equals(originalQuestion.getScoringType())) {
                            updateResultsAndStatistics = true;
                        }

                        if (question instanceof MultipleChoiceQuestion) {
                            //find added Answers, which are not allowed to be added
                            Set<AnswerOption> notAllowedAddedAnswers = new HashSet<AnswerOption>();
                            for (AnswerOption answer : (((MultipleChoiceQuestion) question).getAnswerOptions())) {
                                if (((MultipleChoiceQuestion) originalQuestion).getAnswerOptions().contains(answer)) {
                                    //find original answer
                                    for (AnswerOption originalAnswer : ((MultipleChoiceQuestion) originalQuestion).getAnswerOptions()) {
                                        if (answer.getId().equals(originalAnswer.getId())) {
                                            //reset invalid answer if it already set to true (it's not possible to set an answer valid again)
                                            answer.setInvalid(answer.isInvalid() || originalAnswer.isInvalid());

                                            // check if an answer is  set invalid or if the correctness has changed
                                            if ((answer.isInvalid() && !originalAnswer.isInvalid() && !question.isInvalid())||
                                                (!(answer.isIsCorrect().equals(originalAnswer.isIsCorrect())))) {
                                                updateResultsAndStatistics = true;
                                            }
                                        }

                                    }
                                } else {
                                    //note the added Answers
                                    notAllowedAddedAnswers.add(answer);
                                }
                            }
                            //remove the added Answers
                            ((MultipleChoiceQuestion) question).getAnswerOptions().removeAll(notAllowedAddedAnswers);

                            // check if an answer was deleted
                            if (((MultipleChoiceQuestion) question).getAnswerOptions().size() <
                                ((MultipleChoiceQuestion) originalQuestion).getAnswerOptions().size()) {
                                updateResultsAndStatistics = true;
                            }
                        }
                    }
                }
            } else {
                // question is added (not allowed)
                addedQuestions.add(question);
            }
        }
        // remove all added questions
        quizExercise.getQuestions().removeAll(addedQuestions);

        //update QuizExercise
        ResponseEntity<QuizExercise> methodResult = updateQuizExercise(quizExercise);

        // question deleted?
        if (quizExercise.getQuestions().size() != originalQuizExercise.getQuestions().size()) {
            updateResultsAndStatistics = true;
        }

        // update Statistics and Results
        if (updateResultsAndStatistics) {

            //reset all statistic
            quizExercise.getQuizPointStatistic().resetStatistic();
            for (Question question : quizExercise.getQuestions()) {
                question.getQuestionStatistic().resetStatistic();
            }

            for (Participation participation : participationRepository.findByExerciseId(quizExercise.getId())) {

                Result lastRatedResult = null;
                Result lastUnratedResult = null;

                for (Result result : resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId())) {

                    //recalculate existing score
                    ((QuizSubmission) result.getSubmission()).calculateAndUpdateScores(quizExercise);
                    result.setScore(Math.round(((QuizSubmission) result.getSubmission()).getScoreInPoints() / quizExercise.getMaxTotalScore() * 100));
                    if (result.getScore() == 100) {
                        result.setSuccessful(true);
                    } else {
                        result.setSuccessful(false);
                    }
                    resultRepository.save(result);
                    quizSubmissionRepository.save((QuizSubmission) result.getSubmission());

                    //save last rated and unrated Result for Statistic update
                    if (result.getCompletionDate().isBefore(quizExercise.getDueDate().plusSeconds(5))
                        && (lastRatedResult == null || lastRatedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                        lastRatedResult = result;
                    }
                    if (result.getCompletionDate().isAfter(quizExercise.getDueDate().plusSeconds(5))
                        && (lastUnratedResult == null || lastUnratedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                        lastUnratedResult = result;
                    }
                }

                // update Statistics with latest Results
                if (lastRatedResult != null) {
                    quizExercise.getQuizPointStatistic().addResult(lastRatedResult.getScore(), true);
                }
                if (lastUnratedResult != null) {
                    quizExercise.getQuizPointStatistic().addResult(lastUnratedResult.getScore(), false);
                }
                for (Question question : quizExercise.getQuestions()) {
                    if (lastRatedResult != null) {
                        question.getQuestionStatistic().addResult(((QuizSubmission) lastRatedResult.getSubmission()).getSubmittedAnswerForQuestion(question), true);
                    }
                    if (lastUnratedResult != null) {
                        question.getQuestionStatistic().addResult(((QuizSubmission) lastUnratedResult.getSubmission()).getSubmittedAnswerForQuestion(question), false);
                    }
                }
            }
            //save Statistics
            quizPointStatisticRepository.save(quizExercise.getQuizPointStatistic());
            for (Question question : quizExercise.getQuestions()) {
                questionStatisticRepository.save(question.getQuestionStatistic());
            }
        }

        return methodResult;
    }
}

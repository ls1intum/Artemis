package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.DragAndDropAssignmentRepository;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.repository.QuizExerciseRepository;
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

    private final QuizExerciseRepository quizExerciseRepository;
    private final ParticipationRepository participationRepository;
    private final StatisticService statisticService;
    private final DragAndDropAssignmentRepository dragAndDropAssignmentRepository;

    public QuizExerciseResource(QuizExerciseRepository quizExerciseRepository, ParticipationRepository participationRepository, StatisticService statisticService, DragAndDropAssignmentRepository dragAndDropAssignmentRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.participationRepository = participationRepository;
        this.statisticService = statisticService;
        this.dragAndDropAssignmentRepository = dragAndDropAssignmentRepository;
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

        // fix references in all drag and drop questions (step 1/2)
        for (Question question : quizExercise.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // save references as index to prevent Hibernate Persistence problem
                updateCorrectAssignmentIndexes(dragAndDropQuestion);
            }
        }

        QuizExercise result = quizExerciseRepository.save(quizExercise);

        // fix references in all drag and drop questions (step 2/2)
        for (Question question : result.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // restore references from index after save
                updateCorrectAssignmentObjects(dragAndDropQuestion);
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

        // iterate through questions to add missing pointer back to quizExercise
        // Note: This is necessary because of the @IgnoreJSON in question and answerOption
        //       that prevents infinite recursive JSON serialization.
        for (Question question : quizExercise.getQuestions()) {
            if (question.getId() != null) {
                question.setExercise(quizExercise);
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
                    // reconnect correctAssignments
                    for (DragAndDropAssignment assignment : dragAndDropQuestion.getCorrectAssignments()) {
                        if (assignment.getId() != null) {
                            assignment.setQuestion(dragAndDropQuestion);
                        }
                    }
                }
            }
        }
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

        // fix references in all drag and drop questions (step 1/2)
        for (Question question : quizExercise.getQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) question;
                // save references as index to prevent Hibernate Persistence problem
                updateCorrectAssignmentIndexes(dragAndDropQuestion);
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
                updateCorrectAssignmentObjects(dragAndDropQuestion);
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
     * remove item and location from correct assignments and set itemIndex and locationIndex instead
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void updateCorrectAssignmentIndexes(DragAndDropQuestion dragAndDropQuestion) {
        List<DragAndDropAssignment> assignmentsToBeRemoved = new ArrayList<>();
        for (DragAndDropAssignment assignment : dragAndDropQuestion.getCorrectAssignments()) {
            // check for NullPointers
            if (assignment.getItem() == null || assignment.getLocation() == null) {
                assignmentsToBeRemoved.add(assignment);
                continue;
            }

            // drag item index
            DragItem dragItem = assignment.getItem();
            boolean dragItemFound = false;
            for (DragItem questionDragItem : dragAndDropQuestion.getDragItems()) {
                if (dragItem.equals(questionDragItem)) {
                    dragItemFound = true;
                    assignment.setItemIndex(dragAndDropQuestion.getDragItems().indexOf(questionDragItem));
                    assignment.setItem(null);
                    break;
                }
            }

            // replace drop location
            DropLocation dropLocation = assignment.getLocation();
            boolean dropLocationFound = false;
            for (DropLocation questionDropLocation : dragAndDropQuestion.getDropLocations()) {
                if (dropLocation.equals(questionDropLocation)) {
                    dropLocationFound = true;
                    assignment.setLocationIndex(dragAndDropQuestion.getDropLocations().indexOf(questionDropLocation));
                    assignment.setLocation(null);
                    break;
                }
            }

            // if one of them couldn't be found, remove the assignment entirely
            if (!dragItemFound || !dropLocationFound) {
                assignmentsToBeRemoved.add(assignment);
            }
        }

        for (DragAndDropAssignment assignment : assignmentsToBeRemoved) {
            dragAndDropQuestion.removeCorrectAssignments(assignment);
        }
    }

    /**
     * restore item and location for correct assignments using itemIndex and locationIndex
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void updateCorrectAssignmentObjects(DragAndDropQuestion dragAndDropQuestion) {
        for (DragAndDropAssignment assignment : dragAndDropQuestion.getCorrectAssignments()) {
            // drag item
            assignment.setItem(dragAndDropQuestion.getDragItems().get(assignment.getItemIndex()));
            // drop location
            assignment.setLocation(dragAndDropQuestion.getDropLocations().get(assignment.getLocationIndex()));
            // set question
            assignment.setQuestion(dragAndDropQuestion);
            // save assignment
            dragAndDropAssignmentRepository.save(assignment);
        }
    }
}

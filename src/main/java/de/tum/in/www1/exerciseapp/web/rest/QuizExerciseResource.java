package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.MultipleChoiceQuestionRepository;
import de.tum.in.www1.exerciseapp.repository.QuizExerciseRepository;
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
import java.util.List;
import java.util.Set;
import java.util.HashSet;
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

    public QuizExerciseResource(QuizExerciseRepository quizExerciseRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
    }

    /**
     * POST  /quiz-exercises : Create a new quizExercise.
     *
     * @param quizExercise the quizExercise to create
     * @return the ResponseEntity with status 201 (Created) and with body the new quizExercise, or with status 400 (Bad Request) if the quizExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/quiz-exercises")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
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
                    mcStatistic.setQuestion(mcQuestion);
                    //reconnect answerCounter-entities
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
            // TODO: do the same for dragItems and dropLocations (if question is drag and drop)
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
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    @Transactional(readOnly = true)
    public List<QuizExercise> getQuizExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all QuizExercises for the course with id : {}", courseId);
        return quizExerciseRepository.findByCourseId(courseId);
    }

    /**
     * GET  /quiz-exercises/:id : get the "id" quizExercise.
     *
     * @param id the id of the quizExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("/quiz-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<QuizExercise> getQuizExercise(@PathVariable Long id) {
        log.debug("REST request to get QuizExercise : {}", id);
        QuizExercise quizExercise = quizExerciseRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(quizExercise));
    }

    /**
     * DELETE  /quiz-exercises/:id : delete the "id" quizExercise.
     *
     * @param id the id of the quizExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/quiz-exercises/{id}")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteQuizExercise(@PathVariable Long id) {
        log.debug("REST request to delete QuizExercise : {}", id);
        quizExerciseRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}

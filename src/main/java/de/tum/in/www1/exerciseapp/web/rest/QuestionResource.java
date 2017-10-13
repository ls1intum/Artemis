package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Question;

import de.tum.in.www1.exerciseapp.repository.QuestionRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing Question.
 */
@RestController
@RequestMapping("/api")
public class QuestionResource {

    private final Logger log = LoggerFactory.getLogger(QuestionResource.class);

    private static final String ENTITY_NAME = "question";

    private final QuestionRepository questionRepository;

    public QuestionResource(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /**
     * POST  /questions : Create a new question.
     *
     * @param question the question to create
     * @return the ResponseEntity with status 201 (Created) and with body the new question, or with status 400 (Bad Request) if the question has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/questions")
    @Timed
    public ResponseEntity<Question> createQuestion(@RequestBody Question question) throws URISyntaxException {
        log.debug("REST request to save Question : {}", question);
        if (question.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(ENTITY_NAME, "idexists", "A new question cannot already have an ID")).body(null);
        }
        Question result = questionRepository.save(question);
        return ResponseEntity.created(new URI("/api/questions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /questions : Updates an existing question.
     *
     * @param question the question to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated question,
     * or with status 400 (Bad Request) if the question is not valid,
     * or with status 500 (Internal Server Error) if the question couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/questions")
    @Timed
    public ResponseEntity<Question> updateQuestion(@RequestBody Question question) throws URISyntaxException {
        log.debug("REST request to update Question : {}", question);
        if (question.getId() == null) {
            return createQuestion(question);
        }
        Question result = questionRepository.save(question);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, question.getId().toString()))
            .body(result);
    }

    /**
     * GET  /questions : get all the questions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of questions in body
     */
    @GetMapping("/questions")
    @Timed
    public List<Question> getAllQuestions() {
        log.debug("REST request to get all Questions");
        return questionRepository.findAll();
    }

    /**
     * GET  /questions/:id : get the "id" question.
     *
     * @param id the id of the question to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the question, or with status 404 (Not Found)
     */
    @GetMapping("/questions/{id}")
    @Timed
    public ResponseEntity<Question> getQuestion(@PathVariable Long id) {
        log.debug("REST request to get Question : {}", id);
        Question question = questionRepository.findOne(id);
        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(question));
    }

    /**
     * DELETE  /questions/:id : delete the "id" question.
     *
     * @param id the id of the question to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/questions/{id}")
    @Timed
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        log.debug("REST request to delete Question : {}", id);
        questionRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}

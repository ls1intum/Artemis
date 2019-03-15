package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.QuestionAnswer;
import de.tum.in.www1.artemis.repository.QuestionAnswerRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
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
 * REST controller for managing QuestionAnswer.
 */
@RestController
@RequestMapping("/api")
public class QuestionAnswerResource {

    private final Logger log = LoggerFactory.getLogger(QuestionAnswerResource.class);

    private static final String ENTITY_NAME = "questionAnswer";

    private final QuestionAnswerRepository questionAnswerRepository;

    public QuestionAnswerResource(QuestionAnswerRepository questionAnswerRepository) {
        this.questionAnswerRepository = questionAnswerRepository;
    }

    /**
     * POST  /question-answers : Create a new questionAnswer.
     *
     * @param questionAnswer the questionAnswer to create
     * @return the ResponseEntity with status 201 (Created) and with body the new questionAnswer, or with status 400 (Bad Request) if the questionAnswer has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/question-answers")
    public ResponseEntity<QuestionAnswer> createQuestionAnswer(@RequestBody QuestionAnswer questionAnswer) throws URISyntaxException {
        log.debug("REST request to save QuestionAnswer : {}", questionAnswer);
        if (questionAnswer.getId() != null) {
            throw new BadRequestAlertException("A new questionAnswer cannot already have an ID", ENTITY_NAME, "idexists");
        }
        QuestionAnswer result = questionAnswerRepository.save(questionAnswer);
        return ResponseEntity.created(new URI("/api/question-answers/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /question-answers : Updates an existing questionAnswer.
     *
     * @param questionAnswer the questionAnswer to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated questionAnswer,
     * or with status 400 (Bad Request) if the questionAnswer is not valid,
     * or with status 500 (Internal Server Error) if the questionAnswer couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/question-answers")
    public ResponseEntity<QuestionAnswer> updateQuestionAnswer(@RequestBody QuestionAnswer questionAnswer) throws URISyntaxException {
        log.debug("REST request to update QuestionAnswer : {}", questionAnswer);
        if (questionAnswer.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        QuestionAnswer result = questionAnswerRepository.save(questionAnswer);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, questionAnswer.getId().toString()))
            .body(result);
    }

    /**
     * GET  /question-answers : get all the questionAnswers.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of questionAnswers in body
     */
    @GetMapping("/question-answers")
    public List<QuestionAnswer> getAllQuestionAnswers() {
        log.debug("REST request to get all QuestionAnswers");
        return questionAnswerRepository.findAll();
    }

    /**
     * GET  /question-answers/:id : get the "id" questionAnswer.
     *
     * @param id the id of the questionAnswer to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the questionAnswer, or with status 404 (Not Found)
     */
    @GetMapping("/question-answers/{id}")
    public ResponseEntity<QuestionAnswer> getQuestionAnswer(@PathVariable Long id) {
        log.debug("REST request to get QuestionAnswer : {}", id);
        Optional<QuestionAnswer> questionAnswer = questionAnswerRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(questionAnswer);
    }

    /**
     * DELETE  /question-answers/:id : delete the "id" questionAnswer.
     *
     * @param id the id of the questionAnswer to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/question-answers/{id}")
    public ResponseEntity<Void> deleteQuestionAnswer(@PathVariable Long id) {
        log.debug("REST request to delete QuestionAnswer : {}", id);
        questionAnswerRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}

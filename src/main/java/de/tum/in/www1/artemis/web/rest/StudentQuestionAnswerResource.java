package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
import de.tum.in.www1.artemis.repository.StudentQuestionAnswerRepository;
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
 * REST controller for managing StudentQuestionAnswer.
 */
@RestController
@RequestMapping("/api")
public class StudentQuestionAnswerResource {

    private final Logger log = LoggerFactory.getLogger(StudentQuestionAnswerResource.class);

    private static final String ENTITY_NAME = "questionAnswer";

    private final StudentQuestionAnswerRepository studentQuestionAnswerRepository;

    public StudentQuestionAnswerResource(StudentQuestionAnswerRepository studentQuestionAnswerRepository) {
        this.studentQuestionAnswerRepository = studentQuestionAnswerRepository;
    }

    /**
     * POST  /question-answers : Create a new studentQuestionAnswer.
     *
     * @param studentQuestionAnswer the studentQuestionAnswer to create
     * @return the ResponseEntity with status 201 (Created) and with body the new studentQuestionAnswer, or with status 400 (Bad Request) if the studentQuestionAnswer has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/student-question-answers")
    public ResponseEntity<StudentQuestionAnswer> createStudentQuestionAnswer(@RequestBody StudentQuestionAnswer studentQuestionAnswer) throws URISyntaxException {
        log.debug("REST request to save StudentQuestionAnswer : {}", studentQuestionAnswer);
        if (studentQuestionAnswer.getId() != null) {
            throw new BadRequestAlertException("A new studentQuestionAnswer cannot already have an ID", ENTITY_NAME, "idexists");
        }
        StudentQuestionAnswer result = studentQuestionAnswerRepository.save(studentQuestionAnswer);
        return ResponseEntity.created(new URI("/api/question-answers/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /question-answers : Updates an existing studentQuestionAnswer.
     *
     * @param studentQuestionAnswer the studentQuestionAnswer to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated studentQuestionAnswer,
     * or with status 400 (Bad Request) if the studentQuestionAnswer is not valid,
     * or with status 500 (Internal Server Error) if the studentQuestionAnswer couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/student-question-answers")
    public ResponseEntity<StudentQuestionAnswer> updateStudentQuestionAnswer(@RequestBody StudentQuestionAnswer studentQuestionAnswer) throws URISyntaxException {
        log.debug("REST request to update StudentQuestionAnswer : {}", studentQuestionAnswer);
        if (studentQuestionAnswer.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        StudentQuestionAnswer result = studentQuestionAnswerRepository.save(studentQuestionAnswer);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, studentQuestionAnswer.getId().toString()))
            .body(result);
    }

    /**
     * GET  /question-answers : get all the questionAnswers.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of questionAnswers in body
     */
    @GetMapping("/student-question-answers")
    public List<StudentQuestionAnswer> getAllStudentQuestionAnswers() {
        log.debug("REST request to get all QuestionAnswers");
        return studentQuestionAnswerRepository.findAll();
    }

    /**
     * GET  /question-answers/:id : get the "id" questionAnswer.
     *
     * @param id the id of the questionAnswer to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the questionAnswer, or with status 404 (Not Found)
     */
    @GetMapping("/student-question-answers/{id}")
    public ResponseEntity<StudentQuestionAnswer> getStudentQuestionAnswer(@PathVariable Long id) {
        log.debug("REST request to get StudentQuestionAnswer : {}", id);
        Optional<StudentQuestionAnswer> questionAnswer = studentQuestionAnswerRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(questionAnswer);
    }

    /**
     * DELETE  /question-answers/:id : delete the "id" questionAnswer.
     *
     * @param id the id of the questionAnswer to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/student-question-answers/{id}")
    public ResponseEntity<Void> deleteStudentQuestionAnswer(@PathVariable Long id) {
        log.debug("REST request to delete StudentQuestionAnswer : {}", id);
        studentQuestionAnswerRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}

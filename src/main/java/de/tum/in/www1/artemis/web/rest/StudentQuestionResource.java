package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.repository.StudentQuestionRepository;
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
 * REST controller for managing StudentQuestion.
 */
@RestController
@RequestMapping("/api")
public class StudentQuestionResource {

    private final Logger log = LoggerFactory.getLogger(StudentQuestionResource.class);

    private static final String ENTITY_NAME = "studentQuestion";

    private final StudentQuestionRepository studentQuestionRepository;

    public StudentQuestionResource(StudentQuestionRepository studentQuestionRepository) {
        this.studentQuestionRepository = studentQuestionRepository;
    }

    /**
     * POST  /student-questions : Create a new studentQuestion.
     *
     * @param studentQuestion the studentQuestion to create
     * @return the ResponseEntity with status 201 (Created) and with body the new studentQuestion, or with status 400 (Bad Request) if the studentQuestion has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/student-questions")
    public ResponseEntity<StudentQuestion> createStudentQuestion(@RequestBody StudentQuestion studentQuestion) throws URISyntaxException {
        log.debug("REST request to save StudentQuestion : {}", studentQuestion);
        if (studentQuestion.getId() != null) {
            throw new BadRequestAlertException("A new studentQuestion cannot already have an ID", ENTITY_NAME, "idexists");
        }
        StudentQuestion result = studentQuestionRepository.save(studentQuestion);
        return ResponseEntity.created(new URI("/api/student-questions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /student-questions : Updates an existing studentQuestion.
     *
     * @param studentQuestion the studentQuestion to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated studentQuestion,
     * or with status 400 (Bad Request) if the studentQuestion is not valid,
     * or with status 500 (Internal Server Error) if the studentQuestion couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/student-questions")
    public ResponseEntity<StudentQuestion> updateStudentQuestion(@RequestBody StudentQuestion studentQuestion) throws URISyntaxException {
        log.debug("REST request to update StudentQuestion : {}", studentQuestion);
        if (studentQuestion.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        StudentQuestion result = studentQuestionRepository.save(studentQuestion);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, studentQuestion.getId().toString()))
            .body(result);
    }

    /**
     * GET  /student-questions : get all the studentQuestions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of studentQuestions in body
     */
    @GetMapping("/student-questions")
    public List<StudentQuestion> getAllStudentQuestions() {
        log.debug("REST request to get all StudentQuestions");
        return studentQuestionRepository.findAll();
    }

    /**
     * GET  /student-questions/:id : get the "id" studentQuestion.
     *
     * @param id the id of the studentQuestion to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the studentQuestion, or with status 404 (Not Found)
     */
    @GetMapping("/student-questions/{id}")
    public ResponseEntity<StudentQuestion> getStudentQuestion(@PathVariable Long id) {
        log.debug("REST request to get StudentQuestion : {}", id);
        Optional<StudentQuestion> studentQuestion = studentQuestionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(studentQuestion);
    }

    /**
     * DELETE  /student-questions/:id : delete the "id" studentQuestion.
     *
     * @param id the id of the studentQuestion to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/student-questions/{id}")
    public ResponseEntity<Void> deleteStudentQuestion(@PathVariable Long id) {
        log.debug("REST request to delete StudentQuestion : {}", id);
        studentQuestionRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}

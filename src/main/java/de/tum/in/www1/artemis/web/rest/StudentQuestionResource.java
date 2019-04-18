package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.repository.StudentQuestionRepository;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.StudentQuestionService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing StudentQuestion.
 */
@RestController
@RequestMapping("/api")
public class StudentQuestionResource {

    private final Logger log = LoggerFactory.getLogger(StudentQuestionResource.class);

    private static final String ENTITY_NAME = "studentQuestion";

    private final StudentQuestionRepository studentQuestionRepository;

    private final StudentQuestionService studentQuestionService;

    GroupNotificationService groupNotificationService;

    public StudentQuestionResource(StudentQuestionRepository studentQuestionRepository, GroupNotificationService groupNotificationService,
            StudentQuestionService studentQuestionService) {
        this.studentQuestionRepository = studentQuestionRepository;
        this.studentQuestionService = studentQuestionService;
        this.groupNotificationService = groupNotificationService;
    }

    /**
     * POST /student-questions : Create a new studentQuestion.
     *
     * @param studentQuestion the studentQuestion to create
     * @return the ResponseEntity with status 201 (Created) and with body the new studentQuestion, or with status 400 (Bad Request) if the studentQuestion has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentQuestion> createStudentQuestion(@RequestBody StudentQuestion studentQuestion) throws URISyntaxException {
        log.debug("REST request to save StudentQuestion : {}", studentQuestion);
        if (studentQuestion.getId() != null) {
            throw new BadRequestAlertException("A new studentQuestion cannot already have an ID", ENTITY_NAME, "idexists");
        }
        StudentQuestion question = studentQuestionRepository.save(studentQuestion);
        if (question.getExercise() != null) { // TODO what happens if the question belongs to a lecture?
            groupNotificationService.notifyGroupAboutNewQuestion(question);
        }
        return ResponseEntity.created(new URI("/api/student-questions/" + question.getId())).headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, question.getId().toString()))
                .body(question);
    }

    /**
     * PUT /student-questions : Updates an existing studentQuestion.
     *
     * @param studentQuestion the studentQuestion to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated studentQuestion, or with status 400 (Bad Request) if the studentQuestion is not valid, or with
     *         status 500 (Internal Server Error) if the studentQuestion couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentQuestion> updateStudentQuestion(@RequestBody StudentQuestion studentQuestion) throws URISyntaxException {
        log.debug("REST request to update StudentQuestion : {}", studentQuestion);
        if (studentQuestion.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        StudentQuestion result = studentQuestionRepository.save(studentQuestion);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, studentQuestion.getId().toString())).body(result);
    }

    /**
     * GET /student-questions/:id : get the "id" studentQuestion.
     *
     * @param id the id of the studentQuestion to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the studentQuestion, or with status 404 (Not Found)
     */
    @GetMapping("/student-questions/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentQuestion> getStudentQuestion(@PathVariable Long id) {
        log.debug("REST request to get StudentQuestion : {}", id);
        Optional<StudentQuestion> studentQuestion = studentQuestionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(studentQuestion);
    }

    /**
     * GET /studentQuestions : get all student questions for exercise.
     *
     * @param exerciseId the exercise that the student questions belong to
     * @return the ResponseEntity with status 200 (OK) and with body all student questions for exercise
     */
    @GetMapping("/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentQuestion>> getAllQuestions(@RequestParam(value = "lecture", required = false) Long lectureId,
            @RequestParam(value = "exercise", required = false) Long exerciseId) {
        List<StudentQuestion> studentQuestions = null;
        if (exerciseId != null) {
            studentQuestions = studentQuestionService.findStudentQuestionsForExercise(exerciseId);
        }
        else if (lectureId != null) {
            studentQuestions = studentQuestionService.findStudentQuestionsForLecture(lectureId);
        }

        return new ResponseEntity<>(studentQuestions, null, HttpStatus.OK);
    }

    /**
     * DELETE /student-questions/:id : delete the "id" studentQuestion.
     *
     * @param id the id of the studentQuestion to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/student-questions/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteStudentQuestion(@PathVariable Long id) {
        log.debug("REST request to delete StudentQuestion : {}", id);
        studentQuestionRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}

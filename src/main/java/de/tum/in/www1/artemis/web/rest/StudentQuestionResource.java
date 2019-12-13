package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.StudentQuestionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing StudentQuestion.
 */
@RestController
@RequestMapping("/api")
public class StudentQuestionResource {

    private final Logger log = LoggerFactory.getLogger(StudentQuestionResource.class);

    private static final String ENTITY_NAME = "studentQuestion";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final StudentQuestionRepository studentQuestionRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureRepository lectureRepository;

    private final StudentQuestionService studentQuestionService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserService userService;

    GroupNotificationService groupNotificationService;

    public StudentQuestionResource(StudentQuestionRepository studentQuestionRepository, GroupNotificationService groupNotificationService, LectureRepository lectureRepository,
            StudentQuestionService studentQuestionService, AuthorizationCheckService authorizationCheckService, UserService userService, ExerciseRepository exerciseRepository) {
        this.studentQuestionRepository = studentQuestionRepository;
        this.studentQuestionService = studentQuestionService;
        this.groupNotificationService = groupNotificationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userService = userService;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
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
    // TODO: there are no security checks here. The API endpoint should at least include the course id
    public ResponseEntity<StudentQuestion> createStudentQuestion(@RequestBody StudentQuestion studentQuestion) throws URISyntaxException {
        log.debug("REST request to save StudentQuestion : {}", studentQuestion);
        if (studentQuestion.getId() != null) {
            throw new BadRequestAlertException("A new studentQuestion cannot already have an ID", ENTITY_NAME, "idexists");
        }
        StudentQuestion question = studentQuestionRepository.save(studentQuestion);
        if (question.getExercise() != null) {
            groupNotificationService.notifyTutorAndInstructorGroupAboutNewQuestionForExercise(question);
        }
        if (question.getLecture() != null) {
            groupNotificationService.notifyTutorAndInstructorGroupAboutNewQuestionForLecture(question);
        }
        return ResponseEntity.created(new URI("/api/student-questions/" + question.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, question.getId().toString())).body(question);
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
    // TODO: there are no security checks here. The API endpoint should at least include the course id
    public ResponseEntity<StudentQuestion> updateStudentQuestion(@RequestBody StudentQuestion studentQuestion) throws URISyntaxException {
        log.debug("REST request to update StudentQuestion : {}", studentQuestion);
        if (studentQuestion.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        StudentQuestion result = studentQuestionRepository.save(studentQuestion);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, studentQuestion.getId().toString())).body(result);
    }

    /**
     * GET /exercises/{exerciseId}/student-questions : get all student questions for exercise.
     *
     * @param exerciseId the exercise that the student questions belong to
     * @return the ResponseEntity with status 200 (OK) and with body all student questions for exercise
     */
    @GetMapping("exercises/{exerciseId}/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentQuestion>> getAllQuestionsForExercise(@PathVariable Long exerciseId) {
        final User user = userService.getUserWithGroupsAndAuthorities();
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if (exercise.isEmpty()) {
            throw new EntityNotFoundException("Exercise with exerciseId " + exerciseId + " does not exist!");
        }
        if (!authorizationCheckService.isAtLeastStudentForExercise(exercise.get(), user)) {
            return forbidden();
        }
        List<StudentQuestion> studentQuestions = studentQuestionService.findStudentQuestionsForExercise(exerciseId);
        hideSensitiveInformation(studentQuestions);

        return new ResponseEntity<>(studentQuestions, null, HttpStatus.OK);
    }

    /**
     *
     * GET /lectures/{lectureId}/student-questions : get all student questions for exercise.
     * @param lectureId the lecture that the student questions belong to
     * @return the ResponseEntity with status 200 (OK) and with body all student questions for exercise
     */
    @GetMapping("lectures/{lectureId}/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentQuestion>> getAllQuestionsForLecture(@PathVariable Long lectureId) {
        final User user = userService.getUserWithGroupsAndAuthorities();
        Optional<Lecture> lecture = lectureRepository.findById(lectureId);
        if (lecture.isEmpty()) {
            throw new EntityNotFoundException("Exercise with exerciseId " + lectureId + " does not exist!");
        }
        if (!authorizationCheckService.isAtLeastStudentInCourse(lecture.get().getCourse(), user)) {
            return forbidden();
        }
        List<StudentQuestion> studentQuestions = studentQuestionService.findStudentQuestionsForLecture(lectureId);
        hideSensitiveInformation(studentQuestions);

        return new ResponseEntity<>(studentQuestions, null, HttpStatus.OK);
    }

    private void hideSensitiveInformation(List<StudentQuestion> studentQuestions) {
        for (StudentQuestion question : studentQuestions) {
            question.setExercise(null);
            question.setLecture(null);
            question.setAuthor(question.getAuthor().copyBasicUser());
            for (StudentQuestionAnswer answer : question.getAnswers()) {
                answer.setAuthor(answer.getAuthor().copyBasicUser());
            }
        }
    }

    /**
     * DELETE /student-questions/:id : delete the "id" studentQuestion.
     *
     * @param id the id of the studentQuestion to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/student-questions/{id}")
    // TODO: there are no security checks here. The API endpoint should at least include the course id
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteStudentQuestion(@PathVariable Long id) {
        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<StudentQuestion> optionalStudentQuestion = studentQuestionRepository.findById(id);
        if (optionalStudentQuestion.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StudentQuestion studentQuestion = optionalStudentQuestion.get();
        Course course = null;
        String entity = "";
        if (studentQuestion.getLecture() != null) {
            course = studentQuestion.getLecture().getCourse();
            entity = "lecture with id: " + studentQuestion.getLecture().getId();
        }
        else if (studentQuestion.getExercise() != null) {
            course = studentQuestion.getExercise().getCourse();
            entity = "exercise with id: " + studentQuestion.getExercise().getId();
        }
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        Boolean hasCourseTAAccess = authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        Boolean isUserAuthor = user.getId().equals(studentQuestion.getAuthor().getId());
        if (hasCourseTAAccess || isUserAuthor) {
            log.info("StudentQuestion deleted by " + user.getLogin() + ". Question: " + studentQuestion.getQuestionText() + " for " + entity, user.getLogin());
            studentQuestionRepository.deleteById(id);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
        }
        else {
            return forbidden();
        }
    }
}

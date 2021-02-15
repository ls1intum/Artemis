package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.repository.RepositoryHelper.findCourseByIdElseThrow;
import static de.tum.in.www1.artemis.repository.RepositoryHelper.findStudentQuestionByIdElseThrow;
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
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.StudentQuestionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
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

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final GroupNotificationService groupNotificationService;

    public StudentQuestionResource(StudentQuestionRepository studentQuestionRepository, GroupNotificationService groupNotificationService, LectureRepository lectureRepository,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository) {
        this.studentQuestionRepository = studentQuestionRepository;
        this.groupNotificationService = groupNotificationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /courses/{courseId}/student-questions : Create a new studentQuestion.
     *
     * @param courseId course the question belongs to
     * @param studentQuestion the studentQuestion to create
     * @return the ResponseEntity with status 201 (Created) and with body the new studentQuestion, or with status 400 (Bad Request) if the studentQuestion has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentQuestion> createStudentQuestion(@PathVariable Long courseId, @RequestBody StudentQuestion studentQuestion) throws URISyntaxException {
        log.debug("REST request to save StudentQuestion : {}", studentQuestion);
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (studentQuestion.getId() != null) {
            throw new BadRequestAlertException("A new studentQuestion cannot already have an ID", ENTITY_NAME, "idexists");
        }
        final var course = findCourseByIdElseThrow(courseRepository, courseId);
        if (!this.authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            return forbidden();
        }
        if (!studentQuestion.getCourse().getId().equals(courseId)) {
            return forbidden();
        }
        StudentQuestion question = studentQuestionRepository.save(studentQuestion);
        if (question.getExercise() != null) {
            groupNotificationService.notifyTutorAndInstructorGroupAboutNewQuestionForExercise(question);
        }
        if (question.getLecture() != null) {
            groupNotificationService.notifyTutorAndInstructorGroupAboutNewQuestionForLecture(question);
        }
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/student-questions/" + question.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, question.getId().toString())).body(question);
    }

    /**
     * PUT /courses/{courseId}/student-questions : Updates an existing studentQuestion.
     *
     * @param courseId course the question belongs to
     * @param studentQuestion the studentQuestion to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated studentQuestion, or with status 400 (Bad Request) if the studentQuestion is not valid, or with
     *         status 500 (Internal Server Error) if the studentQuestion couldn't be updated
     */
    @PutMapping("courses/{courseId}/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentQuestion> updateStudentQuestion(@PathVariable Long courseId, @RequestBody StudentQuestion studentQuestion) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to update StudentQuestion : {}", studentQuestion);
        if (studentQuestion.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        findCourseByIdElseThrow(courseRepository, courseId);
        var existingStudentQuestion = findStudentQuestionByIdElseThrow(studentQuestionRepository, studentQuestion.getId());
        if (!existingStudentQuestion.getCourse().getId().equals(courseId)) {
            return forbidden();
        }
        if (mayUpdateOrDeleteStudentQuestion(existingStudentQuestion, user)) {
            existingStudentQuestion.setQuestionText(studentQuestion.getQuestionText());
            existingStudentQuestion.setVisibleForStudents(studentQuestion.isVisibleForStudents());
            StudentQuestion result = studentQuestionRepository.save(existingStudentQuestion);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, studentQuestion.getId().toString())).body(result);
        }
        else {
            return forbidden();
        }
    }

    /**
     * PUT /courses/{courseId}/student-questions/{questionId}/votes : Updates votes for a studentQuestion.
     *
     * @param courseId course the question belongs to
     * @param questionId the ID of the question to update
     * @param voteChange value by which votes are increased / decreased
     * @return the ResponseEntity with status 200 (OK) and with body the updated studentQuestion, or with status 400 (Bad Request) if the studentQuestion is not valid, or with
     *         status 500 (Internal Server Error) if the studentQuestion couldn't be updated
     */
    @PutMapping("courses/{courseId}/student-questions/{questionId}/votes")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentQuestion> updateStudentQuestionVotes(@PathVariable Long courseId, @PathVariable Long questionId, @RequestBody Integer voteChange) {
        if (voteChange < -2 || voteChange > 2) {
            return forbidden();
        }
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<StudentQuestion> optionalStudentQuestion = studentQuestionRepository.findById(questionId);
        if (optionalStudentQuestion.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        findCourseByIdElseThrow(courseRepository, courseId);
        if (!optionalStudentQuestion.get().getCourse().getId().equals(courseId)) {
            return forbidden();
        }
        if (mayUpdateStudentQuestionVotes(optionalStudentQuestion.get(), user)) {
            StudentQuestion updatedStudentQuestion = optionalStudentQuestion.get();
            Integer newVotes = updatedStudentQuestion.getVotes() + voteChange;
            updatedStudentQuestion.setVotes(newVotes);
            StudentQuestion result = studentQuestionRepository.save(updatedStudentQuestion);
            return ResponseEntity.ok().body(result);
        }
        else {
            return forbidden();
        }
    }

    /**
     * GET /courses/{courseId}/exercises/{exerciseId}/student-questions : get all student questions for exercise.
     *
     * @param courseId course the question belongs to
     * @param exerciseId the exercise that the student questions belong to
     * @return the ResponseEntity with status 200 (OK) and with body all student questions for exercise
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentQuestion>> getAllQuestionsForExercise(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<Exercise> exercise = exerciseRepository.findById(exerciseId);
        if (exercise.isEmpty()) {
            throw new EntityNotFoundException("Exercise with exerciseId " + exerciseId + " does not exist!");
        }
        findCourseByIdElseThrow(courseRepository, courseId);
        if (!authorizationCheckService.isAtLeastStudentForExercise(exercise.get(), user)) {
            return forbidden();
        }
        if (!exercise.get().getCourseViaExerciseGroupOrCourseMember().getId().equals(courseId)) {
            return forbidden();
        }
        List<StudentQuestion> studentQuestions = studentQuestionRepository.findStudentQuestionsForExercise(exerciseId);
        hideSensitiveInformation(studentQuestions);

        return new ResponseEntity<>(studentQuestions, null, HttpStatus.OK);
    }

    /**
     * GET /courses/{courseId}/lectures/{lectureId}/student-questions : get all student questions for lecture.
     *
     * @param courseId course the question belongs to
     * @param lectureId the lecture that the student questions belong to
     * @return the ResponseEntity with status 200 (OK) and with body all student questions for lecture
     */
    @GetMapping("courses/{courseId}/lectures/{lectureId}/student-questions")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentQuestion>> getAllQuestionsForLecture(@PathVariable Long courseId, @PathVariable Long lectureId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<Lecture> lecture = lectureRepository.findById(lectureId);
        if (lecture.isEmpty()) {
            throw new EntityNotFoundException("Lecture with lectureId " + lectureId + " does not exist!");
        }
        findCourseByIdElseThrow(courseRepository, courseId);
        if (!authorizationCheckService.isAtLeastStudentInCourse(lecture.get().getCourse(), user)) {
            return forbidden();
        }
        if (!lecture.get().getCourse().getId().equals(courseId)) {
            return forbidden();
        }
        List<StudentQuestion> studentQuestions = studentQuestionRepository.findStudentQuestionsForLecture(lectureId);
        hideSensitiveInformation(studentQuestions);

        return new ResponseEntity<>(studentQuestions, null, HttpStatus.OK);
    }

    /**
     *
     * GET /courses/{courseId}/student-questions : get all student questions for course
     * @param courseId the course that the student questions belong to
     * @return the ResponseEntity with status 200 (OK) and with body all student questions for course
     */
    @GetMapping("courses/{courseId}/student-questions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentQuestion>> getAllQuestionsForCourse(@PathVariable Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        var course = findCourseByIdElseThrow(courseRepository, courseId);
        if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        List<StudentQuestion> studentQuestions = studentQuestionRepository.findStudentQuestionsForCourse(courseId);

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
     * DELETE /courses/{courseId}/student-questions/:id : delete the "id" studentQuestion.
     *
     * @param courseId course the question belongs to
     * @param studentQuestionId the id of the studentQuestion to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/student-questions/{studentQuestionId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteStudentQuestion(@PathVariable Long courseId, @PathVariable Long studentQuestionId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        findCourseByIdElseThrow(courseRepository, courseId);
        var studentQuestion = findStudentQuestionByIdElseThrow(studentQuestionRepository, studentQuestionId);
        String entity = "";
        if (studentQuestion.getLecture() != null) {
            entity = "lecture with id: " + studentQuestion.getLecture().getId();
        }
        else if (studentQuestion.getExercise() != null) {
            entity = "exercise with id: " + studentQuestion.getExercise().getId();
        }
        if (studentQuestion.getCourse() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (mayUpdateOrDeleteStudentQuestion(studentQuestion, user)) {
            log.info("StudentQuestion deleted by " + user.getLogin() + ". Question: " + studentQuestion.getQuestionText() + " for " + entity, user.getLogin());
            studentQuestionRepository.deleteById(studentQuestionId);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, studentQuestionId.toString())).build();
        }
        else {
            return forbidden();
        }
    }

    private boolean mayUpdateOrDeleteStudentQuestion(StudentQuestion studentQuestion, User user) {
        Boolean hasCourseTAAccess = authorizationCheckService.isAtLeastTeachingAssistantInCourse(studentQuestion.getCourse(), user);
        Boolean isUserAuthor = user.getId().equals(studentQuestion.getAuthor().getId());
        return hasCourseTAAccess || isUserAuthor;
    }

    private boolean mayUpdateStudentQuestionVotes(StudentQuestion studentQuestion, User user) {
        Course course = studentQuestion.getCourse();
        Exercise exercise = studentQuestion.getExercise();
        if (course != null) {
            return authorizationCheckService.isAtLeastStudentInCourse(course, user);
        }
        else if (exercise != null) {
            return authorizationCheckService.isAtLeastStudentForExercise(exercise, user);
        }
        else {
            return false;
        }
    }
}

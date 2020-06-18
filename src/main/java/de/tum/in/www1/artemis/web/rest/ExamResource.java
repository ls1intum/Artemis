package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Exam.
 */
@RestController
@RequestMapping("/api")
public class ExamResource {

    private final Logger log = LoggerFactory.getLogger(ExamResource.class);

    private static final String ENTITY_NAME = "exam";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final CourseService courseService;

    private final ExamRepository examRepository;

    private final ExamService examService;

    private final ExamAccessService examAccessService;

    private final ExerciseService exerciseService;

    private final AuditEventRepository auditEventRepository;

    public ExamResource(UserService userService, CourseService courseService, ExamRepository examRepository, ExamService examService, ExamAccessService examAccessService,
            ExerciseService exerciseService, AuditEventRepository auditEventRepository) {
        this.userService = userService;
        this.courseService = courseService;
        this.examRepository = examRepository;
        this.examService = examService;
        this.examAccessService = examAccessService;
        this.exerciseService = exerciseService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * POST /courses/{courseId}/exams : Create a new exam.
     *
     * @param courseId  the course to which the exam belongs
     * @param exam      the exam to create
     * @return the ResponseEntity with status 201 (Created) and with body the new exam, or with status 400 (Bad Request) if the exam has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Exam> createExam(@PathVariable Long courseId, @RequestBody Exam exam) throws URISyntaxException {
        log.debug("REST request to create an exam : {}", exam);
        if (exam.getId() != null) {
            throw new BadRequestAlertException("A new exam cannot already have an ID", ENTITY_NAME, "idexists");
        }

        if (exam.getCourse() == null) {
            return conflict();
        }

        if (!exam.getCourse().getId().equals(courseId)) {
            return conflict();
        }

        // Check that exerciseGroups are not set to prevent manipulation of associated exerciseGroups
        if (!exam.getExerciseGroups().isEmpty()) {
            return forbidden();
        }

        Optional<ResponseEntity<Exam>> courseAccessFailure = examAccessService.checkCourseAccess(courseId);
        if (courseAccessFailure.isPresent()) {
            return courseAccessFailure.get();
        }

        Exam result = examService.save(exam);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * PUT /courses/{courseId}/exams : Updates an existing exam.
     * This route does not save changes to the exercise groups. This should be done via the ExerciseGroupResource.
     *
     * @param courseId      the course to which the exam belongs
     * @param updatedExam   the exam to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated exam
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Exam> updateExam(@PathVariable Long courseId, @RequestBody Exam updatedExam) throws URISyntaxException {
        log.debug("REST request to update an exam : {}", updatedExam);
        if (updatedExam.getId() == null) {
            return createExam(courseId, updatedExam);
        }

        if (updatedExam.getCourse() == null) {
            return conflict();
        }

        if (!updatedExam.getCourse().getId().equals(courseId)) {
            return conflict();
        }

        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccess(courseId, updatedExam.getId());
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        // Make sure that the original references are preserved.
        Exam originalExam = examService.findOne(updatedExam.getId());
        // NOTE: Make sure that all references are preserved here
        updatedExam.setExerciseGroups(originalExam.getExerciseGroups());
        updatedExam.setStudentExams(originalExam.getStudentExams());
        updatedExam.setRegisteredUsers(originalExam.getRegisteredUsers());

        Exam result = examService.save(updatedExam);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * GET /courses/{courseId}/exams/{examId} : Find an exam by id.
     *
     * @param courseId      the course to which the exam belongs
     * @param examId        the exam to find
     * @param withStudents  boolean flag whether to include all students registered for the exam
     * @return the ResponseEntity with status 200 (OK) and with the found exam as body
     */
    @GetMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Exam> getExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam(defaultValue = "false") boolean withStudents) {
        log.debug("REST request to get exam : {}", examId);
        Optional<ResponseEntity<Exam>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }
        if (!withStudents) {
            return ResponseEntity.ok(examService.findOne(examId));
        }
        Exam exam = examService.findOneWithRegisteredUsers(examId);
        exam.getRegisteredUsers().forEach(user -> user.setVisibleRegistrationNumber(user.getRegistrationNumber()));
        return ResponseEntity.ok(exam);
    }

    /**
     * GET /courses/{courseId}/exams : Find all exams for the given course.
     *
     * @param courseId  the course to which the exam belongs
     * @return the ResponseEntity with status 200 (OK) and a list of exams. The list can be empty
     */
    @GetMapping("/courses/{courseId}/exams")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<List<Exam>> getExamsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all exams for Course : {}", courseId);
        Optional<ResponseEntity<List<Exam>>> courseAccessFailure = examAccessService.checkCourseAccess(courseId);
        return courseAccessFailure.orElseGet(() -> ResponseEntity.ok(examService.findAllByCourseId(courseId)));
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId} : Delete the exam with the given id.
     * The delete operation cascades to all student exams, exercise group, exercises and their participations.
     *
     * @param courseId  the course to which the exam belongs
     * @param examId    the id of the exam to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Void> deleteExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to delete exam : {}", examId);
        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        Exam exam = examService.findOneWithExercisesGroupsAndStudentExamsByExamId(examId);

        User user = userService.getUser();
        AuditEvent auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXAM, "exam=" + exam.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " has requested to delete the exam {}", exam.getTitle());
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                exerciseService.delete(exercise.getId(), false, false);
            }
        }

        // The database operation cascades to Student Exams and Exercise Groups
        examService.delete(examId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exam.getTitle())).build();
    }

    /**
     * POST /courses/:courseId/exams/:examId/students/:studentLogin : Add one single given user (based on the login) to the students of the exam so that the student can access the exam
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param studentLogin the login of the user who should get student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> addStudentToExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin) {
        log.debug("REST request to add {} as student to exam : {}", studentLogin, examId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        var course = courseService.findOne(courseId);
        var exam = examService.findOneWithRegisteredUsers(examId);

        Optional<User> student = userService.getUserWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (student.isEmpty()) {
            return notFound();
        }
        exam.addUser(student.get());
        // NOTE: we intentionally add the user to the course group, because the user only has access to the exam of a course, if the student also
        // has access to the course of the exam.
        // we only need to add the user to the course group, if the student is not yet part of it, otherwise the student cannot access the exam (within the course)
        if (!student.get().getGroups().contains(course.getStudentGroupName())) {
            userService.addUserToGroup(student.get(), course.getStudentGroupName());
        }
        examRepository.save(exam);
        return ResponseEntity.ok().body(null);
    }

    /**
     * POST /courses/:courseId/exams/:examId/generate-student-exams : Generates the student exams randomly based on the exam configuration and the exercise groups
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @return the list of student exams with their corresponding users
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/generate-student-exams")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentExam>> generateStudentExams(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to generate student exams for exam {}", examId);

        Optional<ResponseEntity<List<StudentExam>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        List<StudentExam> studentExams = examService.generateStudentExams(examId);

        // we need to break a cycle for the serialization
        for (StudentExam studentExam : studentExams) {
            studentExam.getExam().setRegisteredUsers(null);
            studentExam.getExam().setExerciseGroups(null);
            studentExam.getExam().setStudentExams(null);
        }

        log.info("Generated {} student exams for exam {}", studentExams.size(), examId);
        return ResponseEntity.ok().body(studentExams);
    }

    /**
     * POST /courses/:courseId/exams/:examId/students : Add multiple users to the students of the exam so that they can access the exam
     * The passed list of UserDTOs must include the registration number (the other entries are currently ignored and can be left out)
     * Note: registration based on other user attributes (e.g. email, name, login) is currently NOT supported
     *
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @param studentDtos   the list of students (with at least registration number) who should get access to the exam
     * @return the list of students who could not be registered for the exam, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/students")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentDTO>> addStudentsToExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody List<StudentDTO> studentDtos) {
        log.debug("REST request to add {} as students to exam {}", studentDtos, examId);

        Optional<ResponseEntity<List<StudentDTO>>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        List<StudentDTO> notFoundStudentsDtos = examService.registerStudentsForExam(courseId, examId, studentDtos);
        return ResponseEntity.ok().body(notFoundStudentsDtos);
    }

    /**
     * DELETE /courses/:courseId/exams/:examId/students/:studentLogin : Remove one single given user (based on the login) from the students of the exam so that the student cannot access the exam any more
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param studentLogin the login of the user who should lose student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @DeleteMapping(value = "/courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> removeStudentFromExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin) {
        log.debug("REST request to remove {} as student from exam : {}", studentLogin, examId);

        Optional<ResponseEntity<Void>> courseAndExamAccessFailure = examAccessService.checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure.get();
        }

        var exam = examService.findOneWithRegisteredUsers(examId);
        Optional<User> student = userService.getUserWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (student.isEmpty()) {
            return notFound();
        }
        exam.removeUser(student.get());
        // Note: we intentionally do not remove the user from the course, because the student might just have "deregistered" from the exam, but should
        // still have access to the course.
        examRepository.save(exam);
        return ResponseEntity.ok().body(null);
    }
}

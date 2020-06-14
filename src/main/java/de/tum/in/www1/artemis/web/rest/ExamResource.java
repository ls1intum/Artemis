package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ExamService;
import de.tum.in.www1.artemis.service.UserService;
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

    private final ExamService examService;

    private final ExamRepository examRepository;

    private final AuthorizationCheckService authCheckService;

    public ExamResource(UserService userService, CourseService courseService, ExamService examService, ExamRepository examRepository, AuthorizationCheckService authCheckService) {
        this.userService = userService;
        this.courseService = courseService;
        this.examService = examService;
        this.examRepository = examRepository;
        this.authCheckService = authCheckService;
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

        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        Exam result = examService.save(exam);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * PUT /courses/{courseId}/exams : Updates an existing exam.
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

        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = courseService.findOne(courseId);
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        Optional<Exam> existingExam = examRepository.findById(updatedExam.getId());
        if (existingExam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Exam result = examService.save(updatedExam);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    /**
     * Post /courses/:courseId/exams/:examId/students/:studentLogin : Add one single given user (based on the login) to the students of the exam so that the student can access the exam
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param studentLogin the login of the user who should get student access
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/students/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> addStudentToCourse(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable String studentLogin) {
        log.debug("REST request to add {} as student to exam : {}", studentLogin, examId);
        var course = courseService.findOne(courseId);
        var instructorOrAdmin = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, instructorOrAdmin)) {
            return forbidden();
        }
        var exam = examService.findOneWithRegisteredUsers(examId);
        if (!course.equals(exam.getCourse())) {
            return forbidden();
        }
        Optional<User> student = userService.getUserWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (student.isEmpty()) {
            return notFound();
        }
        exam.addUser(student.get());
        examRepository.save(exam);
        return ResponseEntity.ok().body(null);
    }

    /**
     * Post /courses/:courseId/exams/:examId/students : Add multiple users to the students of the exam so that they can access the exam
     * The passed list of UserDTOs must include the registration number (the other entries are currently ignored and can be left out)
     * Note: registration based on other user attributes (e.g. email, name, login) is currently NOT supported
     *
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param students     the list of students (with at least registration number) who should get access to the exam
     * @return             the list of students who could not be registered for the exam, because they could NOT be found in the Artemis database and could NOT be found in the TUM LDAP
     */
    @PostMapping(value = "/courses/{courseId}/exams/{examId}/students")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<StudentDTO>> addStudentToCourse(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody List<StudentDTO> students) {
        log.debug("REST request to add {} as students to exam : {}", students, examId);
        var course = courseService.findOne(courseId);
        var instructorOrAdmin = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, instructorOrAdmin)) {
            return forbidden();
        }
        var exam = examService.findOneWithRegisteredUsers(examId);
        if (!course.equals(exam.getCourse())) {
            return forbidden();
        }
        List<StudentDTO> notFoundStudents = new ArrayList<>();
        for (var student : students) {
            var registrationNumber = student.getRegistrationNumber();
            // 1) we use the registration number and try to find the student in the Artemis user database
            Optional<User> user = userService.findUserWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
            if (user.isPresent()) {
                exam.addUser(user.get());
                // we only need to add the user to the course group, if the student is not yet part of it, otherwise the student cannot access the exam (within the course)
                if (!user.get().getGroups().contains(course.getStudentGroupName())) {
                    userService.addUserToGroup(user.get(), course.getStudentGroupName());
                }
                continue;
            }
            // 2) if we cannot find the user, we use the registration number and try to find the student in the (TUM) LDAP
            user = userService.createUserFromLdap(registrationNumber);
            if (user.isPresent()) {
                exam.addUser(user.get());
                // the newly created user needs to get the rights to access the course, otherwise the student cannot access the exam (within the course)
                userService.addUserToGroup(user.get(), course.getStudentGroupName());
                continue;
            }
            // 3) if we cannot find the user in the (TUM) LDAP, we report this to the client
            log.warn("User with registration number " + registrationNumber + " not found in Artemis user database and not found in (TUM) LDAP");
            notFoundStudents.add(student);
        }
        examRepository.save(exam);
        return ResponseEntity.ok().body(notFoundStudents);
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
        var course = courseService.findOne(courseId);
        var instructorOrAdmin = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, instructorOrAdmin)) {
            return forbidden();
        }
        var exam = examService.findOneWithRegisteredUsers(examId);
        if (!course.equals(exam.getCourse())) {
            return forbidden();
        }
        Optional<User> student = userService.getUserWithGroupsAndAuthoritiesByLogin(studentLogin);
        if (student.isEmpty()) {
            return notFound();
        }
        exam.removeUser(student.get());
        examRepository.save(exam);
        return ResponseEntity.ok().body(null);
    }
}

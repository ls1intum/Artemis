package de.tum.in.www1.artemis.service.exam;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service implementation to check exam access.
 */
@Service
public class ExamAccessService {

    private static final String ENTITY_NAME = "Exam";

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final CourseRepository courseRepository;

    private final ExamRegistrationService examRegistrationService;

    private final StudentExamService studentExamService;

    public ExamAccessService(ExamRepository examRepository, StudentExamRepository studentExamRepository, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, CourseRepository courseRepository, ExamRegistrationService examRegistrationService, StudentExamService studentExamService) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.examRegistrationService = examRegistrationService;
        this.studentExamService = studentExamService;
    }

    /**
     * Checks if the current user is allowed to see the requested exam. If he is allowed the exam will be returned.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     * @return a ResponseEntity with the exam
     */
    public StudentExam getExamInCourseElseThrow(Long courseId, Long examId) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();

        // Check that the current user is at least student in the course.
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);

        // Check that the exam exists
        Optional<StudentExam> studentExam = studentExamRepository.findByExamIdAndUserId(examId, currentUser.getId());
        if (studentExam.isEmpty()) {
            throw new EntityNotFoundException(ENTITY_NAME, examId);
        }

        Exam exam = studentExam.get().getExam();
        checkExamBelongsToCourseElseThrow(courseId, exam);

        // Check that the current user is registered for the exam
        if (!examRepository.isUserRegisteredForExam(examId, currentUser.getId())) {
            throw new AccessForbiddenException(ENTITY_NAME, examId);
        }

        // Check that the exam is visible
        if (exam.getVisibleDate() != null && exam.getVisibleDate().isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException(ENTITY_NAME, examId);
        }

        return studentExam.get();
    }

    /**
     * Retrieves a specified studentExam from the database and sends it to the client
     * @param courseId the course to which the exam belongs
     * @param examId the examId of the exam we are interested in
     * @param studentExamId the id of the studentExam we are interested in
     * @return a StudentExam without Exercises
     */
    public StudentExam getStudentExamForTestExamElseThrow(Long courseId, Long examId, Long studentExamId) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();

        // Check that the current user is at least student in the course.
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);

        StudentExam studentExam = studentExamRepository.findByIdElseThrow(studentExamId);

        Exam exam = studentExam.getExam();

        if (!examId.equals(exam.getId())) {
            throw new AccessForbiddenException("The provided examId does not match with the examId of the studentExam");
        }

        if (!exam.isTestExam()) {
            throw new AccessForbiddenException("The requested exam is no TestExam");
        }
        // For the start of the exam, the exercises are not needed. They are later loaded via StudentExamResource
        studentExam.setExercises(null);

        checkStudentAccessToExamAndExamIsVisible(course, currentUser, exam);

        return studentExam;
    }

    /**
     * Generates a new TestExam for the specified student
     *
     * @param courseId the courseId of the corresponding course
     * @param examId   the examId for which the StudentExam should be fetched / created
     * @return a StudentExam for the student and exam
     */
    public StudentExam generateTestExamElseThrow(Long courseId, Long examId) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();

        // Check that the current user is at least student in the course.
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);

        StudentExam studentExam = studentExamService.generateTestExam(examId, currentUser);
        // For the start of the exam, the exercises are not needed. They are later loaded via StudentExamResource
        studentExam.setExercises(null);

        checkStudentAccessToExamAndExamIsVisible(course, currentUser, studentExam.getExam());

        return studentExam;
    }

    /**
     * Helper-method to check, if the course and exam belong together, if the student has access to the exam or need
     * to self-register him first and if the exam is already visible
     *
     * @param course      the course linked to the exam and studentExam
     * @param currentUser the user for which the exam should be retrieved
     * @param exam        the exam linked to the studentExam
     */
    private void checkStudentAccessToExamAndExamIsVisible(Course course, User currentUser, Exam exam) {
        checkExamBelongsToCourseElseThrow(course.getId(), exam);

        // Check that the current user is registered for the TestExam. Otherwise, the student can self-register.
        if (!examRepository.isUserRegisteredForExam(exam.getId(), currentUser.getId())) {
            examRegistrationService.selfRegisterToTestExam(course, exam.getId());
        }

        // Check that the exam is visible
        if (exam.getVisibleDate() != null && exam.getVisibleDate().isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException(ENTITY_NAME, exam.getId());
        }
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course.
     *
     * @param courseId The id of the course
     */
    public void checkCourseAccessForEditorElseThrow(Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastEditorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to manage exams in this course!");
        }
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course.
     *
     * @param courseId The id of the course
     */
    public void checkCourseAccessForInstructorElseThrow(Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to manage exams in this course!");
        }
    }

    /**
     * Checks if the current user is allowed to access the exam as teaching assistant.
     *
     * @param courseId The id of the course
     */
    public void checkCourseAccessForTeachingAssistantElseThrow(Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to access exams in this course!");
        }
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     */
    public void checkCourseAndExamAccessForEditorElseThrow(Long courseId, Long examId) {
        checkCourseAccessForEditorElseThrow(courseId);
        checkExamBelongsToCourseElseThrow(courseId, examId);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     */
    public void checkCourseAndExamAccessForInstructorElseThrow(Long courseId, Long examId) {
        checkCourseAccessForInstructorElseThrow(courseId);
        checkExamBelongsToCourseElseThrow(courseId, examId);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param exam     The exam
     */
    public void checkCourseAndExamAccessForInstructorElseThrow(Long courseId, Exam exam) {
        checkCourseAccessForInstructorElseThrow(courseId);
        checkExamBelongsToCourseElseThrow(courseId, exam);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     */
    public void checkCourseAndExamAccessForTeachingAssistantElseThrow(Long courseId, Long examId) {
        checkCourseAccessForTeachingAssistantElseThrow(courseId);
        checkExamBelongsToCourseElseThrow(courseId, examId);
    }

    private void checkExamBelongsToCourseElseThrow(Long courseId, Long examId) {
        Optional<Exam> exam = examRepository.findById(examId);
        if (exam.isEmpty()) {
            throw new EntityNotFoundException(ENTITY_NAME, examId);
        }
        else {
            checkExamBelongsToCourseElseThrow(courseId, exam.get());
        }
    }

    private void checkExamBelongsToCourseElseThrow(Long courseId, Exam exam) {
        if (!exam.getCourse().getId().equals(courseId)) {
            throw new ConflictException("Exercise course id does not match the stored course id", ENTITY_NAME, "cannotChangeCourseId");
        }
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists,
     * that the exam belongs to the given course and the exercise group belongs to the given exam.
     *
     * @param role          The role of the callee
     * @param courseId      The id of the course
     * @param examId        The id of the exam
     * @param exerciseGroup The exercise group
     */
    public void checkCourseAndExamAndExerciseGroupAccessElseThrow(Role role, Long courseId, Long examId, ExerciseGroup exerciseGroup) {
        switch (role) {
            case INSTRUCTOR -> checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
            case EDITOR -> checkCourseAndExamAccessForEditorElseThrow(courseId, examId);
            default -> throw new AccessForbiddenException(ENTITY_NAME, examId);
        }

        Exam exam = exerciseGroup.getExam();
        if (exam == null || !exam.getId().equals(examId)) {
            throw new ConflictException("Invalid exam id", ENTITY_NAME, "noIdMatch");
        }
        checkExamBelongsToCourseElseThrow(courseId, exam);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists,
     * that the exam belongs to the given course and the student exam belongs to the given exam.
     *
     * @param courseId      The id of the course
     * @param examId        The id of the exam
     * @param studentExamId The if of the student exam
     */
    public void checkCourseAndExamAndStudentExamAccessElseThrow(Long courseId, Long examId, Long studentExamId) {
        checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Optional<StudentExam> studentExam = studentExamRepository.findById(studentExamId);
        if (studentExam.isEmpty()) {
            throw new EntityNotFoundException(ENTITY_NAME, examId);
        }
        if (!studentExam.get().getExam().getId().equals(examId)) {
            throw new ConflictException("Invalid exam id", ENTITY_NAME, "noIdMatch");
        }
    }
}

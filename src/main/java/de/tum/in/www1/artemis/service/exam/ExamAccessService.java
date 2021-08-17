package de.tum.in.www1.artemis.service.exam;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;

import javax.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
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
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service implementation to check exam access.
 */
@Service
public class ExamAccessService {

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final ExamRepository examRepository;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final StudentExamRepository studentExamRepository;

    private final CourseRepository courseRepository;

    public ExamAccessService(ExamRepository examRepository, ExerciseGroupRepository exerciseGroupRepository, StudentExamRepository studentExamRepository,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository) {
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.studentExamRepository = studentExamRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Checks if the current user is allowed to see the requested exam. If he is allowed the exam will be returned.
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     * @return a ResponseEntity with the exam
     */
    public ResponseEntity<StudentExam> checkAndGetCourseAndExamAccessForConduction(Long courseId, Long examId) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();

        // Check that the current user is at least student in the course.
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, currentUser);

        // Check that the exam exists
        StudentExam studentExam = studentExamRepository.findByExamIdAndUserIdElseThrow(examId, currentUser.getId());
        Exam exam = studentExam.getExam();

        // Check that the exam belongs to the course
        if (!exam.getCourse().getId().equals(courseId)) {
            return badRequest("courseId", "400", "courseId doesn't match the id of the exam with examId " + examId + "!");
        }

        // Check that the current user is registered for the exam
        if (!examRepository.isUserRegisteredForExam(examId, currentUser.getId())) {
            return forbidden();
        }

        // Check that the exam is visible
        if (exam.getVisibleDate() != null && exam.getVisibleDate().isAfter(ZonedDateTime.now())) {
            return badRequest("exam.visibleDate", "400", "Can't access the exam before it is visible!");
        }

        return ResponseEntity.ok(studentExam);
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, otherwise throws an AccessForbiddenException
     *
     * @param role the role to check the access for in the course
     * @param courseId The id of the course
     * @throws AccessForbiddenException if user isn't instructor in course
     */
    @NotNull
    public void checkCourseAccessForRoleElseThrow(Role role, Long courseId) throws AccessForbiddenException {
        Course course = courseRepository.findByIdElseThrow(courseId);
        switch (role) {
            case ADMIN:
                authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.ADMIN, course, null);
                break;
            case INSTRUCTOR:
                authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
                break;
            case EDITOR:
                authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);
                break;
            case TEACHING_ASSISTANT:
                authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
                break;
            case STUDENT:
                authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
                break;
            default:
                break;
        }
    }

    public void checkCourseAndExamAccessForRoleElseThrow(Role role, Long courseId, Long examId) {
        Exam exam = examRepository.findByIdElseThrow(examId);
        checkCourseAndExamAccessForRoleElseThrow(role, courseId, exam);
    }

    public void checkCourseAndExamAccessForRoleElseThrow(Role role, Long courseId, Exam exam) {
        checkCourseAccessForRoleElseThrow(role, courseId);
        if (!exam.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("CourseId", "400", "CourseId of the exam doesn't match the courseId in the path!");
        }
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists,
     * that the exam belongs to the given course and the exercise group belongs to the given exam.
     *
     * @param role            The role of the callee
     * @param courseId        The id of the course
     * @param examId          The id of the exam
     * @param exerciseGroup   The exercise group
     */
    public void checkCourseAndExamAndExerciseGroupAccessForRoleElseThrow(Role role, Long courseId, Long examId, ExerciseGroup exerciseGroup) {
        switch (role) {
            case ADMIN -> checkCourseAndExamAccessForRoleElseThrow(Role.ADMIN, courseId, examId);
            case INSTRUCTOR -> checkCourseAndExamAccessForRoleElseThrow(Role.INSTRUCTOR, courseId, examId);
            case EDITOR -> checkCourseAndExamAccessForRoleElseThrow(Role.EDITOR, courseId, examId);
            case TEACHING_ASSISTANT -> checkCourseAndExamAccessForRoleElseThrow(Role.TEACHING_ASSISTANT, courseId, examId);
            case STUDENT -> checkCourseAndExamAccessForRoleElseThrow(Role.STUDENT, courseId, examId);
            case ANONYMOUS -> checkCourseAndExamAccessForRoleElseThrow(Role.ANONYMOUS, courseId, examId);
        }
        ;
        Exam exam = exerciseGroup.getExam();
        if (exam == null || !exam.getId().equals(examId) || !exam.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("exam", "400", "The exam in the path doesnt match the Exam to the ExerciseGroup in the body!");
        }
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
        checkCourseAndExamAccessForRoleElseThrow(Role.INSTRUCTOR, courseId, examId);
        StudentExam studentExam = studentExamRepository.findByIdElseThrow(studentExamId);
        if (!studentExam.getExam().getId().equals(examId)) {
            throw new BadRequestAlertException("examId", "400", "Id of the exam referenced by examId doesnt match the one referenced by studentExamId!");
        }
    }
}

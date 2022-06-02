package de.tum.in.www1.artemis.service.exam;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * Service implementation to check student exam access.
 */
@Service
public class StudentExamAccessService {

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    public StudentExamAccessService(CourseRepository courseRepository, UserRepository userRepository, AuthorizationCheckService authorizationCheckService,
            ExamRepository examRepository, StudentExamRepository studentExamRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
    }

    /**
     * Checks if the current user is allowed to see the requested student exam.
     *
     * @param courseId      the if of the course
     * @param examId        the id of the exam
     * @param studentExamId the id of the student exam
     * @param isTestRun     flag to determine if it is a test run or not
     */
    public void checkStudentExamAccessElseThrow(Long courseId, Long examId, Long studentExamId, boolean isTestRun) {
        checkStudentExamAccessElseThrow(courseId, examId, studentExamId, userRepository.getUserWithGroupsAndAuthorities(), isTestRun);
    }

    /**
     * Checks if the current user is allowed to see the requested student exam.
     *
     * @param courseId      the if of the course
     * @param examId        the id of the exam
     * @param studentExamId the id of the student exam
     * @param currentUser   the current user
     * @param isTestRun     flag to determine if this is a test run or not
     */
    public void checkStudentExamAccessElseThrow(Long courseId, Long examId, Long studentExamId, User currentUser, boolean isTestRun) {
        checkCourseAndExamAccessElseThrow(courseId, examId, currentUser, isTestRun);

        // Check that the student exam exists
        StudentExam studentExam = studentExamRepository.findByIdElseThrow(studentExamId);

        // Check that the examId equals the id of the exam of the student exam
        if (!studentExam.getExam().getId().equals(examId)) {
            throw new ConflictException("The student exam does not belong to the exam", "StudentExam", "studentExamExamConflict");
        }

        // Check that the student of the required student exam (from the database) is the current user
        if (!studentExam.getUser().equals(currentUser)) {
            throw new AccessForbiddenException();
        }
    }

    /**
     * Checks if the current user is allowed to access the requested exam.
     *
     * @param courseId      the if of the course
     * @param examId        the id of the exam
     * @param currentUser   the user
     * @param isTestRun     flag to determine if this is a testRun
     */
    public void checkCourseAndExamAccessElseThrow(Long courseId, Long examId, User currentUser, boolean isTestRun) {
        // Check that the exam exists
        Exam exam = examRepository.findByIdElseThrow(examId);

        // Check that the exam belongs to the course
        if (!exam.getCourse().getId().equals(courseId)) {
            throw new ConflictException("The exam does not belong to the course", "Exam", "examCourseConflict");
        }

        Course course = courseRepository.findByIdElseThrow(courseId);
        if (isTestRun) {
            // Check that the current user is at least instructor in the course.
            if (!authorizationCheckService.isAtLeastInstructorInCourse(course, currentUser)) {
                throw new AccessForbiddenException();
            }
        }
        else {
            // Check that the current user is at least student in the course.
            if (!authorizationCheckService.isAtLeastStudentInCourse(course, currentUser)) {
                throw new AccessForbiddenException();
            }

            // Check that the exam is already visible. After the exam, we directly show the summary!
            if (exam.getVisibleDate() != null && (exam.getVisibleDate().isAfter(ZonedDateTime.now()))) {
                throw new AccessForbiddenException();
            }

            // Check that the current user is registered for the exam
            if (!examRepository.isUserRegisteredForExam(examId, currentUser.getId())) {
                throw new AccessForbiddenException();
            }
        }
    }

    /**
     * Checks if the user is allowed to access the course
     * @param courseId the corresponding courseId
     * @param currentUser the user for which the access should be checked
     */
    public void checkCourseAccessForStudentElseThrow(Long courseId, User currentUser) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastStudentInCourse(course, currentUser)) {
            throw new AccessForbiddenException("You are not allowed to access exams in this course!");
        }
    }
}

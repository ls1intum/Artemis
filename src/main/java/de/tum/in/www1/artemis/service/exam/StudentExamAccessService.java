package de.tum.in.www1.artemis.service.exam;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

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
     * @param <T>           The type of the return type of the requesting route so that the
     *                      response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <T> Optional<ResponseEntity<T>> checkStudentExamAccess(Long courseId, Long examId, Long studentExamId, boolean isTestRun) {
        User currentUser = userRepository.getUserWithGroupsAndAuthorities();
        return checkStudentExamAccess(courseId, examId, studentExamId, currentUser, isTestRun);
    }

    /**
     * Checks if the current user is allowed to see the requested student exam.
     *
     * @param courseId      the if of the course
     * @param examId        the id of the exam
     * @param studentExamId the id of the student exam
     * @param currentUser   the current user
     * @param isTestRun     flag to determine if this is a test run or not
     * @param <T>           The type of the return type of the requesting route so that the
     *                      response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <T> Optional<ResponseEntity<T>> checkStudentExamAccess(Long courseId, Long examId, Long studentExamId, User currentUser, boolean isTestRun) {
        Optional<ResponseEntity<T>> courseAndExamAccessFailure = checkCourseAndExamAccess(courseId, examId, currentUser, isTestRun);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure;
        }

        // Check that the student exam exists
        Optional<StudentExam> studentExam = studentExamRepository.findById(studentExamId);
        if (studentExam.isEmpty()) {
            return Optional.of(notFound());
        }

        // Check that the examId equals the id of the exam of the student exam
        if (!studentExam.get().getExam().getId().equals(examId)) {
            return Optional.of(conflict());
        }

        // Check that the student of the required student exam is the current user
        if (!studentExam.get().getUser().equals(currentUser)) {
            return Optional.of(forbidden());
        }

        return Optional.empty();
    }

    /**
     * Checks if the current user is allowed to access the requested exam.
     *
     * @param courseId      the if of the course
     * @param examId        the id of the exam
     * @param currentUser   the user
     * @param isTestRun       flag to determine if this is a testRun
     * @param <T>           The type of the return type of the requesting route so that the
     *                      response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <T> Optional<ResponseEntity<T>> checkCourseAndExamAccess(Long courseId, Long examId, User currentUser, boolean isTestRun) {
        // Check that the exam exists
        Optional<Exam> exam = examRepository.findById(examId);
        if (exam.isEmpty()) {
            return Optional.of(notFound());
        }

        // Check that the exam belongs to the course
        if (!exam.get().getCourse().getId().equals(courseId)) {
            return Optional.of(conflict());
        }

        Course course = courseRepository.findByIdElseThrow(courseId);
        if (isTestRun) {
            // Check that the current user is at least instructor in the course.
            if (!authorizationCheckService.isAtLeastInstructorInCourse(course, currentUser)) {
                return Optional.of(forbidden());
            }
        }
        else {
            // Check that the current user is at least student in the course.
            if (!authorizationCheckService.isAtLeastStudentInCourse(course, currentUser)) {
                return Optional.of(forbidden());
            }

            // Check that the exam is already visible. After the exam, we directly show the summary!
            if (exam.get().getVisibleDate() != null && (exam.get().getVisibleDate().isAfter(ZonedDateTime.now()))) {
                return Optional.of(forbidden());
            }

            // Check that the current user is registered for the exam
            if (!examRepository.isUserRegisteredForExam(examId, currentUser.getId())) {
                return Optional.of(forbidden());
            }
        }

        return Optional.empty();
    }
}

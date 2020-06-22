package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseGroupRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;

/**
 * Service implementation to check exam access.
 */
@Service
public class ExamAccessService {

    private final ExamRepository examRepository;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final StudentExamRepository studentExamRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authorizationCheckService;

    public ExamAccessService(ExamRepository examRepository, ExerciseGroupRepository exerciseGroupRepository, StudentExamRepository studentExamRepository,
            CourseService courseService, AuthorizationCheckService authorizationCheckService) {
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.studentExamRepository = studentExamRepository;
        this.courseService = courseService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course
     *
     * @param courseId  The id of the course
     * @param <T>       The type of the return type of the requesting route so that the response can be returned there
     * @return an optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <T> Optional<ResponseEntity<T>> checkCourseAccess(Long courseId) {
        Course course = courseService.findOne(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return Optional.of(forbidden());
        }
        return Optional.empty();
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists and that the exam
     * belongs to the given course.
     *
     * @param courseId  The id of the course
     * @param examId    The id of the exam
     * @param <X>       The type of the return type of the requesting route so that the response can be returned there
     * @return an optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <X> Optional<ResponseEntity<X>> checkCourseAndExamAccess(Long courseId, Long examId) {
        Optional<ResponseEntity<X>> courseAccessFailure = checkCourseAccess(courseId);
        if (courseAccessFailure.isPresent()) {
            return courseAccessFailure;
        }
        Optional<Exam> exam = examRepository.findById(examId);
        if (exam.isEmpty()) {
            return Optional.of(notFound());
        }
        if (!exam.get().getCourse().getId().equals(courseId)) {
            return Optional.of(conflict());
        }
        return Optional.empty();
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists,
     * that the exam belongs to the given course and the exercise group belongs to the given exam.
     *
     * @param courseId          The id of the course
     * @param examId            The id of the exam
     * @param exerciseGroupId   The id of the exercise group
     * @param <X>               The type of the return type of the requesting route so that the
     *                          response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <X> Optional<ResponseEntity<X>> checkCourseAndExamAndExerciseGroupAccess(Long courseId, Long examId, Long exerciseGroupId) {
        Optional<ResponseEntity<X>> courseAndExamAccessFailure = checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure;
        }
        Optional<ExerciseGroup> exerciseGroup = exerciseGroupRepository.findById(exerciseGroupId);
        if (exerciseGroup.isEmpty()) {
            return Optional.of(notFound());
        }
        if (!exerciseGroup.get().getExam().getId().equals(examId)) {
            return Optional.of(conflict());
        }
        return Optional.empty();
    }

    /**
     * Checks if the current user is allowed to manage exams of the given course, that the exam exists,
     * that the exam belongs to the given course and the student exam belongs to the given exam.
     *
     * @param courseId      The id of the course
     * @param examId        The id of the exam
     * @param studentExamId The if of the student exam
     * @param <X>           The type of the return type of the requesting route so that the
     *      *               response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <X> Optional<ResponseEntity<X>> checkCourseAndExamAndStudentExamAccess(Long courseId, Long examId, Long studentExamId) {
        Optional<ResponseEntity<X>> courseAndExamAccessFailure = checkCourseAndExamAccess(courseId, examId);
        if (courseAndExamAccessFailure.isPresent()) {
            return courseAndExamAccessFailure;
        }
        Optional<StudentExam> studentExam = studentExamRepository.findById(studentExamId);
        if (studentExam.isEmpty()) {
            return Optional.of(notFound());
        }
        if (!studentExam.get().getExam().getId().equals(examId)) {
            return Optional.of(conflict());
        }
        return Optional.empty();
    }
}

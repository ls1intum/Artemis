package de.tum.cit.aet.artemis.exam.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class StudentExamApi extends AbstractExamApi {

    private final StudentExamRepository studentExamRepository;

    public StudentExamApi(StudentExamRepository studentExamRepository) {
        this.studentExamRepository = studentExamRepository;
    }

    public Optional<StudentExam> findByExerciseIdAndUserId(Long exerciseId, Long userId) {
        return studentExamRepository.findByExerciseIdAndUserId(exerciseId, userId);
    }

    public Optional<StudentExam> findStudentExam(Exercise exercise, StudentParticipation participation) {
        return studentExamRepository.findStudentExam(exercise, participation);
    }

    /**
     * Whether the student may already see the result of the given exam participation, i.e. the exam is running for a programming exercise or the results have been published. This
     * delegates to the static {@link ExamService#shouldStudentSeeResult} and therefore adds no bean dependency, so callers that already depend on this API do not pull the exam
     * service into their startup dependency chain.
     *
     * @param studentExam   the student exam the participation belongs to
     * @param participation the student's participation
     * @return true if the student may see the result
     */
    public boolean shouldStudentSeeResult(StudentExam studentExam, StudentParticipation participation) {
        return ExamService.shouldStudentSeeResult(studentExam, participation);
    }

    public Set<StudentExam> findAllWithExercisesByExamId(long examId) {
        return studentExamRepository.findAllWithExercisesByExamId(examId);
    }

    public Optional<StudentExam> findByExamIdAndParticipationId(long examId, long participationId) {
        return studentExamRepository.findByExamIdAndParticipationId(examId, participationId);
    }

    public Optional<Boolean> isSubmitted(long examId, long userId) {
        return studentExamRepository.isSubmitted(examId, userId);
    }

    public Optional<Boolean> isSubmitted(long participationId) {
        return studentExamRepository.isSubmitted(participationId);
    }

    public void save(StudentExam studentExam) {
        studentExamRepository.save(studentExam);
    }

    /**
     * Finds all student exams for a user grouped by course.
     * This uses a simplified query that only fetches basic exercise data.
     * Participation data (submissions, results, feedbacks) should be fetched separately.
     *
     * @param userId the id of the user
     * @return student exams grouped by course
     */
    public Map<Course, List<StudentExam>> findStudentExamsByCourseForUserId(long userId) {
        return studentExamRepository.findAllWithExercisesByUserId(userId).stream().collect(Collectors.groupingBy(studentExam -> studentExam.getExam().getCourse()));
    }

    /**
     * Finds which of the given users have a working period in the exam. That is the case when the user has a student exam of their own (the newest one for test exams);
     * instructor test runs are ignored.
     *
     * @param examId  the id of the exam
     * @param userIds the ids of the users to check
     * @return the ids of the given users that have a student exam to read the working period from
     */
    public Set<Long> findUserIdsWithWorkingPeriod(long examId, Collection<Long> userIds) {
        return studentExamRepository.findUserIdsWithStudentExamByExamIdAndUserIdIn(examId, userIds);
    }

    /**
     * Finds which of the given users have the exercise in their newest student exam of the exam; instructor test runs are ignored. A result for an exercise that the
     * student exam does not contain would not be found by the exam result calculation.
     *
     * @param examId     the id of the exam
     * @param exerciseId the id of the exercise
     * @param userIds    the ids of the users to check
     * @return the ids of the given users whose newest student exam contains the exercise
     */
    public Set<Long> findUserIdsWithExerciseInNewestStudentExam(long examId, long exerciseId, Collection<Long> userIds) {
        return studentExamRepository.findUserIdsWithExerciseInNewestStudentExam(examId, exerciseId, userIds);
    }

    public Set<Long> findRegisteredNonTestExamIdsByUserIdAndCourseIds(long userId, Collection<Long> courseIds) {
        return studentExamRepository.findRegisteredNonTestExamIdsByUserIdAndCourseIds(userId, courseIds);
    }

    public Set<Long> findAssignedExamExerciseIdsByUserIdAndCourseIds(long userId, Collection<Long> courseIds) {
        return studentExamRepository.findAssignedExamExerciseIdsByUserIdAndCourseIds(userId, courseIds);
    }
}

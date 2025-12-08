package de.tum.cit.aet.artemis.exam.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
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

    public Map<Course, List<StudentExam>> findStudentExamsByCourseForUserId(long userId) {
        return studentExamRepository.findAllWithExercisesSubmissionPolicyParticipationsSubmissionsResultsAndFeedbacksByUserId(userId).stream()
                .collect(Collectors.groupingBy(studentExam -> studentExam.getExam().getCourse()));
    }

    public Optional<StudentExam> findByExamIdAndUserId(Long examId, Long userId) {
        return studentExamRepository.findByExamIdAndUserId(examId, userId);
    }
}

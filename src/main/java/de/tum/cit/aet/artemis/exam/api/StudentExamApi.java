package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Profile(PROFILE_CORE)
@Controller
public class StudentExamApi extends AbstractExamApi {

    private final StudentExamRepository studentExamRepository;

    public StudentExamApi(StudentExamRepository studentExamRepository) {
        this.studentExamRepository = studentExamRepository;
    }

    public Optional<StudentExam> findWithExercisesParticipationsSubmissionsById(long studentExamId, boolean isTestRun) {
        return studentExamRepository.findWithExercisesParticipationsSubmissionsById(studentExamId, isTestRun);
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

    public Optional<StudentExam> findByExamIdAndUserId(long examId, long userId) {
        return studentExamRepository.findByExamIdAndUserId(examId, userId);
    }

    public void save(StudentExam studentExam) {
        studentExamRepository.save(studentExam);
    }

    public long countByExamId(long examId) {
        return studentExamRepository.countByExamId(examId);
    }

    public Map<Course, List<StudentExam>> findStudentExamsByCourseForUserId(long userId) {
        return studentExamRepository.findAllWithExercisesSubmissionPolicyParticipationsSubmissionsResultsAndFeedbacksByUserId(userId).stream()
                .collect(Collectors.groupingBy(studentExam -> studentExam.getExam().getCourse()));
    }
}

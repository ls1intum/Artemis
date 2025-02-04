package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Profile(PROFILE_CORE)
@Controller
public class ExamApi extends AbstractExamApi {

    private final ExamService examService;

    private final ExamRepository examRepository;

    public ExamApi(@Lazy ExamService examService, ExamRepository examRepository) {
        this.examService = examService;
        this.examRepository = examRepository;
    }

    public StudentExamWithGradeDTO getStudentExamGradeForDataExport(StudentExam studentExam) {
        return examService.getStudentExamGradeForDataExport(studentExam);
    }

    public boolean shouldStudentSeeResult(StudentExam studentExam, StudentParticipation participation) {
        return ExamService.shouldStudentSeeResult(studentExam, participation);
    }

    public Exam findByIdElseThrow(long id) {
        return examRepository.findByIdElseThrow(id);
    }

    public Set<Exam> findActiveExams(Set<Long> courseIds, long userId, ZonedDateTime visible, ZonedDateTime end) {
        return examRepository.findActiveExams(courseIds, userId, visible, end);
    }

    public List<Exam> findByCourseId(long courseId) {
        return examRepository.findByCourseId(courseId);
    }

    public Set<Exam> findByCourseIdForUser(Long courseId, long userId, Set<String> groupNames, ZonedDateTime now) {
        return examRepository.findByCourseIdForUser(courseId, userId, groupNames, now);
    }

    public Optional<Exam> findWithExerciseGroupsExercisesParticipationsAndSubmissionsById(long examId) {
        return examRepository.findWithExerciseGroupsExercisesParticipationsAndSubmissionsById(examId);
    }

    public Exam findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(long examId) {
        return examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);
    }

    public void save(Exam exam) {
        examRepository.save(exam);
    }

    public Set<Exercise> findAllExercisesWithDetailsByExamId(long examId) {
        return examRepository.findAllExercisesWithDetailsByExamId(examId);
    }

    public Set<Exam> filterVisibleExams(Set<Exam> exams) {
        return examRepository.filterVisibleExams(exams);
    }

    public Set<Exercise> getExercisesByCourseId(long courseId) {
        return examRepository.findByCourseIdWithExerciseGroupsAndExercises(courseId).stream().flatMap(e -> e.getExerciseGroups().stream()).filter(Objects::nonNull)
                .map(ExerciseGroup::getExercises).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}

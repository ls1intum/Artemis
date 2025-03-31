package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Profile(PROFILE_CORE)
@Controller
public class ExamRepositoryApi extends AbstractExamApi {

    private final ExamRepository examRepository;

    public ExamRepositoryApi(ExamRepository examRepository) {
        this.examRepository = examRepository;
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

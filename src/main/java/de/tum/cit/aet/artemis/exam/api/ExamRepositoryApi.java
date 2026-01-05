package de.tum.cit.aet.artemis.exam.api;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamDeletionInfoDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Conditional(ExamEnabled.class)
@Controller
@Lazy
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

    public Set<Exam> findAllVisibleByCourseId(long courseId, ZonedDateTime now) {
        return examRepository.findAllVisibleByCourseId(courseId, now);
    }

    /**
     * Finds all exam IDs for a given course.
     *
     * @param courseId the ID of the course
     * @return set of exam IDs
     */
    public Set<Long> findExamIdsByCourseId(long courseId) {
        return examRepository.findExamIdsByCourseId(courseId);
    }

    /**
     * Finds all exercise IDs for a given exam.
     *
     * @param examId the ID of the exam
     * @return set of exercise IDs
     */
    public Set<Long> findExerciseIdsByExamId(Long examId) {
        return examRepository.findExerciseIdsByExamId(examId);
    }

    /**
     * Counts the number of student exams for a given exam.
     * This is used for calculating weighted progress during course deletion/reset.
     *
     * @param examId the ID of the exam
     * @return the number of student exams
     */
    public long countStudentExamsByExamId(long examId) {
        return examRepository.countStudentExamsByExamId(examId);
    }

    /**
     * Counts the number of programming exercises in an exam.
     * This is used for calculating weighted progress during course deletion/reset,
     * as programming exercises require repository deletion per student.
     *
     * @param examId the ID of the exam
     * @return the number of programming exercises in the exam
     */
    public long countProgrammingExercisesByExamId(long examId) {
        return examRepository.countProgrammingExercisesByExamId(examId);
    }

    /**
     * Gets deletion info (student exam count, programming exercise count) for all exams in a course.
     * This is used to calculate accurate total weight for course deletion/reset progress tracking.
     *
     * @param courseId the ID of the course
     * @return list of ExamDeletionInfoDTO with counts for each exam
     */
    public List<ExamDeletionInfoDTO> findDeletionInfoByCourseId(long courseId) {
        return examRepository.findDeletionInfoByCourseId(courseId);
    }

    /**
     * Finds all exams for a course with their exercise groups and exercises.
     *
     * @param courseId the ID of the course
     * @return list of exams with exercise groups and exercises
     */
    public List<Exam> findByCourseIdWithExerciseGroupsAndExercises(long courseId) {
        return examRepository.findByCourseIdWithExerciseGroupsAndExercises(courseId);
    }
}

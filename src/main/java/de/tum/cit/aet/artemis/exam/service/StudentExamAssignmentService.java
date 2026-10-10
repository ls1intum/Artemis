package de.tum.cit.aet.artemis.exam.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Owns the selection-to-commit boundary that makes an exam exercise no longer authorable. */
@Service
@Lazy
@Conditional(ExamEnabled.class)
public class StudentExamAssignmentService {

    private final ExamRepository examRepository;

    private final ExamUserRepository examUserRepository;

    private final StudentExamRepository studentExamRepository;

    private final Optional<HyperionExerciseMutationApi> mutationApi;

    public StudentExamAssignmentService(ExamRepository examRepository, ExamUserRepository examUserRepository, StudentExamRepository studentExamRepository,
            Optional<HyperionExerciseMutationApi> mutationApi) {
        this.examRepository = examRepository;
        this.examUserRepository = examUserRepository;
        this.studentExamRepository = studentExamRepository;
        this.mutationApi = mutationApi;
    }

    /**
     * Assigns registered users under the exam-row lock.
     *
     * @param examId      exam to assign
     * @param onlyMissing exclude users who already have a regular student exam
     * @return newly assigned student exams
     */
    public List<StudentExam> assignRegisteredStudents(long examId, boolean onlyMissing) {
        return assign(examId, exam -> {
            Set<Long> users = new HashSet<>(examUserRepository.findUserIdsByExamId(examId));
            if (onlyMissing) {
                users.removeAll(studentExamRepository.findUserIdsWithStudentExamsForExam(examId));
            }
            return studentExamRepository.createRandomStudentExams(exam, users);
        });
    }

    /**
     * Assigns one student without racing authoring or another assignment.
     *
     * @param examId exam to assign
     * @param userId student to assign
     * @return existing regular exam or unfinished test-exam attempt, otherwise a newly assigned attempt
     */
    public StudentExam assignStudent(long examId, long userId) {
        return assign(examId, exam -> {
            if (exam.isTestExam()) {
                // The access-layer preflight can race another start. Recheck after acquiring the exam-row lock.
                List<StudentExam> unfinished = studentExamRepository.findStudentExamsForTestExamsByUserIdAndExamId(userId, examId).stream().filter(attempt -> !attempt.isFinished())
                        .toList();
                if (unfinished.size() > 1) {
                    throw new IllegalStateException("Multiple unfinished test-exam attempts exist for user " + userId + " in exam " + examId);
                }
                if (!unfinished.isEmpty()) {
                    return studentExamRepository.findByIdWithExercisesElseThrow(unfinished.getFirst().getId());
                }
            }
            else {
                var existing = studentExamRepository.findWithExercisesByUserIdAndExamId(userId, examId, false);
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
            return studentExamRepository.createRandomStudentExams(exam, Set.of(userId)).getFirst();
        });
    }

    /**
     * Freezes a test run's selected programming exercises before participations are prepared.
     *
     * @param testRun selected exercises and instructor
     * @return persisted test run
     */
    public StudentExam assignTestRun(StudentExam testRun) {
        return withReservations(testRun.getExercises(), () -> examRepository.withExerciseSelectionLock(testRun.getExam().getId(), exam -> {
            Set<Long> currentExercises = exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).map(Exercise::getId)
                    .collect(java.util.stream.Collectors.toSet());
            if (!testRun.getExercises().stream().allMatch(exercise -> currentExercises.contains(exercise.getId()))) {
                throw new IllegalStateException("The selected exercises no longer belong to this exam.");
            }
            return studentExamRepository.saveAndFlush(testRun);
        }));
    }

    private <T> T assign(long examId, Function<Exam, T> assignment) {
        List<HyperionExerciseMutationApi.ParticipationReservation> reservations = new ArrayList<>();
        try {
            return examRepository.withExerciseSelectionLock(examId, exam -> {
                reserve(exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).toList(), reservations);
                return assignment.apply(exam);
            });
        }
        finally {
            // Outside the repository proxy: keep generation excluded until commit (or rollback), not merely until saveAll returns.
            close(reservations);
        }
    }

    private <T> T withReservations(Collection<Exercise> exercises, java.util.function.Supplier<T> assignment) {
        List<HyperionExerciseMutationApi.ParticipationReservation> reservations = new ArrayList<>();
        try {
            reserve(exercises, reservations);
            return assignment.get();
        }
        finally {
            close(reservations);
        }
    }

    private void reserve(Collection<Exercise> exercises, List<HyperionExerciseMutationApi.ParticipationReservation> reservations) {
        mutationApi.ifPresent(api -> exercises.stream().filter(ProgrammingExercise.class::isInstance).map(Exercise::getId).distinct().sorted()
                .forEach(id -> reservations.add(api.reserveParticipation(id))));
    }

    private static void close(List<HyperionExerciseMutationApi.ParticipationReservation> reservations) {
        RuntimeException failure = null;
        for (var reservation : reservations.reversed()) {
            try {
                reservation.close();
            }
            catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                }
                else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}

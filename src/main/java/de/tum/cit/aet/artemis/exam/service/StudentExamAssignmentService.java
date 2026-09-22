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

/** Reserves programming exercises while student exam assignments are saved. */
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
     * Assigns registered users while programming authoring is excluded.
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
     * Assigns one student while programming authoring is excluded.
     *
     * @param examId exam to assign
     * @param userId student to assign
     * @return newly assigned student exam
     */
    public StudentExam assignStudent(long examId, long userId) {
        return assign(examId, exam -> studentExamRepository.createRandomStudentExams(exam, Set.of(userId)).getFirst());
    }

    /**
     * Freezes a test run's selected programming exercises before participations are prepared.
     *
     * @param testRun selected exercises and instructor
     * @return persisted test run
     */
    public StudentExam assignTestRun(StudentExam testRun) {
        return withReservations(testRun.getExercises(), () -> studentExamRepository.save(testRun));
    }

    private <T> T assign(long examId, Function<Exam, T> assignment) {
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
        return withReservations(exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).toList(), () -> assignment.apply(exam));
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

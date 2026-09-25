package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_EXERCISE_START_STATUS;
import static de.tum.cit.aet.artemis.exam.web.ExamWebsocketTopics.EXERCISE_START_STATUS;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.util.ExamExerciseStartPreparationStatus;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.api.dtos.ParticipationReservation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Owns student exam assignment reservations and preparation progress. */
@Service
@Lazy
@Conditional(ExamEnabled.class)
public class StudentExamPreparationService {

    private static final Logger log = LoggerFactory.getLogger(StudentExamPreparationService.class);

    private final CacheManager cacheManager;

    private final WebsocketMessagingService websocketMessagingService;

    private final ExamRepository examRepository;

    private final ExamUserRepository examUserRepository;

    private final StudentExamRepository studentExamRepository;

    private final Optional<HyperionExerciseMutationApi> mutationApi;

    public StudentExamPreparationService(ExamRepository examRepository, ExamUserRepository examUserRepository, StudentExamRepository studentExamRepository,
            Optional<HyperionExerciseMutationApi> mutationApi, CacheManager cacheManager, WebsocketMessagingService websocketMessagingService) {
        this.examRepository = examRepository;
        this.examUserRepository = examUserRepository;
        this.studentExamRepository = studentExamRepository;
        this.mutationApi = mutationApi;
        this.cacheManager = cacheManager;
        this.websocketMessagingService = websocketMessagingService;
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
        return withReservations(testRun.getExercises(), () -> examRepository.withExerciseSelectionLock(testRun.getExam().getId(), exam -> studentExamRepository.save(testRun)));
    }

    private <T> T assign(long examId, Function<Exam, T> assignment) {
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
        return withReservations(exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).toList(),
                () -> examRepository.withExerciseSelectionLock(examId, assignment));
    }

    private <T> T withReservations(Collection<Exercise> exercises, java.util.function.Supplier<T> assignment) {
        List<ParticipationReservation> reservations = new ArrayList<>();
        try {
            reserve(exercises, reservations);
            return assignment.get();
        }
        finally {
            close(reservations);
        }
    }

    private void reserve(Collection<Exercise> exercises, List<ParticipationReservation> reservations) {
        mutationApi.ifPresent(api -> exercises.stream().filter(ProgrammingExercise.class::isInstance).map(Exercise::getId).distinct().sorted()
                .forEach(id -> reservations.add(api.reserveParticipation(id))));
    }

    private static void close(List<ParticipationReservation> reservations) {
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

    void sendAndCacheExercisePreparationStatus(Long examId, int finished, int failed, int overall, int participations, ZonedDateTime startTime, ReentrantLock lock) {
        // Synchronizing and comparing to avoid race conditions here
        // Otherwise it can happen that a status with less completed exams is sent after one with a higher value
        try {
            lock.lock();
            ExamExerciseStartPreparationStatus status = null;
            var cache = cacheManager.getCache(EXAM_EXERCISE_START_STATUS);
            if (cache != null) {
                var oldValue = cache.get(examId);
                if (oldValue != null) {
                    var oldStatus = (ExamExerciseStartPreparationStatus) oldValue.get();
                    if (oldStatus != null) {
                        status = new ExamExerciseStartPreparationStatus(Math.max(finished, oldStatus.finished()), Math.max(failed, oldStatus.failed()),
                                Math.max(overall, oldStatus.overall()), Math.max(participations, oldStatus.participationCount()), startTime);
                    }
                }
                if (status == null) {
                    status = new ExamExerciseStartPreparationStatus(finished, failed, overall, participations, startTime);
                }
                cache.put(examId, status);
            }
            else {
                log.warn("Unable to add exam exercise start status to distributed cache because it is null");
            }
            websocketMessagingService.sendMessage(EXERCISE_START_STATUS.at(examId), status);
        }
        catch (Exception e) {
            log.warn("Failed to send exercise preparation status", e);
        }
        finally {
            lock.unlock();
        }
    }

    public Optional<ExamExerciseStartPreparationStatus> getExerciseStartStatusOfExam(Long examId) {
        return Optional.ofNullable(cacheManager.getCache(EXAM_EXERCISE_START_STATUS)).map(cache -> cache.get(examId))
                .map(wrapper -> (ExamExerciseStartPreparationStatus) wrapper.get());
    }

    public void invalidateExerciseStartStatus(Long examId) {
        var cache = cacheManager.getCache(EXAM_EXERCISE_START_STATUS);
        if (cache != null) {
            cache.evict(examId);
        }
    }

}

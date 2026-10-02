package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.exam.web.ExamWebsocketTopics.EXERCISE_START_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.util.ExamExerciseStartPreparationStatus;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.api.dtos.ParticipationReservation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

class StudentExamPreparationServiceTest {

    private final ExamTestRepository exams = mock();

    private final ExamUserRepository users = mock();

    private final StudentExamTestRepository studentExams = mock();

    private final HyperionExerciseMutationApi mutations = mock();

    private final StudentExamPreparationService service = new StudentExamPreparationService(exams, users, studentExams, Optional.of(mutations), mock(), mock());

    private final Exam exam = new Exam();

    private final List<String> calls = new ArrayList<>();

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void simultaneousIndividualStartsReturnOneAttempt(boolean testExam) throws Exception {
        exam.setTestExam(testExam);
        var arrived = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var lock = new ReentrantLock();
        var assigned = new AtomicReference<StudentExam>();
        var created = new AtomicInteger();
        when(mutations.reserveParticipation(anyLong())).thenAnswer(_ -> new ParticipationReservation(() -> {
        }));
        doAnswer(invocation -> {
            arrived.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            lock.lock();
            try {
                Function<Exam, StudentExam> assignment = invocation.getArgument(1);
                return assignment.apply(exam);
            }
            finally {
                lock.unlock();
            }
        }).when(exams).withExerciseSelectionLock(anyLong(), any());
        when(studentExams.findWithExercisesByUserIdAndExamId(11L, 10L, false)).thenAnswer(_ -> Optional.ofNullable(assigned.get()));
        when(studentExams.findStudentExamsForTestExamsByUserIdAndExamId(11L, 10L)).thenAnswer(_ -> assigned.get() == null ? List.of() : List.of(assigned.get()));
        when(studentExams.findByIdWithExercisesElseThrow(55L)).thenAnswer(_ -> assigned.get());
        when(studentExams.createRandomStudentExams(exam, Set.of(11L))).thenAnswer(_ -> {
            created.incrementAndGet();
            var attempt = new StudentExam();
            attempt.setId(55L);
            assigned.set(attempt);
            return List.of(attempt);
        });
        try (var callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> service.assignStudent(10L, 11L));
            var second = callers.submit(() -> service.assignStudent(10L, 11L));
            try {
                assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
            }
            finally {
                release.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(second.get(5, TimeUnit.SECONDS));
            assertThat(created).hasValue(1);
        }
    }

    @Test
    void newlyAddedBusyExerciseInTheLockedGraphPreventsAssignment() {
        Exam locked = new Exam();
        locked.setId(10L);
        var added = new ProgrammingExercise();
        added.setId(4L);
        var group = new ExerciseGroup();
        group.setExercises(Set.of(added));
        locked.setExerciseGroups(List.of(group));
        doAnswer(invocation -> {
            Function<Exam, ?> assignment = invocation.getArgument(1);
            return assignment.apply(locked);
        }).when(exams).withExerciseSelectionLock(anyLong(), any());
        doThrow(new IllegalStateException("new draft is being generated")).when(mutations).reserveParticipation(4L);

        assertThatThrownBy(() -> service.assignRegisteredStudents(10L, false)).hasMessage("new draft is being generated");
        verifyNoInteractions(studentExams, users);
    }

    @Test
    void preparationProgressDoesNotRegressAndCanBeInvalidated() {
        var cache = new ConcurrentMapCacheManager(Constants.EXAM_EXERCISE_START_STATUS);
        WebsocketMessagingService messages = mock();
        var preparation = new StudentExamPreparationService(exams, users, studentExams, Optional.empty(), cache, messages);
        var start = ZonedDateTime.now();
        var lock = new ReentrantLock();
        preparation.sendAndCacheExercisePreparationStatus(42L, 5, 2, 10, 20, start, lock);
        preparation.sendAndCacheExercisePreparationStatus(42L, 3, 1, 8, 12, start, lock);
        var expected = new ExamExerciseStartPreparationStatus(5, 2, 10, 20, start);
        assertThat(preparation.getExerciseStartStatusOfExam(42L)).contains(expected);
        verify(messages, times(2)).sendMessage(EXERCISE_START_STATUS.at(42), expected);
        assertThat(lock.isLocked()).isFalse();
        preparation.invalidateExerciseStartStatus(42L);
        assertThat(preparation.getExerciseStartStatusOfExam(42L)).isEmpty();
    }

    @BeforeEach
    void setup() {
        exam.setId(10L);
        ProgrammingExercise first = new ProgrammingExercise();
        first.setId(1L);
        ProgrammingExercise second = new ProgrammingExercise();
        second.setId(2L);
        QuizExercise quiz = new QuizExercise();
        quiz.setId(3L);
        ExerciseGroup group = new ExerciseGroup();
        group.setExercises(Set.of(second, quiz, first));
        exam.setExerciseGroups(List.of(group));
        when(exams.findWithExerciseGroupsAndExercisesByIdOrElseThrow(10L)).thenReturn(exam);
        doAnswer(invocation -> {
            Function<Exam, ?> operation = invocation.getArgument(1);
            return operation.apply(exam);
        }).when(exams).withExerciseSelectionLock(anyLong(), any());
        when(studentExams.createRandomStudentExams(any(), any())).thenAnswer(ignored -> {
            calls.add("save");
            return List.of(new StudentExam());
        });
        when(mutations.reserveParticipation(1L)).thenAnswer(ignored -> reserve(1));
        when(mutations.reserveParticipation(2L)).thenAnswer(ignored -> reserve(2));
    }

    private ParticipationReservation reserve(int id) {
        calls.add("reserve" + id);
        return new ParticipationReservation(() -> calls.add("release" + id));
    }

    @Test
    void missingUsersAreAssignedWhileProgrammingExercisesAreReserved() {
        when(users.findUserIdsByExamId(10L)).thenAnswer(ignored -> {
            calls.add("users");
            return Set.of(11L, 12L);
        });
        when(studentExams.findUserIdsWithStudentExamsForExam(10L)).thenReturn(Set.of(11L));
        service.assignRegisteredStudents(10L, true);
        verify(studentExams).createRandomStudentExams(exam, Set.of(12L));
        assertThat(calls).containsExactly("reserve1", "reserve2", "users", "save", "release2", "release1");
    }

    @Test
    void reservationConflictPreventsAssignmentAndReleasesAlreadyAcquiredReservations() {
        doThrow(new IllegalStateException("generation active")).when(mutations).reserveParticipation(2L);
        assertThatThrownBy(() -> service.assignRegisteredStudents(10L, false)).hasMessage("generation active");
        assertThat(calls).containsExactly("reserve1", "release1");
        verifyNoInteractions(studentExams, users);
    }

    @Test
    void saveFailureStillReleasesEveryReservation() {
        doThrow(new IllegalStateException("save failed")).when(studentExams).createRandomStudentExams(any(), any());
        assertThatThrownBy(() -> service.assignRegisteredStudents(10L, false)).hasMessage("save failed");
        assertThat(calls).containsExactly("reserve1", "reserve2", "release2", "release1");
    }

    @Test
    void quizOnlyAssignmentDoesNotTouchProgrammingReservations() {
        exam.getExerciseGroups().getFirst().setExercises(Set.of(new QuizExercise()));
        service.assignRegisteredStudents(10L, false);
        verifyNoInteractions(mutations);
    }

    @Test
    void testRunIsGuardedUntilSaveTransactionReturns() {
        StudentExam testRun = new StudentExam();
        testRun.setExam(exam);
        testRun.setExercises(new ArrayList<>(exam.getExerciseGroups().getFirst().getExercises()));
        when(studentExams.save(testRun)).thenAnswer(ignored -> {
            assertThat(calls).containsExactly("reserve1", "reserve2");
            return testRun;
        });
        assertThat(service.assignTestRun(testRun)).isSameAs(testRun);
        assertThat(calls).containsExactly("reserve1", "reserve2", "release2", "release1");
    }

    @Test
    void individualAssignmentUsesTheExistingCreationPath() {
        service.assignStudent(10L, 11L);
        verify(studentExams).findWithExercisesByUserIdAndExamId(11L, 10L, false);
        verify(studentExams).createRandomStudentExams(exam, Set.of(11L));
        verifyNoMoreInteractions(studentExams);
        assertThat(calls).containsExactly("reserve1", "reserve2", "save", "release2", "release1");
    }

    @Test
    void absentHyperionApiPreservesAssignment() {
        var withoutHyperion = new StudentExamPreparationService(exams, users, studentExams, Optional.empty(), mock(), mock());
        withoutHyperion.assignRegisteredStudents(10L, false);
        verifyNoInteractions(mutations);
        assertThat(calls).containsExactly("save");
    }
}

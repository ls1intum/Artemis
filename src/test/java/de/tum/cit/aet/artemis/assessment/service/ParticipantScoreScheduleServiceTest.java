package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@ExtendWith(MockitoExtension.class)
class ParticipantScoreScheduleServiceTest {

    @Mock
    private TaskScheduler scheduler;

    @Mock
    private ParticipantScoreRepository participantScoreRepository;

    @Mock
    private StudentScoreRepository studentScoreRepository;

    @Mock
    private ResultTestRepository resultRepository;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    private UserTestRepository userRepository;

    @Test
    void newerResultRecomputesAfterAnUnfinishedOlderScoreIsPersisted() throws Exception {
        var exercise = new QuizExercise();
        exercise.setId(1L);
        exercise.setMaxPoints(10.0);
        exercise.setCourse(new Course());
        var user = new User();
        user.setId(2L);
        var score = new StudentScore();
        score.setId(3L);
        score.setExercise(exercise);
        score.setUser(user);
        var oldResult = new Result().score(0.0);
        oldResult.setId(4L);
        var newResult = new Result().score(100.0);
        newResult.setId(5L);
        var latestResult = new AtomicReference<>(oldResult);
        when(exerciseRepository.findById(exercise.getId())).thenReturn(Optional.of(exercise));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(studentScoreRepository.findByExercise_IdAndUser_Id(exercise.getId(), user.getId())).thenReturn(Optional.of(score));
        when(resultRepository.getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(exercise.getId(), user.getId()))
                .thenAnswer(invocation -> List.of(latestResult.get()));
        when(resultRepository.getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(exercise.getId(), user.getId()))
                .thenAnswer(invocation -> List.of(latestResult.get()));

        var firstFuture = mock(ScheduledFuture.class);
        var secondFuture = mock(ScheduledFuture.class);
        var tasks = ArgumentCaptor.forClass(Runnable.class);
        doReturn(firstFuture, secondFuture).when(scheduler).schedule(tasks.capture(), any(Instant.class));
        var savingOldScore = new CountDownLatch(1);
        var finishOldSave = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        var saves = new AtomicInteger();
        var runningThread = new AtomicReference<Thread>();
        when(firstFuture.cancel(anyBoolean())).thenAnswer(invocation -> {
            if (invocation.getArgument(0, Boolean.class)) {
                runningThread.get().interrupt();
            }
            return true;
        });
        doAnswer(invocation -> {
            if (saves.incrementAndGet() == 1) {
                savingOldScore.countDown();
                // Model a save that finishes despite an interrupt: its audit timestamp is newer than the next result's scheduling event.
                boolean finished = false;
                while (!finished) {
                    try {
                        finished = finishOldSave.await(10, TimeUnit.SECONDS);
                        assertThat(finished).as("the older save is released").isTrue();
                    }
                    catch (InterruptedException e) {
                        interrupted.set(true);
                    }
                }
            }
            ReflectionTestUtils.setField(score, "lastModifiedDate", Instant.now());
            return score;
        }).when(participantScoreRepository).save(score);

        var service = new ParticipantScoreScheduleService(scheduler, Optional.of(mock(CompetencyProgressApi.class)), participantScoreRepository, studentScoreRepository,
                mock(TeamScoreRepository.class), exerciseRepository, resultRepository, userRepository, mock(TeamRepository.class));
        service.activate();
        service.scheduleTask(exercise.getId(), user.getId(), null);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var firstTask = executor.submit(() -> {
                runningThread.set(Thread.currentThread());
                tasks.getAllValues().getFirst().run();
            });
            try {
                assertThat(savingOldScore.await(10, TimeUnit.SECONDS)).as("the older task has calculated its score").isTrue();
                latestResult.set(newResult);
                service.scheduleTask(exercise.getId(), user.getId(), null);
                finishOldSave.countDown();
                firstTask.get(10, TimeUnit.SECONDS);
                tasks.getAllValues().getLast().run();

                assertSoftly(softly -> {
                    softly.assertThat(score.getLastResult()).as("the newer result must replace the older result").isSameAs(newResult);
                    softly.assertThat(score.getLastScore()).isEqualTo(100.0);
                    softly.assertThat(interrupted).as("superseding a running task must not interrupt persistence").isFalse();
                });
            }
            finally {
                finishOldSave.countDown();
            }
        }
        finally {
            SecurityContextHolder.clearContext();
        }
    }
}

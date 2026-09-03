package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Covers the scheduling behaviour of {@link MilestoneScoreScheduleService} in isolation - what it promises about
 * <em>when</em> a recomputation runs, not what the recomputation produces (that is
 * {@code UserStoryExerciseGradingFanOutTest}).
 * <p>
 * Deliberately not an integration test: the service is a singleton driving a background scheduler, so activating it
 * inside a database-backed test class lets its recomputations race every later test in that class.
 */
class MilestoneScoreScheduleServiceTest {

    private static final long MILESTONE_EXERCISE_ID = 42L;

    private static final long STUDENT_ID = 7L;

    private ThreadPoolTaskScheduler scheduler;

    private MilestoneScoreService milestoneScoreService;

    private MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    private MilestoneScoreScheduleService service;

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.initialize();

        milestoneScoreService = mock(MilestoneScoreService.class);
        when(milestoneScoreService.recalculate(anyLong(), anyLong())).thenReturn(Optional.empty());
        milestoneExerciseGroupRepository = mock(MilestoneExerciseGroupRepository.class);
        when(milestoneExerciseGroupRepository.findMilestoneExerciseIdByUserStoryExerciseId(anyLong())).thenReturn(Optional.of(MILESTONE_EXERCISE_ID));

        service = new MilestoneScoreScheduleService(scheduler, milestoneScoreService, milestoneExerciseGroupRepository,
                mock(ProgrammingExerciseStudentParticipationRepository.class));
        service.activate();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
        scheduler.shutdown();
    }

    @Test
    void shouldCollapseABurstOfUserStoryEventsIntoASingleRecomputation() {
        // One build fans a result out to every story of the group, so one push produces one event per story for the same
        // student. Recomputing once per story would be pure waste - each run already reads every story's latest result.
        List.of(101L, 102L, 103L, 104L).forEach(userStoryExerciseId -> service.scheduleForUserStory(userStoryExerciseId, STUDENT_ID));

        await().atMost(10, TimeUnit.SECONDS).until(service::isIdle);
        verify(milestoneScoreService, times(1)).recalculate(MILESTONE_EXERCISE_ID, STUDENT_ID);
    }

    @Test
    void shouldNotCollapseEventsForDifferentStudents() {
        // Debouncing is per milestone AND student: two students pushing at the same time must both be recomputed.
        service.scheduleForUserStory(101L, STUDENT_ID);
        service.scheduleForUserStory(101L, STUDENT_ID + 1);

        await().atMost(10, TimeUnit.SECONDS).until(service::isIdle);
        verify(milestoneScoreService, times(1)).recalculate(MILESTONE_EXERCISE_ID, STUDENT_ID);
        verify(milestoneScoreService, times(1)).recalculate(MILESTONE_EXERCISE_ID, STUDENT_ID + 1);
    }

    @Test
    void shouldIgnoreResultsOfStoriesThatBelongToNoMilestoneGroup() {
        when(milestoneExerciseGroupRepository.findMilestoneExerciseIdByUserStoryExerciseId(anyLong())).thenReturn(Optional.empty());

        service.scheduleForUserStory(101L, STUDENT_ID);

        // The lookup runs on the scheduler (it must not run on the caller's thread, which is a JPA flush), so the
        // service is only idle again once that lookup has come back empty.
        await().atMost(10, TimeUnit.SECONDS).until(service::isIdle);
        verify(milestoneScoreService, times(0)).recalculate(anyLong(), anyLong());
    }

    @Test
    void shouldNotTouchTheDatabaseOnTheCallingThread() {
        // The regression guard: scheduleForUserStory is reached from ResultListener, inside the flush that persists the
        // result. A query there re-enters the session and rolls the result back, so the lookup must not have happened by
        // the time this method returns.
        service.scheduleForUserStory(101L, STUDENT_ID);

        verify(milestoneExerciseGroupRepository, never()).findMilestoneExerciseIdByUserStoryExerciseId(anyLong());
        await().atMost(10, TimeUnit.SECONDS).until(service::isIdle);
        verify(milestoneExerciseGroupRepository, times(1)).findMilestoneExerciseIdByUserStoryExerciseId(101L);
    }

    @Test
    void shouldDropWorkWhileTheServiceIsNotRunning() {
        // Mirrors shutdown: anything scheduled afterwards is dropped rather than queued, and the cron sweep picks the
        // affected milestones up again once the service is back.
        service.shutdown();

        service.scheduleForUserStory(101L, STUDENT_ID);

        assertThat(service.isIdle()).isTrue();
        verify(milestoneScoreService, times(0)).recalculate(anyLong(), anyLong());
    }

    @Test
    void shouldKeepRecomputingWhenARunFails() {
        // A failing recomputation must not poison the pair: the next event has to be scheduled and run as usual.
        when(milestoneScoreService.recalculate(anyLong(), anyLong())).thenThrow(new IllegalStateException("boom"));

        service.scheduleForUserStory(101L, STUDENT_ID);
        await().atMost(10, TimeUnit.SECONDS).until(service::isIdle);

        service.scheduleForUserStory(101L, STUDENT_ID);
        await().atMost(10, TimeUnit.SECONDS).until(service::isIdle);

        verify(milestoneScoreService, times(2)).recalculate(MILESTONE_EXERCISE_ID, STUDENT_ID);
    }
}

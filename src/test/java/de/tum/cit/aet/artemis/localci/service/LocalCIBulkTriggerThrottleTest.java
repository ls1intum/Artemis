package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;

/**
 * Unit tests for the wait that keeps a bulk trigger from filling the build queue.
 * <p>
 * What matters is when the run waits and, as much, when it does not: a run that waited on an agentless cluster or on an
 * unreadable queue, or that never stopped waiting, would leave an instructor's participations unbuilt, and nothing
 * retries a build that was not triggered. Time is a fake clock that the fake sleep advances, so nothing here waits.
 */
@ExtendWith(MockitoExtension.class)
class LocalCIBulkTriggerThrottleTest {

    private static final int MAX_QUEUED_JOBS_PER_BUILD_SLOT = 2;

    private static final Duration MAX_TOTAL_WAIT = Duration.ofMinutes(1);

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    private final AtomicLong nowNanos = new AtomicLong();

    private final List<Duration> sleeps = new ArrayList<>();

    private LocalCIBulkTriggerThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = throttleWithTotalWait(MAX_TOTAL_WAIT, duration -> {
            sleeps.add(duration);
            nowNanos.addAndGet(duration.toNanos());
        });
    }

    private LocalCIBulkTriggerThrottle throttleWithTotalWait(Duration maxTotalWait, LocalCIBulkTriggerThrottle.Sleeper sleeper) {
        return new LocalCIBulkTriggerThrottle(distributedDataAccessService, MAX_QUEUED_JOBS_PER_BUILD_SLOT, maxTotalWait, nowNanos::get, sleeper);
    }

    private static BuildAgentInformation agent(int maxConcurrentBuildJobs, @Nullable BuildAgentStatus status) {
        return new BuildAgentInformation(new BuildAgentDTO("agent", "address", "Agent"), maxConcurrentBuildJobs, 0, List.of(), status, null, null, 0);
    }

    private void givenAgents(BuildAgentInformation... agents) {
        when(distributedDataAccessService.getBuildAgentInformation()).thenReturn(List.of(agents));
    }

    @Test
    void awaitCapacity_withoutBuildAgents_doesNotWaitOrLookAtTheQueue() {
        // Nothing is taken from the queue then, so waiting for it to shrink would wait for an agent to turn up.
        givenAgents();

        throttle.awaitCapacity();

        assertThat(sleeps).isEmpty();
        verify(distributedDataAccessService, never()).getQueuedJobsSize();
    }

    @Test
    void awaitCapacity_whenAllBuildAgentsArePaused_doesNotWait() {
        givenAgents(agent(4, BuildAgentStatus.PAUSED), agent(4, BuildAgentStatus.SELF_PAUSED));

        throttle.awaitCapacity();

        assertThat(sleeps).isEmpty();
        verify(distributedDataAccessService, never()).getQueuedJobsSize();
    }

    @Test
    void awaitCapacity_belowTheLimit_doesNotWait() {
        // One agent with four slots may have eight queued jobs.
        givenAgents(agent(4, BuildAgentStatus.ACTIVE));
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(7);

        throttle.awaitCapacity();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void awaitCapacity_atTheLimit_waitsUntilTheQueueHasRoom() {
        givenAgents(agent(4, BuildAgentStatus.ACTIVE));
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(8, 9, 7);

        throttle.awaitCapacity();

        assertThat(sleeps).containsExactly(LocalCIBulkTriggerThrottle.POLL_INTERVAL, LocalCIBulkTriggerThrottle.POLL_INTERVAL);
        verify(distributedDataAccessService, times(3)).getQueuedJobsSize();
    }

    @Test
    void awaitCapacity_countsTheSlotsOfAllBuildAgentsThatTakeJobs() {
        // 4 + 2 slots count, the two paused agents do not: a limit of 12 jobs. An agent without a status takes jobs.
        givenAgents(agent(4, BuildAgentStatus.ACTIVE), agent(2, null), agent(10, BuildAgentStatus.PAUSED), agent(10, BuildAgentStatus.SELF_PAUSED));
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(11);

        throttle.awaitCapacity();

        assertThat(sleeps).as("11 queued jobs are below the limit of 12").isEmpty();

        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(12, 11);

        throttle.awaitCapacity();

        assertThat(sleeps).as("12 queued jobs are the limit").hasSize(1);
    }

    @Test
    void awaitCapacity_readsTheBuildAgentsOnlyOnceWithinTheRefreshInterval() {
        givenAgents(agent(4, BuildAgentStatus.ACTIVE));
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(0);

        throttle.awaitCapacity();
        throttle.awaitCapacity();
        throttle.awaitCapacity();

        verify(distributedDataAccessService, times(1)).getBuildAgentInformation();

        nowNanos.addAndGet(LocalCIBulkTriggerThrottle.BUILD_SLOTS_REFRESH_INTERVAL.toNanos());
        throttle.awaitCapacity();

        verify(distributedDataAccessService, times(2)).getBuildAgentInformation();
    }

    @Test
    void awaitCapacity_whileWaiting_noticesBuildAgentsThatJoinedInTheMeantime() {
        // Two slots allow four jobs and ten are queued. Once the slots are read again, ten slots allow twenty.
        when(distributedDataAccessService.getBuildAgentInformation()).thenReturn(List.of(agent(2, BuildAgentStatus.ACTIVE)), List.of(agent(10, BuildAgentStatus.ACTIVE)));
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(10);

        throttle.awaitCapacity();

        // Six polls of five seconds pass before the thirty second old reading is replaced.
        assertThat(sleeps).hasSize(6);
    }

    @Test
    void awaitCapacity_afterWaitingTooLong_queuesWithoutWaitingForTheRestOfTheRun() {
        givenAgents(agent(1, BuildAgentStatus.ACTIVE));
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(50);
        var shortBudget = throttleWithTotalWait(Duration.ofSeconds(12), duration -> {
            sleeps.add(duration);
            nowNanos.addAndGet(duration.toNanos());
        });

        shortBudget.awaitCapacity();

        // After 5 s and 10 s the budget of 12 s is not used up, after 15 s it is.
        assertThat(sleeps).hasSize(3);

        shortBudget.awaitCapacity();
        shortBudget.awaitCapacity();

        assertThat(sleeps).as("the run does not wait again").hasSize(3);
        verify(distributedDataAccessService, times(4)).getQueuedJobsSize();
    }

    @Test
    void awaitCapacity_whenInterrupted_returnsAndDoesNotWaitAgain() {
        givenAgents(agent(1, BuildAgentStatus.ACTIVE));
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(50);
        var interrupted = throttleWithTotalWait(MAX_TOTAL_WAIT, duration -> {
            sleeps.add(duration);
            throw new InterruptedException("shutting down");
        });

        assertThatCode(interrupted::awaitCapacity).doesNotThrowAnyException();
        interrupted.awaitCapacity();

        assertThat(sleeps).as("the second call does not sleep").hasSize(1);
        assertThat(Thread.currentThread().isInterrupted()).as("the flag is left alone so the caller's own pauses keep working").isFalse();
    }

    @Test
    void awaitCapacity_whenTheQueueCannotBeRead_returnsAndLooksAgainNextTime() {
        givenAgents(agent(4, BuildAgentStatus.ACTIVE));
        when(distributedDataAccessService.getQueuedJobsSize()).thenThrow(new IllegalStateException("not connected")).thenReturn(8, 0);

        assertThatCode(throttle::awaitCapacity).doesNotThrowAnyException();

        assertThat(sleeps).isEmpty();

        throttle.awaitCapacity();

        assertThat(sleeps).as("the next call sees a full queue and waits for it").hasSize(1);
    }

    @Test
    void awaitCapacity_whenTheBuildAgentsCannotBeRead_returnsAndReadsThemAgainNextTime() {
        when(distributedDataAccessService.getBuildAgentInformation()).thenThrow(new IllegalStateException("not connected")).thenReturn(List.of(agent(4, BuildAgentStatus.ACTIVE)));
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(0);

        assertThatCode(throttle::awaitCapacity).doesNotThrowAnyException();

        verify(distributedDataAccessService, never()).getQueuedJobsSize();

        throttle.awaitCapacity();

        verify(distributedDataAccessService, times(2)).getBuildAgentInformation();
        verify(distributedDataAccessService).getQueuedJobsSize();
    }
}

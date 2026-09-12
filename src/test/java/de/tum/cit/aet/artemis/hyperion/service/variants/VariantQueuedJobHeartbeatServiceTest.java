package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A job waiting behind the bounded variant pool has no worker to beat for it, yet read-side reconciliation fails any
 * non-terminal job whose heartbeat has gone stale — so an instructor's queued variant could be failed for waiting.
 * The node holding the queue vouches for those jobs instead. These tests pin the two halves that make that safe: while
 * a job is queued here its heartbeat is refreshed, and the moment it stops being this queue's responsibility the
 * refreshing stops — otherwise a dead node's queue could never be recovered, which is the reason the staleness check
 * exists. The clock is driven directly rather than waited on.
 */
class VariantQueuedJobHeartbeatServiceTest {

    private static final String JOB_ID = "job-1";

    private static final String OTHER_JOB_ID = "job-2";

    private ExerciseVariantJobService jobService;

    private VariantQueuedJobHeartbeatService queuedJobHeartbeats;

    @BeforeEach
    void setUp() {
        jobService = mock(ExerciseVariantJobService.class);
        queuedJobHeartbeats = new VariantQueuedJobHeartbeatService(jobService);
    }

    @Test
    void keepsAQueuedJobAliveWhileItWaits() {
        queuedJobHeartbeats.noteQueued(JOB_ID);

        queuedJobHeartbeats.refreshQueuedJobHeartbeats();
        queuedJobHeartbeats.refreshQueuedJobHeartbeats();

        verify(jobService, times(2)).heartbeat(JOB_ID);
    }

    @Test
    void stopsBeatingForAJobThatLeftTheQueue() {
        queuedJobHeartbeats.noteQueued(JOB_ID);
        queuedJobHeartbeats.noteLeftQueue(JOB_ID);

        queuedJobHeartbeats.refreshQueuedJobHeartbeats();

        verify(jobService, never()).heartbeat(JOB_ID);
    }

    @Test
    void beatsForNothingWhenTheQueueIsEmpty() {
        queuedJobHeartbeats.refreshQueuedJobHeartbeats();

        verify(jobService, never()).heartbeat(anyString());
    }

    @Test
    void oneUnreachableJobDoesNotCostTheOthersTheirRefresh() {
        // Nothing may escape the scheduled method either: the scheduler would stop invoking it, and every job queued
        // on this node afterwards would go stale.
        queuedJobHeartbeats.noteQueued(JOB_ID);
        queuedJobHeartbeats.noteQueued(OTHER_JOB_ID);
        doThrow(new IllegalStateException("distributed map unavailable")).when(jobService).heartbeat(JOB_ID);

        queuedJobHeartbeats.refreshQueuedJobHeartbeats();

        verify(jobService).heartbeat(OTHER_JOB_ID);
    }
}

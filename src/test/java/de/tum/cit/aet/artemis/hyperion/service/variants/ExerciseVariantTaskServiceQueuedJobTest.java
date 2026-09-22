package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A variant job waits in the executor's queue before it runs, and the queue holds up to 32 of them while a job
 * takes minutes. The record can therefore reach a terminal state before its task ever starts — the instructor
 * cancels it, or read-side reconciliation marks it stale. These tests pin that the record, not the queued task,
 * decides whether the pipeline runs at all: a terminal job is dropped instead of provisioning a clone and
 * publishing events nothing could correct afterwards, and a job that does run starts its own heartbeat window
 * rather than inheriting the time it spent queued.
 */
class ExerciseVariantTaskServiceQueuedJobTest {

    private static final String JOB_ID = "job-1";

    private static final String INITIATOR = "instructor1";

    private ExerciseVariantGenerationPipelineService pipeline;

    private ExerciseVariantJobService jobService;

    private ExerciseVariantTaskService taskService;

    private VariantQueuedJobHeartbeatService queuedJobHeartbeats;

    private VariantJob job;

    @BeforeEach
    void setUp() {
        pipeline = mock(ExerciseVariantGenerationPipelineService.class);
        jobService = mock(ExerciseVariantJobService.class);
        queuedJobHeartbeats = mock(VariantQueuedJobHeartbeatService.class);
        taskService = new ExerciseVariantTaskService(pipeline, jobService, queuedJobHeartbeats);

        job = new VariantJob();
        job.setJobId(JOB_ID);
        job.setInitiatorLogin(INITIATOR);
    }

    @Test
    void doesNotRunAJobThatBecameTerminalWhileItWasQueued() {
        when(jobService.getJob(JOB_ID, INITIATOR)).thenReturn(Optional.of(recordInPhase(VariantJobPhase.FAILED)));

        taskService.runJobAsync(job);

        verify(pipeline, never()).run(job);
    }

    @Test
    void doesNotRunAJobWhoseRecordIsGone() {
        when(jobService.getJob(JOB_ID, INITIATOR)).thenReturn(Optional.empty());

        taskService.runJobAsync(job);

        verify(pipeline, never()).run(job);
    }

    @Test
    void runsAStillPendingJobAndStartsItsHeartbeatWindow() {
        when(jobService.getJob(JOB_ID, INITIATOR)).thenReturn(Optional.of(recordInPhase(VariantJobPhase.ANALYZING)));

        taskService.runJobAsync(job);

        verify(jobService).heartbeat(JOB_ID);
        verify(pipeline).run(job);
    }

    @Test
    void stopsTheQueueFromVouchingForAJobItNoLongerHolds() {
        // The worker has the job now, so its own heartbeats take over — leaving it registered would keep refreshing a
        // job the queue is no longer responsible for. Also true for a job that turns out to be terminal.
        when(jobService.getJob(JOB_ID, INITIATOR)).thenReturn(Optional.of(recordInPhase(VariantJobPhase.ANALYZING)));

        taskService.runJobAsync(job);

        verify(queuedJobHeartbeats).noteLeftQueue(JOB_ID);
    }

    private VariantJob recordInPhase(VariantJobPhase phase) {
        VariantJob record = new VariantJob();
        record.setJobId(JOB_ID);
        record.setInitiatorLogin(INITIATOR);
        record.setPhase(phase);
        return record;
    }
}

package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

/** Requests cancellation of stale authoring without admitting a replacement while its worker can still run. */
final class GenerationJobReaper {

    private final GenerationJobService jobService;

    private final DistributedMap<String, GenerationJobService.JobInfo> jobMap;

    private final DistributedMap<String, Boolean> cancellationMap;

    private final GenerationJobReplayStore replayStore;

    @Nullable
    private final HyperionGenerationBudgetService generationBudgetService;

    @Nullable
    private final Duration staleJobTimeout;

    GenerationJobReaper(GenerationJobService jobService, DistributedMap<String, GenerationJobService.JobInfo> jobMap, DistributedMap<String, Boolean> cancellationMap,
            GenerationJobReplayStore replayStore, @Nullable HyperionGenerationBudgetService generationBudgetService, @Nullable Duration staleJobTimeout) {
        this.jobService = jobService;
        this.jobMap = jobMap;
        this.cancellationMap = cancellationMap;
        this.replayStore = replayStore;
        this.staleJobTimeout = staleJobTimeout;
        this.generationBudgetService = generationBudgetService;
    }

    void sweep() {
        Instant now = Instant.now();
        Instant staleBefore = staleBefore(now);
        for (Map.Entry<String, GenerationJobService.JobInfo> entry : jobMap.entrySet()) {
            GenerationJobService.JobInfo job = entry.getValue();
            if (job == null || !shouldClearAsStale(job, staleBefore)) {
                continue;
            }
            String key = entry.getKey();
            jobService.lockJobSlot(key);
            try {
                GenerationJobService.JobInfo current = jobMap.get(key);
                if (current != null && current.jobId().equals(job.jobId()) && shouldClearAsStale(current, staleBefore)) {
                    requestStaleCancellation(key, current, now);
                }
            }
            finally {
                jobService.unlockJobSlot(key);
            }
        }
    }

    /** Stale heartbeats request a stop; only completion or explicit operator recovery releases the reservation. */
    void requestStaleCancellation(String key, GenerationJobService.JobInfo current, Instant now) {
        if (!current.cancellable() || current.cancellationRequested()) {
            return;
        }
        if (jobMap.replace(key, current, current.withCancellationRequested())) {
            cancellationMap.put(current.jobId(), Boolean.TRUE);
            if (generationBudgetService != null) {
                generationBudgetService.retainReservationForBudgetWindow(current.budgetReservationId());
            }
            replayStore.terminalizeStoppedJob(current, stoppedMessage(current, now), stoppedTerminationReason(current, now));
            jobService.interruptCluster(current.jobId());
        }
    }

    /** Explicit operator recovery after the old worker and save requests have been stopped. */
    boolean stopActiveJob(String key, GenerationJobService.JobInfo current, Instant now) {
        if (!jobMap.remove(key, current)) {
            return false;
        }
        cancellationMap.put(current.jobId(), Boolean.TRUE);
        replayStore.terminalizeStoppedJob(current, stoppedMessage(current, now), stoppedTerminationReason(current, now));
        replayStore.sealUsageIncomplete(current.jobId());
        replayStore.retainAfterJobCleared(current.exerciseId(), current.jobId());
        if (generationBudgetService != null) {
            generationBudgetService.retainReservationForBudgetWindow(current.budgetReservationId());
        }
        jobService.interruptCluster(current.jobId());
        jobService.publishExerciseState(current.exerciseId(), current.jobId(), false);
        return true;
    }

    @Nullable
    Instant staleBefore(Instant now) {
        return staleJobTimeout == null || staleJobTimeout.isZero() || staleJobTimeout.isNegative() ? null : now.minus(staleJobTimeout);
    }

    boolean shouldClearAsStale(GenerationJobService.JobInfo job, @Nullable Instant staleBefore) {
        return staleBefore != null && !job.lastHeartbeatOrStartedAt().isAfter(staleBefore);
    }

    private String stoppedMessage(GenerationJobService.JobInfo job, Instant now) {
        if (job.deadlineAt() != null && !job.deadlineAt().isAfter(now)) {
            return "Generation stopped because it exceeded the configured time limit. Review the exercise before use.";
        }
        return "Generation stopped sending heartbeats. Stop the old worker and inspect the exercise before manually clearing its reservation.";
    }

    private ExerciseGenerationEventDTO.TerminationReason stoppedTerminationReason(GenerationJobService.JobInfo job, Instant now) {
        return job.deadlineAt() != null && !job.deadlineAt().isAfter(now) ? ExerciseGenerationEventDTO.TerminationReason.DEADLINE_EXCEEDED
                : ExerciseGenerationEventDTO.TerminationReason.CANCELLED;
    }
}

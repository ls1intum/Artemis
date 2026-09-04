package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

/**
 * Decides which generation slots have been abandoned and reclaims the ones that are safe to reclaim.
 * <p>
 * Reclamation is deliberately asymmetric. A cancellable generation is stopped once its owner leaves the cluster, because nothing it has written is durable yet. A
 * non-cancellable persistence or revert mutation keeps its slot instead: cluster departure does not prove that the owner's Git and database writes have stopped, so releasing
 * the slot could let a replacement claim race a partitioned writer. Those slots block the exercise until an operator confirms the old owner is quiescent and recovers them.
 */
final class GenerationJobReaper {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobReaper.class);

    private final GenerationJobService jobService;

    private final DistributedDataProvider distributedDataProvider;

    private final DistributedMap<String, GenerationJobService.JobInfo> jobMap;

    private final DistributedMap<String, Boolean> cancellationMap;

    private final GenerationJobReplayStore replayStore;

    @Nullable
    private final HyperionGenerationBudgetService generationBudgetService;

    @Nullable
    private final Duration staleJobTimeout;

    private final Duration maxJobDuration;

    GenerationJobReaper(GenerationJobService jobService, DistributedDataProvider distributedDataProvider, DistributedMap<String, GenerationJobService.JobInfo> jobMap,
            DistributedMap<String, Boolean> cancellationMap, GenerationJobReplayStore replayStore, @Nullable HyperionGenerationBudgetService generationBudgetService,
            @Nullable Duration staleJobTimeout, Duration maxJobDuration) {
        this.jobService = jobService;
        this.distributedDataProvider = distributedDataProvider;
        this.jobMap = jobMap;
        this.cancellationMap = cancellationMap;
        this.replayStore = replayStore;
        this.generationBudgetService = generationBudgetService;
        this.staleJobTimeout = staleJobTimeout;
        this.maxJobDuration = maxJobDuration;
    }

    /** Cancels stale jobs and terminalizes jobs whose owner has left the Hazelcast cluster. */
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
                if (current == null || !current.jobId().equals(job.jobId()) || !shouldClearAsStale(current, staleBefore)) {
                    continue;
                }
                reclaimStaleJob(key, current, Instant.now());
            }
            finally {
                jobService.unlockJobSlot(key);
            }
        }
    }

    /** Whether the node that claimed this slot is still a member of the cluster. Unknown ownership and lookup failures both count as present, so the slot is retained. */
    boolean ownerMemberIsPresent(GenerationJobService.JobInfo job) {
        if (job.ownerNodeId() == null) {
            return true;
        }
        try {
            return distributedDataProvider.getDataNodeIds().map(nodeIds -> nodeIds.contains(job.ownerNodeId())).orElse(true);
        }
        catch (RuntimeException e) {
            log.warn("Could not determine whether the owner of stale generation job {} is still a cluster member; retaining its slot", job.jobId(), e);
            return true;
        }
    }

    /**
     * Reclaims a cancellable stale job only after its owner leaves the cluster. Non-cancellable and external mutation slots fail closed because their durable writes may still be
     * running after membership loss.
     */
    boolean reclaimStaleJob(String key, GenerationJobService.JobInfo current, Instant now) {
        // Membership loss neither fences nor hard-cancels an external request, so clearing its slot here could overlap a partitioned writer with a new generation.
        if (GenerationJobService.isExternalMutationJob(current)) {
            return false;
        }
        if (!current.cancellable()) {
            // A durable persistence/revert mutation may still be in flight, so retain the slot: a replacement claim must conflict rather than race the old owner's un-fenced
            // Git/DB writes. Only an absent owner is logged; a live owner with a merely stale heartbeat is not actionable.
            if (!ownerMemberIsPresent(current)) {
                log.warn(
                        "Retaining non-cancellable generation slot for job {} (exercise {}) after its owner {} left the Hazelcast cluster: cluster departure does not prove that "
                                + "the in-flight persistence/revert mutation has stopped. The slot stays claimed and blocks generation, revert and ordinary REST edits of this "
                                + "exercise until an operator recovers it: read it with GET /api/admin/exercises/{}/hyperion-wedged-slot and, once the old owner and its Git/DB "
                                + "requests are confirmed quiescent, release it with DELETE /api/admin/exercises/{}/hyperion-wedged-slots/{}?reason=...",
                        current.jobId(), current.exerciseId(), current.ownerNodeId(), current.exerciseId(), current.exerciseId(), current.jobId());
            }
            return false;
        }
        if (ownerMemberIsPresent(current)) {
            signalStaleLiveOwner(current, now);
            return false;
        }
        stopActiveJob(key, current, now);
        return true;
    }

    private void signalStaleLiveOwner(GenerationJobService.JobInfo current, Instant now) {
        boolean alreadyCancelled = Boolean.TRUE.equals(cancellationMap.get(current.jobId()));
        cancellationMap.put(current.jobId(), Boolean.TRUE);
        replayStore.terminalizeStoppedJob(current, stoppedMessage(current, now), stoppedTerminationReason(current, now));
        if (!alreadyCancelled) {
            jobService.interruptCluster(current.jobId());
        }
    }

    /** @return whether this call was the one that removed the slot */
    boolean stopActiveJob(String key, GenerationJobService.JobInfo current, Instant now) {
        cancellationMap.put(current.jobId(), Boolean.TRUE, maxJobDuration.isZero() || maxJobDuration.isNegative() ? Duration.ofSeconds(1) : maxJobDuration);
        replayStore.terminalizeStoppedJob(current, stoppedMessage(current, now), stoppedTerminationReason(current, now));
        replayStore.sealUsageIncomplete(current.jobId());
        boolean released = jobMap.remove(key, current);
        if (released) {
            replayStore.retainAfterJobCleared(current.exerciseId(), current.jobId());
        }
        retainUncertainBudgetReservation(current);
        jobService.interruptCluster(current.jobId());
        if (GenerationJobService.isGenerationJob(current)) {
            jobService.publishExerciseState(current.exerciseId(), current.jobId(), false);
        }
        return released;
    }

    private void retainUncertainBudgetReservation(GenerationJobService.JobInfo job) {
        if (GenerationJobService.isGenerationJob(job) && generationBudgetService != null) {
            generationBudgetService.retainReservationForBudgetWindow(job.budgetReservationId());
        }
    }

    @Nullable
    Instant staleBefore(Instant now) {
        return staleJobTimeout == null || staleJobTimeout.isZero() || staleJobTimeout.isNegative() ? null : now.minus(staleJobTimeout);
    }

    boolean shouldClearAsStale(GenerationJobService.JobInfo job, @Nullable Instant staleBefore) {
        return (staleBefore != null && !job.lastHeartbeatOrStartedAt().isAfter(staleBefore)) || !ownerMemberIsPresent(job);
    }

    private String stoppedMessage(GenerationJobService.JobInfo job, Instant now) {
        if (job.deadlineAt() != null && !job.deadlineAt().isAfter(now)) {
            return "Generation stopped because it exceeded the configured time limit. Nothing was changed.";
        }
        return "Generation stopped because the owning node stopped sending heartbeats. Review the exercise and repositories before use if this happened while saving.";
    }

    private ExerciseGenerationEventDTO.TerminationReason stoppedTerminationReason(GenerationJobService.JobInfo job, Instant now) {
        return job.deadlineAt() != null && !job.deadlineAt().isAfter(now) ? ExerciseGenerationEventDTO.TerminationReason.DEADLINE_EXCEEDED
                : ExerciseGenerationEventDTO.TerminationReason.CANCELLED;
    }
}

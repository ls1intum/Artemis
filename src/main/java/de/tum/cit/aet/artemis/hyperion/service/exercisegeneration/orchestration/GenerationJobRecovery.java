package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Instant;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.WedgedSlotInfo;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.WedgedSlotKind;

/** Operator recovery policy, evaluated while the caller holds the exercise slot lock. */
final class GenerationJobRecovery {

    private GenerationJobRecovery() {
    }

    static Optional<WedgedSlotInfo> info(long exerciseId, @Nullable JobInfo job, GenerationJobReaper reaper) {
        if (job == null || job.cancellable()) {
            return Optional.empty();
        }
        return Optional.of(new WedgedSlotInfo(exerciseId, job.jobId(), kind(job), job.ownerNodeId(), job.startedAt(), !reaper.ownerMemberIsPresent(job)));
    }

    static boolean recover(DistributedMap<String, JobInfo> jobs, String key, String token, GenerationClusterTopology topology, GenerationJobReaper reaper) {
        topology.verifyMajority();
        JobInfo job = jobs.get(key);
        if (job == null || !job.jobId().equals(token) || job.cancellable()) {
            return false;
        }
        // Only a retained partial undo is known to be quiescent while its owner remains a member.
        if (reaper.ownerMemberIsPresent(job) && !GenerationRevertSlots.isPending(job)) {
            return false;
        }
        if (GenerationJobService.isGenerationJob(job)) {
            return reaper.stopActiveJob(key, job, Instant.now());
        }
        return jobs.remove(key, job);
    }

    private static WedgedSlotKind kind(JobInfo job) {
        if (job.jobId().startsWith(GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX)) {
            return WedgedSlotKind.EXTERNAL_MUTATION;
        }
        if (GenerationRevertSlots.isPending(job)) {
            return WedgedSlotKind.REVERT_RECOVERY;
        }
        return job.jobId().startsWith(GenerationJobService.REVERT_JOB_PREFIX) ? WedgedSlotKind.REVERT : WedgedSlotKind.GENERATION;
    }
}

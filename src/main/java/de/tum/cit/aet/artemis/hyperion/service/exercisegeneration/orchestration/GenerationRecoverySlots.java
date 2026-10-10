package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

/** Exact-token undo admission and transitions to quiescent, fail-closed recovery guards. */
final class GenerationRecoverySlots {

    private static final String GENERATION_RECOVERY_PREFIX = "generation-recovery-";

    private static final String RECOVERY_PREFIX = "revert-recovery-";

    static final String RETRY_PREFIX = "revert-retry-";

    private GenerationRecoverySlots() {
    }

    static String claim(DistributedMap<String, JobInfo> jobMap, User user, long exerciseId, @Nullable String localNodeId, Runnable validateTopology,
            Consumer<JobInfo> claimNormal) {
        String key = String.valueOf(exerciseId);
        jobMap.lock(key);
        try {
            validateTopology.run();
            JobInfo existing = jobMap.get(key);
            boolean recovering = existing != null && existing.jobId().startsWith(RECOVERY_PREFIX);
            String token = (recovering ? RETRY_PREFIX : "revert-") + UUID.randomUUID();
            Instant now = Instant.now();
            JobInfo replacement = new JobInfo(token, user.getLogin(), exerciseId, now, null, localNodeId, now, false, null);
            if (recovering) {
                if (!jobMap.replace(key, existing, replacement)) {
                    throw new ConflictException("The recovery reservation changed; retry the undo.", "hyperionExerciseGeneration", "exerciseGenerationRunning");
                }
            }
            else {
                claimNormal.accept(replacement);
            }
            return token;
        }
        finally {
            jobMap.unlock(key);
        }
    }

    /** A completed partial undo is quiescent but inconsistent; only an authorized undo retry may consume this guard. */
    static void retain(DistributedMap<String, JobInfo> jobMap, long exerciseId, String token) {
        String key = String.valueOf(exerciseId);
        jobMap.lock(key);
        try {
            JobInfo current = jobMap.get(key);
            if (current != null && current.jobId().equals(token)) {
                jobMap.replace(key, current, new JobInfo(RECOVERY_PREFIX + UUID.randomUUID(), current.userLogin(), exerciseId, current.startedAt(), null, current.ownerNodeId(),
                        Instant.now(), false, null));
            }
        }
        finally {
            jobMap.unlock(key);
        }
    }

    static void retainGeneration(DistributedMap<String, JobInfo> jobMap, long exerciseId, String token, String localNodeId) {
        String key = String.valueOf(exerciseId);
        jobMap.lock(key);
        try {
            JobInfo current = jobMap.get(key);
            if (current != null && current.jobId().equals(token) && !current.cancellable() && localNodeId.equals(current.ownerNodeId())) {
                jobMap.replace(key, current, new JobInfo(GENERATION_RECOVERY_PREFIX + token, current.userLogin(), exerciseId, current.startedAt(), null, current.ownerNodeId(),
                        Instant.now(), false, null));
            }
        }
        finally {
            jobMap.unlock(key);
        }
    }

    static boolean isGenerationRecovery(JobInfo job) {
        return job.jobId().startsWith(GENERATION_RECOVERY_PREFIX);
    }

    static boolean isPending(@Nullable JobInfo current) {
        return current != null && current.jobId().startsWith(RECOVERY_PREFIX);
    }
}

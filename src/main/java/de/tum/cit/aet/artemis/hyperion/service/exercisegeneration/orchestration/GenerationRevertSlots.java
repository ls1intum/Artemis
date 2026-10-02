package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

/** Exact-token transitions between an executing undo and its quiescent, fail-closed recovery state. */
final class GenerationRevertSlots {

    static final String RECOVERY_PREFIX = "revert-recovery-";

    static final String RETRY_PREFIX = "revert-retry-";

    private GenerationRevertSlots() {
    }

    static String claim(DistributedMap<String, JobInfo> jobMap, User user, long exerciseId, @Nullable String localNodeId, Runnable validateTopology,
            Consumer<JobInfo> claimNormal) {
        String key = String.valueOf(exerciseId);
        jobMap.lock(key);
        try {
            validateTopology.run();
            JobInfo existing = jobMap.get(key);
            boolean recovering = existing != null && existing.jobId().startsWith(RECOVERY_PREFIX);
            String token = (recovering ? RETRY_PREFIX : GenerationJobService.REVERT_JOB_PREFIX) + UUID.randomUUID();
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

    static boolean isPending(@Nullable JobInfo current) {
        return current != null && current.jobId().startsWith(RECOVERY_PREFIX);
    }
}

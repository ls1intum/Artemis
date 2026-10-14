package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

/** Identifies legacy activity records during a coordinated upgrade; new recovery ownership is stored in the database. */
final class GenerationRecoverySlots {

    private static final String GENERATION_RECOVERY_PREFIX = "generation-recovery-";

    static final String RECOVERY_PREFIX = "revert-recovery-";

    static final String RETRY_PREFIX = "revert-retry-";

    private GenerationRecoverySlots() {
    }

    static boolean isGenerationRecovery(JobInfo job) {
        return job.jobId().startsWith(GENERATION_RECOVERY_PREFIX);
    }

    static boolean isPending(@Nullable JobInfo current) {
        return current != null && current.jobId().startsWith(RECOVERY_PREFIX);
    }
}

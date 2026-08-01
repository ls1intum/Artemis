package de.tum.cit.aet.artemis.buildagent.service.runner;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;

/**
 * Executes a prepared LocalCI build in an isolated environment.
 */
public interface BuildJobRunner {

    BuildRunnerType type();

    BuildRunnerStatus status();

    BuildJobRunnerResult execute(BuildJobQueueItem buildJob, PreparedBuildJob preparedBuildJob);

    void cancel(String buildJobId);

    boolean isActive(String buildJobId);

    /**
     * Removes execution resources left behind by interrupted build-agent processes.
     */
    default void cleanupOrphans() {
    }
}

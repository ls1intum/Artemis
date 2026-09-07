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
     * Whether the job is still fetching the image it will run in, and therefore has no execution resource yet.
     * <p>
     * Two callers need this, and both would otherwise mistake a slow fetch for a failure: the stale-job watchdog, which
     * looks for jobs that are tracked as running but have nothing running, and the build timeout, which must not spend a
     * job's budget on a fetch. Fetching is bounded by its own timeout, so neither needs to act as a backstop for it.
     *
     * @param buildJobId the job to ask about
     * @return {@code true} while the image is being fetched
     */
    default boolean isFetchingImage(String buildJobId) {
        return false;
    }

    /**
     * Removes execution resources left behind by interrupted build-agent processes.
     */
    default void cleanupOrphans() {
    }
}

package de.tum.cit.aet.artemis.hyperion.service;

import org.slf4j.Logger;

/** Failure handling shared by Hyperion build-result polls, keeping each caller's polling policy and logging context. */
public final class BuildResultPollingSupport {

    private BuildResultPollingSupport() {
    }

    /**
     * Propagates polling interruptions, or warns and waits before the caller retries an ordinary failed poll.
     * An interruption during the backoff propagates directly from sleep, retaining its cleared interrupt flag.
     *
     * @param failure      the exception from the failed poll
     * @param commitHash   commit used by the caller's diagnostic message
     * @param pollInterval retry delay in milliseconds
     * @param logger       the caller's logger
     * @throws InterruptedException the original polling interruption, or an interruption during retry backoff
     */
    public static void handleFailure(Exception failure, String commitHash, long pollInterval, Logger logger) throws InterruptedException {
        if (failure instanceof InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw interruption;
        }
        logger.warn("Exception while polling for build result for commit {}: {}. Continuing...", commitHash, failure.getMessage());
        Thread.sleep(pollInterval);
    }
}

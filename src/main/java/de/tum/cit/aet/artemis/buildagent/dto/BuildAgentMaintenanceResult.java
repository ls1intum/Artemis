package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Hazelcast topic payload describing the result of one maintenance action on a build agent.
 * <p>
 * Published by the agent that ran the action immediately after the action completes (success, partial failure, full
 * failure, or skipped). Forwarded by core nodes onto the per-agent WebSocket topic
 * {@code /topic/admin/build-agent/<short-name>/maintenance} so any admin currently viewing that agent's details page
 * gets a real-time toast — operators no longer have to ssh to the agent and grep the log to find out whether the
 * wipe actually deleted anything.
 * <p>
 * Counterpart to {@link BuildAgentMaintenanceAction} (which kicks off the action). The action carries an intent, this
 * record carries the outcome.
 *
 * @param agentShortName the short name of the agent that performed the action; used both for client-side routing
 *                           (each admin is viewing a specific agent's page) and to identify the source in toasts
 * @param timestamp      when the agent finished the action (wall clock)
 * @param actionType     which action this is the result of
 * @param outcome        success / partial failure / failure / skipped
 * @param bytesFreed     total bytes freed by the action: deleted file bytes for wipe/cleanup, reclaimed image bytes
 *                           for {@code CLEAR_DOCKER_IMAGES}. {@code 0} when nothing was freed.
 * @param itemsAffected  number of items successfully removed: files for {@code RUN_CACHE_CLEANUP} /
 *                           {@code WIPE_*_CACHE}, images for {@code CLEAR_DOCKER_IMAGES}
 * @param errorCount     number of items the action attempted to remove but could not (typically a permission or
 *                           in-use error); a non-zero value with {@code SUCCESS} promotes the outcome to
 *                           {@code PARTIAL_FAILURE}
 * @param durationMs     wall-clock duration of the action in milliseconds
 * @param skipReason     populated when {@code outcome == SKIPPED}; values mirror the existing skip reasons emitted by
 *                           {@code BuildContainerCacheCleanupService} (e.g. {@code "disabled"}, {@code "read-only"},
 *                           {@code "no-target"}). {@code null} otherwise.
 * @param message        optional human-readable detail (typically only set when {@code outcome == FAILED} to carry
 *                           the exception message into the toast). {@code null} otherwise.
 */
public record BuildAgentMaintenanceResult(String agentShortName, Instant timestamp, BuildAgentMaintenanceAction.Type actionType, Outcome outcome, long bytesFreed,
        long itemsAffected, long errorCount, long durationMs, @Nullable String skipReason, @Nullable String message) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Outcome {
        /** Action ran and reported zero errors. */
        SUCCESS,
        /** Action ran but {@code errorCount > 0}; some items could not be removed. */
        PARTIAL_FAILURE,
        /** Action threw before completing; {@code message} carries the cause. */
        FAILED,
        /** Action short-circuited before running; {@code skipReason} explains why. */
        SKIPPED
    }
}

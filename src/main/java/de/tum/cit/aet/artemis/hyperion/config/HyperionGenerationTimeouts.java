package de.tum.cit.aet.artemis.hyperion.config;

import java.time.Duration;

/**
 * The two timeout invariants that relate {@code artemis.hyperion.agent.max-job-duration} to the values around it.
 * <p>
 * They live here, as pure functions, so the beans that need them at construction time and the eager {@link HyperionGenerationConfigurationValidator} enforce the same rule rather
 * than two drifting copies. Both are startup errors, so a deployment with a contradictory pair never reaches an instructor.
 */
public final class HyperionGenerationTimeouts {

    private HyperionGenerationTimeouts() {
    }

    /**
     * Requires a wall-clock deadline that a run can actually be given.
     *
     * @param maxJobDuration the configured deadline
     * @throws IllegalArgumentException if it is absent or not positive
     */
    public static void validateMaxJobDuration(Duration maxJobDuration) {
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-job-duration must be positive");
        }
    }

    /**
     * Requires the stale-slot timeout to outlast every deadline a run may be given, including the ones effort profiles can raise it to. Otherwise another node reclaims a slot
     * whose owner is still legitimately running, and two nodes can hold the same exercise.
     *
     * @param staleJobTimeout       the configured stale-slot timeout
     * @param longestMaxJobDuration the largest deadline across the deployment default and every configured effort profile
     * @throws IllegalArgumentException if the timeout does not strictly exceed the longest deadline
     */
    public static void validateStaleJobTimeout(Duration staleJobTimeout, Duration longestMaxJobDuration) {
        validateMaxJobDuration(longestMaxJobDuration);
        if (staleJobTimeout == null || staleJobTimeout.compareTo(longestMaxJobDuration) <= 0) {
            throw new IllegalArgumentException("artemis.hyperion.agent.stale-job-timeout (" + staleJobTimeout
                    + ") must be greater than the longest configured max-job-duration (the deployment default or any effort profile under artemis.hyperion.agent.profiles, "
                    + "whichever is larger: " + longestMaxJobDuration + "), or another node would reclaim a slot while its owner is still legitimately running");
        }
    }

    /**
     * Requires the ownership heartbeat to fire at least once within a run's deadline.
     *
     * @param ownerHeartbeatInterval the configured heartbeat interval
     * @param maxJobDuration         the deployment-default deadline
     * @throws IllegalArgumentException if the interval is not positive or not shorter than the deadline
     */
    public static void validateOwnerHeartbeatInterval(Duration ownerHeartbeatInterval, Duration maxJobDuration) {
        validateMaxJobDuration(maxJobDuration);
        if (ownerHeartbeatInterval == null || ownerHeartbeatInterval.isZero() || ownerHeartbeatInterval.isNegative() || ownerHeartbeatInterval.compareTo(maxJobDuration) >= 0) {
            throw new IllegalArgumentException("artemis.hyperion.agent.owner-heartbeat-interval must be positive and shorter than max-job-duration");
        }
    }
}

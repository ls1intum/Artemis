package de.tum.cit.aet.artemis.hyperion.runtime.agent;

/** Execution evidence used to decide whether another authoring block is useful. It never establishes save eligibility. */
public interface AuthoringProgress {

    /** @return structured mutation and verification counters for the current workspace */
    Snapshot authoringProgress();

    /** @return true once per job, allowing one bounded repair-model handoff */
    boolean claimRepairModelHandoff();

    /** A failed or stale verification never becomes a passing checkpoint merely because files changed. */
    record Snapshot(long mutations, long checks, int remainingFailures, boolean verified) {
    }
}

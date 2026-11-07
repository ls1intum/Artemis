package de.tum.cit.aet.artemis.hyperion.runtime.agent;

/** Assesses twenty-turn blocks from tool evidence, with one diagnostic block before stopping or handing off. */
final class AuthoringProgressMonitor {

    static final int BLOCK_TURNS = 20;

    enum Decision {
        CONTINUE, VERIFIED, STALLED
    }

    private long lastChecks;

    private long lastCheckedMutations;

    private int bestRemainingFailures;

    private int blocksWithoutProgress;

    AuthoringProgressMonitor(AuthoringProgress.Snapshot initial) {
        lastChecks = initial.checks();
        lastCheckedMutations = initial.mutations();
        // A previous stage's pass is stale after changing stage or files; zero is not a baseline for the new work.
        bestRemainingFailures = initial.remainingFailures() == 0 && !initial.verified() ? Integer.MAX_VALUE : initial.remainingFailures();
    }

    Decision assess(AuthoringProgress.Snapshot current) {
        if (current.verified()) {
            return Decision.VERIFIED;
        }
        boolean checked = current.checks() > lastChecks;
        boolean improved = checked && current.mutations() > lastCheckedMutations && current.remainingFailures() < bestRemainingFailures;
        if (checked) {
            lastCheckedMutations = current.mutations();
            bestRemainingFailures = Math.min(bestRemainingFailures, current.remainingFailures());
        }
        lastChecks = current.checks();
        if (improved) {
            blocksWithoutProgress = 0;
        }
        else {
            blocksWithoutProgress++;
        }
        // Writes are useful pending work, but never reset stagnation without a confirming verification.
        return blocksWithoutProgress >= 2 ? Decision.STALLED : Decision.CONTINUE;
    }
}

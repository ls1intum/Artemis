package de.tum.cit.aet.artemis.buildagent.service;

import java.util.Set;

import org.jspecify.annotations.Nullable;

/** Identifies stale build agents from a complete, nonempty membership lookup. */
final class OfflineBuildAgentDetector {

    private OfflineBuildAgentDetector() {
    }

    /**
     * Determines whether the node behind a stored build agent entry is offline.
     *
     * @param storedNodeIdentifier the identifier the agent stored for itself, as reported by the provider
     * @param liveNodeIdentifiers  the identifiers currently reported as alive by the provider
     * @return true if the entry should be cleaned up
     */
    static boolean isOffline(@Nullable String storedNodeIdentifier, @Nullable Set<String> liveNodeIdentifiers) {
        // An empty snapshot is indistinguishable from a failed lookup (for example a timed-out Redis CLIENT LIST), so
        // never conclude that anything is offline from it. Otherwise a single failed lookup would wipe every agent.
        if (storedNodeIdentifier == null || liveNodeIdentifiers == null || liveNodeIdentifiers.isEmpty()) {
            return false;
        }

        return !liveNodeIdentifiers.contains(storedNodeIdentifier);
    }

}

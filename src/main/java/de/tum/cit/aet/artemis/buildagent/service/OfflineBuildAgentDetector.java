package de.tum.cit.aet.artemis.buildagent.service;

import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Decides whether a build agent entry in the distributed map belongs to a node that is no longer alive.
 *
 * <p>
 * This lives in its own class because the two supported providers report node identity in different namespaces, and
 * conflating them silently disabled cleanup entirely under Redis:
 * <ul>
 * <li><strong>Hazelcast:</strong> build agents connect as clients, so they never appear in the cluster member list.
 * Absence from that list is therefore not evidence that an agent is gone. Only entries whose address looks like a
 * member address (a port shared with a live member) may be cleaned up here; client-mode agents bind to ephemeral ports
 * and are handled by the client-disconnection listener instead.</li>
 * <li><strong>Redis:</strong> there is no member/client distinction. Every node, including every build agent, appears
 * in the client list, so absence from it <em>is</em> decisive.</li>
 * </ul>
 */
final class OfflineBuildAgentDetector {

    private OfflineBuildAgentDetector() {
    }

    /**
     * Determines whether the node behind a stored build agent entry is offline.
     *
     * @param storedNodeIdentifier        the identifier the agent stored for itself, as reported by the provider
     * @param liveNodeIdentifiers         the identifiers currently reported as alive by the provider
     * @param buildAgentsAppearInLiveList whether build agents are expected to appear in {@code liveNodeIdentifiers}
     * @return true if the entry should be cleaned up
     */
    static boolean isOffline(@Nullable String storedNodeIdentifier, @Nullable Set<String> liveNodeIdentifiers, boolean buildAgentsAppearInLiveList) {
        // An empty snapshot is indistinguishable from a failed lookup (for example a timed-out Redis CLIENT LIST), so
        // never conclude that anything is offline from it. Otherwise a single failed lookup would wipe every agent.
        if (storedNodeIdentifier == null || liveNodeIdentifiers == null || liveNodeIdentifiers.isEmpty()) {
            return false;
        }

        if (liveNodeIdentifiers.contains(storedNodeIdentifier)) {
            return false;
        }

        if (buildAgentsAppearInLiveList) {
            return true;
        }

        return looksLikeClusterMemberAddress(storedNodeIdentifier, liveNodeIdentifiers);
    }

    /**
     * Checks whether an address has the shape of a Hazelcast cluster member address, meaning it uses the same port as
     * one of the live members. Cluster members use the configured port (typically 5701), while clients use ephemeral
     * ports assigned by the OS.
     *
     * @param address             the address to classify, expected in {@code [host]:port} form
     * @param liveNodeIdentifiers the addresses of the live cluster members
     * @return true if the address uses a port that a live cluster member also uses
     */
    private static boolean looksLikeClusterMemberAddress(String address, Set<String> liveNodeIdentifiers) {
        if (!address.contains("]:")) {
            return false;
        }
        String addressPort = extractPort(address);
        if (addressPort == null) {
            return false;
        }
        return liveNodeIdentifiers.stream().map(OfflineBuildAgentDetector::extractPort).filter(Objects::nonNull).anyMatch(addressPort::equals);
    }

    /**
     * Extracts the port from an address in {@code [host]:port} form.
     *
     * @param address the address string
     * @return the port, or {@code null} if it cannot be extracted
     */
    @Nullable
    private static String extractPort(@Nullable String address) {
        if (address == null) {
            return null;
        }
        int lastColon = address.lastIndexOf(':');
        if (lastColon >= 0 && lastColon < address.length() - 1) {
            return address.substring(lastColon + 1);
        }
        return null;
    }
}

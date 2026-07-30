package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifies the decision of whether a stored build agent entry belongs to a node that is no longer alive.
 *
 * <p>
 * The two supported providers report node identity in different namespaces, which is what the
 * {@code buildAgentsAppearInLiveList} flag distinguishes:
 * <ul>
 * <li>Hazelcast build agents connect as clients and never appear in the member list, so absence from that list means
 * nothing on its own.</li>
 * <li>The Redis provider has no member/client split; every node appears in the client list, so absence is decisive.</li>
 * </ul>
 */
class OfflineBuildAgentDetectorTest {

    private static final Set<String> HAZELCAST_MEMBERS = Set.of("[192.168.1.1]:5701", "[192.168.1.2]:5701");

    @Test
    void testRedisAgentMissingFromClientListIsOffline() {
        assertThat(OfflineBuildAgentDetector.isOffline("artemis-1001", Set.of("artemis-1002", "artemis-2"), true)).isTrue();
    }

    @Test
    void testRedisAgentPresentInClientListIsOnline() {
        assertThat(OfflineBuildAgentDetector.isOffline("artemis-1001", Set.of("artemis-1001", "artemis-2"), true)).isFalse();
    }

    /**
     * A failed or timed-out {@code CLIENT LIST} lookup yields an empty set. Treating that as "everything is offline"
     * would wipe every registered build agent, so an empty snapshot must never mark anything offline.
     */
    @Test
    void testEmptyLiveNodeSetNeverMarksAgentOffline() {
        assertThat(OfflineBuildAgentDetector.isOffline("artemis-1001", Set.of(), true)).isFalse();
        assertThat(OfflineBuildAgentDetector.isOffline("[192.168.1.9]:5701", Set.of(), false)).isFalse();
    }

    @Test
    void testHazelcastMemberThatLeftTheClusterIsOffline() {
        assertThat(OfflineBuildAgentDetector.isOffline("[192.168.1.9]:5701", HAZELCAST_MEMBERS, false)).isTrue();
    }

    @Test
    void testHazelcastMemberStillInClusterIsOnline() {
        assertThat(OfflineBuildAgentDetector.isOffline("[192.168.1.1]:5701", HAZELCAST_MEMBERS, false)).isFalse();
    }

    /**
     * Hazelcast clients bind to ephemeral ports, so their address never matches a member port. They are cleaned up by
     * the client-disconnection listener and must not be removed here.
     */
    @Test
    void testHazelcastClientWithEphemeralPortIsNotOffline() {
        assertThat(OfflineBuildAgentDetector.isOffline("[192.168.1.9]:54321", HAZELCAST_MEMBERS, false)).isFalse();
    }

    /**
     * Guards the regression this class was extracted for: under Redis the identifier carries no {@code [host]:port}
     * shape, and the previous inline condition reduced to {@code x && !x}, so cleanup could never fire.
     */
    @Test
    void testRedisStyleIdentifierIsNotTreatedAsHazelcastAddress() {
        assertThat(OfflineBuildAgentDetector.isOffline("artemis-1001", HAZELCAST_MEMBERS, false)).isFalse();
    }
}

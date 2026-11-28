package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class OfflineBuildAgentDetectorTest {

    @Test
    void testRedisAgentMissingFromClientListIsOffline() {
        assertThat(OfflineBuildAgentDetector.isOffline("artemis-1001", Set.of("artemis-1002", "artemis-2"))).isTrue();
    }

    @Test
    void testRedisAgentPresentInClientListIsOnline() {
        assertThat(OfflineBuildAgentDetector.isOffline("artemis-1001", Set.of("artemis-1001", "artemis-2"))).isFalse();
    }

    /**
     * A failed or timed-out {@code CLIENT LIST} lookup yields an empty set. Treating that as "everything is offline"
     * would wipe every registered build agent, so an empty snapshot must never mark anything offline.
     */
    @Test
    void testEmptyLiveNodeSetNeverMarksAgentOffline() {
        assertThat(OfflineBuildAgentDetector.isOffline("artemis-1001", Set.of())).isFalse();
    }

    @Test
    void testNullStoredIdentifierIsNotOffline() {
        assertThat(OfflineBuildAgentDetector.isOffline(null, Set.of("artemis-1001"))).isFalse();
        assertThat(OfflineBuildAgentDetector.isOffline("artemis-1001", null)).isFalse();
    }

}

package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

/**
 * Exercises the registry against the local provider. The registry is intentionally backend-agnostic (it is built on the
 * expiring map plus a heartbeat), so the local provider is enough to cover its logic and the map contract itself is
 * covered for every backend by {@link AbstractDistributedDataTest}.
 */
class NodeRegistryServiceTest {

    @Test
    void testHeartbeatPublishesLocalNodeAndDeregisterRemovesIt() {
        NodeRegistryService registry = new NodeRegistryService(Optional.of(new LocalDataProviderService()), "test-instance");

        assertThat(registry.getLiveNodeIds()).as("no node is known before the first heartbeat").isEmpty();

        registry.heartbeat();

        assertThat(registry.getLiveNodeIds()).containsExactly(registry.getLocalNodeId());
        assertThat(registry.isNodeAlive(registry.getLocalNodeId())).isTrue();

        registry.deregister();

        assertThat(registry.getLiveNodeIds()).as("a graceful shutdown must be visible immediately, not after the timeout").isEmpty();
        assertThat(registry.isNodeAlive(registry.getLocalNodeId())).isFalse();
    }

    @Test
    void testRepeatedHeartbeatKeepsExactlyOneEntry() {
        NodeRegistryService registry = new NodeRegistryService(Optional.of(new LocalDataProviderService()), "test-instance");

        registry.heartbeat();
        registry.heartbeat();
        registry.heartbeat();

        assertThat(registry.getLiveNodeIds()).hasSize(1);
    }

    @Test
    void testUnknownNodeIsNotAlive() {
        NodeRegistryService registry = new NodeRegistryService(Optional.of(new LocalDataProviderService()), "test-instance");
        registry.heartbeat();

        assertThat(registry.isNodeAlive("some-other-node")).isFalse();
    }

    /**
     * Deployments without a distributed provider (for example a node configured with neither Hazelcast nor Redis) must
     * not fail on startup or on every scheduled heartbeat.
     */
    @Test
    void testDegradesGracefullyWithoutProvider() {
        NodeRegistryService registry = new NodeRegistryService(Optional.empty(), "test-instance");

        assertThatCode(registry::heartbeat).doesNotThrowAnyException();
        assertThatCode(registry::deregister).doesNotThrowAnyException();
        assertThat(registry.getLiveNodeIds()).isEmpty();
        assertThat(registry.isNodeAlive("any")).isFalse();
        assertThat(registry.getLocalNodeId()).isEqualTo("unknown-node");
    }
}

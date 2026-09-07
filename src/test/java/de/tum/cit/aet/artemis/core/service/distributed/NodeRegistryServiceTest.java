package de.tum.cit.aet.artemis.core.service.distributed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
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
     * A failing heartbeat must not propagate: the scheduler would stop invoking the method and the node would then never
     * re-register, turning a transient blip into permanent absence.
     */
    @Test
    void testFailingHeartbeatIsSwallowed() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        DistributedMap<String, ClusterNodeInfo> nodes = mock(DistributedMap.class);
        when(provider.getLocalMemberAddress()).thenReturn("[10.0.0.1]:5701");
        when(provider.<String, ClusterNodeInfo>getExpiringMap(anyString(), any())).thenReturn(nodes);
        doThrow(new IllegalStateException("cluster unreachable")).when(nodes).put(anyString(), any());

        NodeRegistryService registry = new NodeRegistryService(Optional.of(provider), "instance");

        assertThatCode(registry::heartbeat).doesNotThrowAnyException();
        assertThatCode(registry::deregister).doesNotThrowAnyException();
    }

    /**
     * A Hazelcast identifier carries the port, a Redis client name does not. Both have to yield a usable description,
     * because the admin node overview renders the host and port separately.
     */
    @Test
    void testLocalNodeDescriptionSplitsHostAndPortWhenPresent() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        LocalDataProviderService backing = new LocalDataProviderService();
        when(provider.getLocalMemberAddress()).thenReturn("[10.0.0.1]:5701");
        when(provider.<String, ClusterNodeInfo>getExpiringMap(anyString(), any())).thenAnswer(invocation -> backing.getExpiringMap("clusterNodes", Duration.ofSeconds(35)));

        NodeRegistryService registry = new NodeRegistryService(Optional.of(provider), "artemis-node-1");
        registry.heartbeat();

        assertThat(registry.getLiveNodes()).singleElement().satisfies(node -> {
            assertThat(node.host()).isEqualTo("10.0.0.1");
            assertThat(node.port()).isEqualTo(5701);
            assertThat(node.instanceId()).isEqualTo("artemis-node-1");
        });
    }

    @Test
    void testLocalNodeDescriptionKeepsWholeIdentifierWhenItHasNoPort() {
        NodeRegistryService registry = new NodeRegistryService(Optional.of(new LocalDataProviderService()), "artemis-node-2");
        registry.heartbeat();

        assertThat(registry.getLiveNodes()).singleElement().satisfies(node -> {
            assertThat(node.host()).isEqualTo("localhost");
            assertThat(node.port()).isZero();
        });
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

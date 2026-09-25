package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.core.types.RedisClientInfo;

import reactor.core.publisher.Flux;

class RedisClientListResolverTest {

    @Test
    void monitoringNamesStayReadableWhileOwnershipKeepsEveryIncarnation() {
        var factory = mock(ReactiveRedisConnectionFactory.class);
        var connection = mock(ReactiveRedisConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(connection);
        var first = new RedisNodeIdentity("artemis-core");
        var second = new RedisNodeIdentity("artemis-core");
        when(connection.serverCommands().getClientList()).thenReturn(Flux.just(client(first.connectionName(), "10.0.0.1:1234"), client(second.connectionName(), "10.0.0.2:5678"),
                client("artemis-legacy", "10.0.0.3:1234"), client("foreign", "10.0.0.4:1234")));

        var snapshot = new RedisClientListResolver(factory).resolveClients();
        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.coordinationNodeIds()).containsExactlyInAnyOrder(first.connectionName(), second.connectionName());
        assertThat(snapshot.clientNames()).containsExactlyInAnyOrder("artemis-core", "artemis-legacy");
        assertThat(snapshot.addressesByClientName().get("artemis-core")).containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }

    @Test
    void partialClusterResponseNeverBecomesOwnershipEvidence() {
        var factory = mock(ReactiveRedisConnectionFactory.class);
        var connection = mock(ReactiveRedisClusterConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(connection);
        var firstNode = new RedisClusterNode("10.0.0.1", 6379);
        var secondNode = new RedisClusterNode("10.0.0.2", 6379);
        var identity = new RedisNodeIdentity("artemis-core");
        when(connection.clusterGetNodes()).thenReturn(Flux.just(firstNode, secondNode));
        when(connection.serverCommands().getClientList(firstNode)).thenReturn(Flux.just(client(identity.connectionName(), "10.0.0.3:1234")));
        when(connection.serverCommands().getClientList(secondNode)).thenReturn(Flux.error(new IllegalStateException("node unavailable")));

        var resolver = new RedisClientListResolver(factory);
        var incomplete = resolver.resolveClients();
        assertThat(incomplete.complete()).isFalse();
        assertThat(incomplete.coordinationNodeIds()).isEmpty();
        assertThat(resolver.getClientAddressesByName()).isEmpty();

        when(connection.serverCommands().getClientList(secondNode)).thenReturn(Flux.just(client(identity.connectionName(), "10.0.0.3:5678")));
        var complete = resolver.resolveClients();
        assertThat(complete.complete()).isTrue();
        assertThat(complete.coordinationNodeIds()).containsExactly(identity.connectionName());
        assertThat(complete.addressesByClientName().get("artemis-core")).containsExactly("10.0.0.3");
    }

    private static RedisClientInfo client(String name, String address) {
        var properties = new Properties();
        properties.setProperty("name", name);
        properties.setProperty("addr", address);
        return new RedisClientInfo(properties);
    }
}

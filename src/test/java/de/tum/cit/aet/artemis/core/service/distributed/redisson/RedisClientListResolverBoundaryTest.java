package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.core.types.RedisClientInfo;

import reactor.core.publisher.Flux;

class RedisClientListResolverBoundaryTest {

    @Test
    void standaloneLookupFiltersForeignClientsAndDeduplicatesConnections() {
        var factory = mock(ReactiveRedisConnectionFactory.class);
        var connection = mock(ReactiveRedisConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(connection);
        when(connection.serverCommands().getClientList()).thenReturn(Flux.just(client(null), client("foreign"), client("ARTEMIS-agent"), client("ARTEMIS-agent")));
        var resolver = new RedisClientListResolver(factory);
        var snapshot = resolver.resolveClients();
        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.clientNames()).containsExactly("ARTEMIS-agent");
        assertThat(resolver.getUniqueClients()).containsExactlyInAnyOrderElementsOf(snapshot.clientNames());
        verify(connection, org.mockito.Mockito.times(2)).close();
    }

    @Test
    void emptyClusterTopologyAndFailedStandaloneLookupAreUnknownRatherThanKnownEmpty() {
        var factory = mock(ReactiveRedisConnectionFactory.class);
        var cluster = mock(ReactiveRedisClusterConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(cluster);
        when(cluster.clusterGetNodes()).thenReturn(Flux.empty());
        var resolver = new RedisClientListResolver(factory);
        assertThat(resolver.resolveClients().complete()).isFalse();
        var standalone = mock(ReactiveRedisConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(standalone);
        when(standalone.serverCommands().getClientList()).thenReturn(Flux.error(new IllegalStateException("connection lost")));
        assertThat(resolver.resolveClients().complete()).isFalse();
        assertThat(resolver.getUniqueClients()).isEmpty();
    }

    @Test
    void clusterIncludesClientsFromEveryNode() {
        var factory = mock(ReactiveRedisConnectionFactory.class);
        var cluster = mock(ReactiveRedisClusterConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(cluster);
        var first = new RedisClusterNode("192.0.2.1", 6379);
        var second = new RedisClusterNode("192.0.2.2", 6379);
        when(cluster.clusterGetNodes()).thenReturn(Flux.just(first, second));
        when(cluster.serverCommands().getClientList(first)).thenReturn(Flux.just(client("artemis-agent")));
        when(cluster.serverCommands().getClientList(second)).thenReturn(Flux.just(client("artemis-core")));
        var snapshot = new RedisClientListResolver(factory).resolveClients();
        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.clientNames()).containsExactlyInAnyOrder("artemis-agent", "artemis-core");
    }

    private RedisClientInfo client(String name) {
        Properties properties = new Properties();
        if (name != null) {
            properties.setProperty("name", name);
        }
        return new RedisClientInfo(properties);
    }
}

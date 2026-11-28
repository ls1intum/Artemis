package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisClusterConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.core.types.RedisClientInfo;

import reactor.core.publisher.Flux;

class RedisClientListResolverBoundaryTest {

    @Test
    void standaloneLookupFiltersForeignClientsAndNormalizesIpv4Ipv6AndMissingAddresses() {
        var factory = mock(ReactiveRedisConnectionFactory.class);
        var connection = mock(ReactiveRedisConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(connection);
        when(connection.serverCommands().getClientList()).thenReturn(
                Flux.just(client(null, "1.2.3.4:1"), client("foreign", "1.2.3.4:1"), client("ARTEMIS-agent", "192.0.2.1:1234"), client("ARTEMIS-agent", "192.0.2.1:5678"),
                        client("artemis-ipv6", "[::1]:1234"), client("artemis-unbracketed", "2001:db8::1:1234"), client("artemis-host", "host"), client("artemis-missing", null),
                        client("artemis-blank", " "), client("artemis-malformed", "[]:1234"), client("artemis-open", "[::1"), client("artemis-empty-host", " :1234")));
        var resolver = new RedisClientListResolver(factory);
        var snapshot = resolver.resolveClients();
        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.addressesByClientName()).containsEntry("ARTEMIS-agent", Set.of("192.0.2.1")).containsEntry("artemis-ipv6", Set.of("::1"))
                .containsEntry("artemis-unbracketed", Set.of("2001:db8::1")).containsEntry("artemis-host", Set.of("host")).containsEntry("artemis-missing", Set.of())
                .containsEntry("artemis-blank", Set.of()).containsEntry("artemis-malformed", Set.of()).containsEntry("artemis-open", Set.of())
                .containsEntry("artemis-empty-host", Set.of()).doesNotContainKey("foreign");
        assertThat(resolver.getUniqueClients()).containsExactlyInAnyOrderElementsOf(snapshot.clientNames());
        assertThat(resolver.getClientAddressesByName()).contains(snapshot.addressesByClientName());
        verify(connection, org.mockito.Mockito.times(3)).close();
    }

    @Test
    void emptyClusterTopologyAndFailedStandaloneLookupAreUnknownRatherThanKnownEmpty() {
        var factory = mock(ReactiveRedisConnectionFactory.class);
        var cluster = mock(ReactiveRedisClusterConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(cluster);
        when(cluster.clusterGetNodes()).thenReturn(Flux.empty());
        var resolver = new RedisClientListResolver(factory);
        assertThat(resolver.resolveClients().complete()).isFalse();
        assertThat(resolver.getClientAddressesByName()).isEmpty();
        var standalone = mock(ReactiveRedisConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(standalone);
        when(standalone.serverCommands().getClientList()).thenReturn(Flux.error(new IllegalStateException("connection lost")));
        assertThat(resolver.resolveClients().complete()).isFalse();
        assertThat(resolver.getUniqueClients()).isEmpty();
    }

    @Test
    void clusterMergesObservedHostsAcrossEveryNode() {
        var factory = mock(ReactiveRedisConnectionFactory.class);
        var cluster = mock(ReactiveRedisClusterConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getReactiveConnection()).thenReturn(cluster);
        var first = new RedisClusterNode("192.0.2.1", 6379);
        var second = new RedisClusterNode("192.0.2.2", 6379);
        when(cluster.clusterGetNodes()).thenReturn(Flux.just(first, second));
        when(cluster.serverCommands().getClientList(first)).thenReturn(Flux.just(client("artemis-agent", "192.0.2.3:1111")));
        when(cluster.serverCommands().getClientList(second)).thenReturn(Flux.just(client("artemis-agent", "192.0.2.4:2222")));
        var snapshot = new RedisClientListResolver(factory).resolveClients();
        assertThat(snapshot.complete()).isTrue();
        assertThat(snapshot.addressesByClientName()).isEqualTo(Map.of("artemis-agent", Set.of("192.0.2.3", "192.0.2.4")));
    }

    private RedisClientInfo client(String name, String address) {
        Properties properties = new Properties();
        if (name != null) {
            properties.setProperty("name", name);
        }
        if (address != null) {
            properties.setProperty("addr", address);
        }
        return new RedisClientInfo(properties);
    }
}

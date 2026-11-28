package de.tum.cit.aet.artemis.core.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;

class RedisHealthIndicatorTest {

    private final RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

    private final RedisClientListResolver clients = mock(RedisClientListResolver.class);

    private final DistributedDataProvider provider = mock(DistributedDataProvider.class);

    private final RedisHealthIndicator indicator = new RedisHealthIndicator(factory, clients, provider);

    @Test
    void reportsTheActualProcessIdentityAndMembers() {
        var connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        var members = Set.of("artemis-core", "artemis-agent");
        when(clients.getUniqueClients()).thenReturn(members);
        when(provider.getLocalMemberAddress()).thenReturn("artemis-core");

        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("Artemis Clients", members).containsEntry("Unique Artemis clients", 2).containsEntry("This node client name", "artemis-core");
        verify(connection).close();
    }

    @Test
    void reportsAConnectionFailureEvenWhenTheExceptionHasNoMessage() {
        when(factory.getConnection()).thenThrow(new IllegalStateException());
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void reportsAMissingPingResponse() {
        var connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("ping", "Redis ping failed");
        verify(connection).close();
    }
}

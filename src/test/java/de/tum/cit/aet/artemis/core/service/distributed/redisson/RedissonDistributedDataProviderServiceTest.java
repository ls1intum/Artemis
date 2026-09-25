package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.currentKeyFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

class RedissonDistributedDataProviderServiceTest {

    @Test
    void testNamespacesDataKeysButKeepsCrossReleaseLocksStable() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        var service = new RedissonDistributedDataProviderService(redissonClient, mock(RedisClientListResolver.class), new RedisNodeIdentity("artemis-test"));

        var queue = service.<String>getQueue("jobs");
        service.getLock("scheduler-lock");

        assertThat(queue.getName()).isEqualTo("jobs");
        verify(redissonClient).getQueue(currentKeyFor("jobs"));
        verify(redissonClient).getTopic(currentKeyFor("jobs") + ":queue_notification");
        verify(redissonClient).getLock("scheduler-lock");
    }

    @Test
    void coordinationRequiresCompleteViewContainingThisProcess() {
        var client = mock(RedissonClient.class);
        var resolver = mock(RedisClientListResolver.class);
        var identity = new RedisNodeIdentity("artemis-core");
        var provider = new RedissonDistributedDataProviderService(client, resolver, identity);
        when(resolver.resolveClients()).thenReturn(new RedisClientListResolver.ClientListSnapshot(Map.of(), Set.of(identity.connectionName()), false));
        assertThat(provider.getCoordinationSnapshot()).isEmpty();
        when(resolver.resolveClients()).thenReturn(new RedisClientListResolver.ClientListSnapshot(Map.of(), Set.of("other"), true));
        assertThat(provider.getCoordinationSnapshot()).isEmpty();
        when(resolver.resolveClients()).thenReturn(new RedisClientListResolver.ClientListSnapshot(Map.of(), Set.of(identity.connectionName(), "other"), true));
        var snapshot = provider.getCoordinationSnapshot().orElseThrow();
        assertThat(snapshot.ownerNodeIds()).containsExactlyInAnyOrder(identity.connectionName(), "other");
        assertThat(snapshot.memberQuorumRequired()).isFalse();
        assertThat(provider.getLocalNodeId()).isEqualTo(identity.connectionName());
        when(client.isShutdown()).thenReturn(true);
        assertThat(provider.getCoordinationSnapshot()).isEmpty();
    }

}

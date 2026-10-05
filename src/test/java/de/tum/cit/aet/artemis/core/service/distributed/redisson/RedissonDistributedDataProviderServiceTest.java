package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.currentKeyFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

class RedissonDistributedDataProviderServiceTest {

    @Test
    void testNamespacesDataKeysButKeepsCrossReleaseLocksStable() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        var service = new RedissonDistributedDataProviderService(redissonClient, mock(RedisClientListResolver.class));

        var queue = service.<String>getQueue("jobs");
        service.getLock("scheduler-lock");

        assertThat(queue.getName()).isEqualTo("jobs");
        verify(redissonClient).getQueue(currentKeyFor("jobs"));
        verify(redissonClient).getTopic(currentKeyFor("jobs") + ":queue_notification");
        verify(redissonClient).getLock("scheduler-lock");
    }

    @Test
    void testClientDisconnectionListenerIsNotifiedAboutClientsMissingFromTheNextCompleteSnapshot() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RedisClientListResolver resolver = mock(RedisClientListResolver.class);
        when(resolver.getUniqueClients()).thenReturn(Set.of("node-a", "node-b"));
        when(resolver.resolveClients()).thenReturn(new RedisClientListResolver.ClientListSnapshot(Map.of("node-a", Set.of("10.0.0.1:1")), true));
        var service = new RedissonDistributedDataProviderService(redissonClient, resolver);
        var disconnected = new CopyOnWriteArrayList<String>();

        var listenerId = service.addClientDisconnectionListener(disconnected::add);
        try {
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(disconnected).containsExactly("node-b"));
        }
        finally {
            assertThat(service.removeClientDisconnectionListener(listenerId)).isTrue();
        }
    }
}

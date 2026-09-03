package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import static de.tum.cit.aet.artemis.core.service.distributed.DistributedDataSchema.currentKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        verify(redissonClient).getQueue(currentKey("jobs"));
        verify(redissonClient).getTopic(currentKey("jobs") + ":queue_notification");
        verify(redissonClient).getLock("scheduler-lock");
    }
}

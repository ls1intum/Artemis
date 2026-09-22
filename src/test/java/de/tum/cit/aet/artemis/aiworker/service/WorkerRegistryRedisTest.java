package de.tum.cit.aet.artemis.aiworker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.jms.ConnectionFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.config.AiWorkerProperties;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisNodeIdentity;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

/** Real codec and independent connections, not an in-memory stand-in for Redis ownership. */
@EnabledIf("isDockerAvailable")
class WorkerRegistryRedisTest {

    static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void concurrentCoordinatorsFenceReplacementsAndLateCompletion() throws Exception {
        try (var store = ValkeyTestContainerFactory.create()) {
            store.start();
            var firstIdentity = new RedisNodeIdentity("aiworker-core");
            var secondIdentity = new RedisNodeIdentity("aiworker-core");
            String address = "redis://" + store.getHost() + ":" + store.getMappedPort(6379);
            var firstClient = client(address, firstIdentity);
            var secondClient = client(address, secondIdentity);
            try {
                var firstData = new RedissonDistributedDataProviderService(firstClient, new RedisClientListResolver(new RedissonConnectionFactory(firstClient)), firstIdentity);
                var secondData = new RedissonDistributedDataProviderService(secondClient, new RedisClientListResolver(new RedissonConnectionFactory(secondClient)), secondIdentity);
                var properties = new AiWorkerProperties("tcp://broker:61617?sslEnabled=true", "core", "test", List.of("worker"), Duration.ofSeconds(30), Duration.ofSeconds(45));
                var first = new WorkerRegistryService(properties, mock(ConnectionFactory.class), new WorkerMessageCodecApi(), firstData);
                var second = new WorkerRegistryService(properties, mock(ConnectionFactory.class), new WorkerMessageCodecApi(), secondData);
                var capability = new WorkloadCapabilityDTO("document-check", 1, "text");
                UUID incarnation = UUID.randomUUID();
                String digest = "sha256:" + "a".repeat(64);
                var heartbeat = new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker", incarnation, 1, Instant.now(), WorkerEventType.HEARTBEAT, null, true, digest, null,
                        null, new WorkerCapacityDTO(1, List.of()), capability);
                first.recordPresence("worker", heartbeat);
                var barrier = new CyclicBarrier(2);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var claims = List.of(first, second).stream().map(registry -> executor.submit(() -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        try {
                            return registry.claim("job", "document:1", capability);
                        }
                        catch (ServiceUnavailableAlertException occupied) {
                            return null;
                        }
                    })).toList();
                    var a = claims.getFirst().get(10, TimeUnit.SECONDS);
                    var b = claims.getLast().get(10, TimeUnit.SECONDS);
                    assertThat(a == null ^ b == null).isTrue();
                    var old = a == null ? b : a;
                    second.release(old);
                    var replacement = first.claim("new", "document:2", capability);
                    first.recordPresence("worker", new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(), WorkerEventType.HEARTBEAT,
                            replacement.identity(), false, digest, null, null, new WorkerCapacityDTO(1, List.of(replacement.identity())), capability));
                    second.recordCompletion(old, new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker", incarnation, 3, Instant.now(), WorkerEventType.ERROR,
                            old.identity(), true, digest, "late", null, new WorkerCapacityDTO(1, List.of()), capability));
                    second.release(old);
                    assertThat(first.renew(old)).isFalse();
                    assertThat(second.renew(replacement)).isTrue();
                    first.release(replacement);
                    assertThatThrownBy(() -> second.claim("still-busy", "document:3", capability)).isInstanceOf(ServiceUnavailableAlertException.class);
                    // A restarted process cannot displace a still-present incarnation.
                    second.recordPresence("worker", new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker", UUID.randomUUID(), 4, Instant.now(), WorkerEventType.HEARTBEAT,
                            null, true, digest, null, null, new WorkerCapacityDTO(1, List.of()), capability));
                    assertThat(first.workerStatuses().getFirst().incarnation()).isEqualTo(incarnation);
                }
            }
            finally {
                firstClient.shutdown();
                secondClient.shutdown();
            }
        }
    }

    private RedissonClient client(String address, RedisNodeIdentity identity) {
        var config = new Config();
        config.useSingleServer().setAddress(address).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer(identity).customize(config);
        return Redisson.create(config);
    }
}

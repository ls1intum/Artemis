package de.tum.cit.aet.artemis.aiworker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionClaimDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerTransport;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

class WorkerRegistryClusterTest {

    @Test
    @EnabledIf("dockerAvailable")
    void twoRedisClientsShareFourSlotsWithoutDoubleAdmission() throws Exception {
        try (var store = ValkeyTestContainerFactory.create()) {
            store.start();
            String address = "redis://" + store.getHost() + ":" + store.getMappedPort(6379);
            var first = redisClient(address);
            var second = redisClient(address);
            try {
                var properties = new AiWorkerProperties(List.of("worker"), Duration.ofSeconds(30), Duration.ofSeconds(45));
                var firstCore = registry(properties, first);
                var secondCore = registry(properties, second);
                firstCore.recordPresence("worker", event(WorkerCommandDTO.PROTOCOL_VERSION, "worker", UUID.randomUUID(), 1, Instant.now(), WorkerEventType.HEARTBEAT, null, true,
                        "sha256:" + "a".repeat(64), null, null, null).withCapacity(new WorkerCapacityDTO(4, List.of())));
                var barrier = new CyclicBarrier(4);
                try (var executor = Executors.newFixedThreadPool(4)) {
                    var claims = new java.util.ArrayList<java.util.concurrent.Future<ExecutionClaimDTO>>();
                    for (int index = 0; index < 4; index++) {
                        final int id = index;
                        claims.add(executor.submit(() -> {
                            barrier.await(10, TimeUnit.SECONDS);
                            return (id % 2 == 0 ? firstCore : secondCore).claim("job-" + id, "resource-" + id, CAPABILITY);
                        }));
                    }
                    var admitted = new java.util.ArrayList<ExecutionClaimDTO>();
                    for (var claim : claims) {
                        admitted.add(claim.get(10, TimeUnit.SECONDS));
                    }
                    assertThat(admitted).extracting(claim -> claim.identity().slot()).containsExactlyInAnyOrder(0, 1, 2, 3);
                    assertThatThrownBy(() -> secondCore.claim("overflow", "resource-5", CAPABILITY)).isInstanceOf(ServiceUnavailableAlertException.class);
                    var released = admitted.removeFirst();
                    secondCore.release(released);
                    var replacement = firstCore.claim("replacement", "resource-6", CAPABILITY);
                    firstCore.release(released);
                    assertThat(secondCore.renew(replacement)).isTrue();
                    assertThat(admitted).allMatch(secondCore::renew);
                }
            }
            finally {
                second.shutdown();
                first.shutdown();
            }
        }
    }

    private static WorkerRegistryService registry(AiWorkerProperties properties, RedissonClient client) {
        var provider = new RedissonDistributedDataProviderService(client, new RedisClientListResolver(new RedissonConnectionFactory(client)));
        return new WorkerRegistryService(properties, new WorkerTransport(provider, new WorkerMessageCodecApi()), provider);
    }

    private static RedissonClient redisClient(String address) {
        var config = new Config();
        config.useSingleServer().setAddress(address);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer().customize(config);
        return Redisson.create(config);
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static final WorkloadCapabilityDTO CAPABILITY = new WorkloadCapabilityDTO("text-analysis", 1, "text");

    private static WorkerEventDTO event(int protocol, String worker, UUID incarnation, long sequence, Instant time, WorkerEventType type, ExecutionIdentityDTO identity,
            boolean ready, String image, String message, Object unusedActivity, Object unusedOutput) {
        return new WorkerEventDTO(protocol, worker, incarnation, sequence, time, type, identity, ready, image, message, null,
                new WorkerCapacityDTO(1, type == WorkerEventType.HEARTBEAT && identity != null ? List.of(identity) : List.of()), CAPABILITY);
    }

}

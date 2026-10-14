package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.testcontainers.DockerClientFactory;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.Hazelcast;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.config.AiWorkerProperties;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.aiworker.service.WorkerRegistryService;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisNodeIdentity;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

/** Checks callback retry and eventual abandoned-delivery cleanup across independent provider clients. */
class WorkerTransportProviderTest {

    @Test
    void hazelcastClientSharesAcknowledgedEventWithCore() {
        var config = new com.hazelcast.config.Config().setClusterName("worker-transport-" + UUID.randomUUID());
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        var first = Hazelcast.newHazelcastInstance(config);
        try {
            var clientConfig = new ClientConfig().setClusterName(config.getClusterName());
            clientConfig.getNetworkConfig().addAddress("127.0.0.1:" + first.getCluster().getLocalMember().getSocketAddress().getPort());
            var second = HazelcastClient.newHazelcastClient(clientConfig);
            try {
                verifyDelivery(new HazelcastDistributedDataProviderService(first), new HazelcastDistributedDataProviderService(second));
            }
            finally {
                second.shutdown();
            }
        }
        finally {
            first.shutdown();
        }
    }

    @Test
    @EnabledIf("dockerAvailable")
    void redisClientsShareAcknowledgedEvent() {
        try (var store = ValkeyTestContainerFactory.create()) {
            store.start();
            String address = "redis://" + store.getHost() + ":" + store.getMappedPort(6379);
            var first = redis(address);
            var second = redis(address);
            try {
                verifyDelivery(
                        new RedissonDistributedDataProviderService(first, new RedisClientListResolver(new RedissonConnectionFactory(first)),
                                new RedisNodeIdentity("aiworker-test")),
                        new RedissonDistributedDataProviderService(second, new RedisClientListResolver(new RedissonConnectionFactory(second)),
                                new RedisNodeIdentity("aiworker-test")));
            }
            finally {
                first.shutdown();
                second.shutdown();
            }
        }
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static org.redisson.api.RedissonClient redis(String address) {
        var config = new Config();
        config.useSingleServer().setAddress(address).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer(new RedisNodeIdentity("aiworker-test")).customize(config);
        return Redisson.create(config);
    }

    @Test
    void localProviderRetainsLiveEvidenceAndReclaimsAbandonedDelivery() {
        var provider = new LocalDataProviderService();
        verifyDelivery(provider, provider);
    }

    private static void verifyDelivery(DistributedDataProvider writerData, DistributedDataProvider readerData) {
        var writer = new WorkerTransport(writerData, new WorkerMessageCodecApi());
        var reader = new WorkerTransport(readerData, new WorkerMessageCodecApi());
        var identity = new ExecutionIdentityDTO("job", "document:1", UUID.randomUUID(), "worker-1", UUID.randomUUID(), 0);
        var event = new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", identity.workerIncarnation(), 1, Instant.now(), WorkerEventType.FINISHED, identity, true,
                "sha256:" + "a".repeat(64), null, "result".repeat(100_000), new WorkerCapacityDTO(1, List.of()), new WorkloadCapabilityDTO("document-check", 1, "text"));
        writer.publish(event);
        assertThatThrownBy(() -> reader.receive(identity, _ -> {
            throw new IllegalStateException("apply failed");
        })).isInstanceOf(IllegalStateException.class);
        var applied = new AtomicInteger();
        reader.receive(identity, observed -> {
            assertThat(observed).isEqualTo(event);
            applied.incrementAndGet();
        });
        writer.publish(event);
        reader.receive(identity, _ -> applied.incrementAndGet());
        assertThat(applied).hasValue(1);
        verifyPruning(writerData, readerData, writer, reader);
    }

    private static void verifyPruning(DistributedDataProvider writerData, DistributedDataProvider readerData, WorkerTransport writer, WorkerTransport reader) {
        var properties = new AiWorkerProperties(List.of("worker-1"), Duration.ofSeconds(30), Duration.ofSeconds(45));
        var registry = new WorkerRegistryService(properties, reader, readerData);
        var capability = new WorkloadCapabilityDTO("document-check", 1, "text");
        UUID incarnation = UUID.randomUUID();
        String digest = "sha256:" + "a".repeat(64);
        writer.publish(new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", incarnation, 2, Instant.now(), WorkerEventType.HEARTBEAT, null, true, digest, null, null,
                new WorkerCapacityDTO(1, List.of()), capability));
        registry.refreshPresence();
        var abandoned = registry.claim("abandoned", "document:2", capability);
        var oldEvent = new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", incarnation, 3, Instant.now(), WorkerEventType.CHECKPOINT, abandoned.identity(), true,
                digest, null, "abandoned checkpoint", new WorkerCapacityDTO(1, List.of()), capability);
        writer.publish(oldEvent);
        readerData.<String, UUID>getExpiringMap("aiworker-leases", properties.leaseTtl()).remove("worker-1", abandoned.identity().executionId());
        var live = registry.claim("live", "document:3", capability);
        var liveEvent = new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", incarnation, 4, Instant.now(), WorkerEventType.FINISHED, live.identity(), true, digest,
                null, "live output", new WorkerCapacityDTO(1, List.of()), capability);
        writer.publish(liveEvent);

        registry.pruneUnclaimedDeliveries();
        assertThat(reader.receive(abandoned.identity(), _ -> {
            throw new AssertionError("abandoned event remained");
        })).isFalse();
        assertThat(registry.renew(live)).isTrue();
        writer.publish(oldEvent);
        assertThat(writerData.getMap("aiworker-event-chunks").isEmpty()).isFalse();
        String orphan = "worker-1:" + abandoned.identity().executionId() + ":0000000000000000005";
        writerData.<String, String>getMap("aiworker-event-chunks").put(orphan + ":0", "late orphan chunk");
        writerData.<String, String>getExpiringMap("aiworker-event-acknowledgements", Duration.ofHours(4)).put(orphan, "late orphan acknowledgement");
        registry.pruneUnclaimedDeliveries();
        assertThat(readerData.getMap("aiworker-event-chunks").keySet()).noneMatch(key -> key.toString().startsWith("worker-1:" + abandoned.identity().executionId() + ":"));
        assertThat(reader.receive(live.identity(), observed -> assertThat(observed).isEqualTo(liveEvent))).isTrue();
        assertThat(readerData.getMap("aiworker-events-worker-1").isEmpty()).isTrue();
        assertThat(readerData.getMap("aiworker-event-chunks").isEmpty()).isTrue();
        assertThat(readerData.getExpiringMap("aiworker-event-acknowledgements", Duration.ofHours(4)).keySet()).doesNotContain(orphan);
        registry.release(live);
    }
}

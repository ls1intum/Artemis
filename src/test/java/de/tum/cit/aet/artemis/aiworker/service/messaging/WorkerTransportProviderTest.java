package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

/** Checks that independent provider clients see one retained event after callback failure. */
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
                verifyDelivery(new WorkerTransport(new HazelcastDistributedDataProviderService(first), new WorkerMessageCodecApi()),
                        new WorkerTransport(new HazelcastDistributedDataProviderService(second), new WorkerMessageCodecApi()));
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
                        new WorkerTransport(new RedissonDistributedDataProviderService(first, new RedisClientListResolver(new RedissonConnectionFactory(first))),
                                new WorkerMessageCodecApi()),
                        new WorkerTransport(new RedissonDistributedDataProviderService(second, new RedisClientListResolver(new RedissonConnectionFactory(second))),
                                new WorkerMessageCodecApi()));
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
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer().customize(config);
        return Redisson.create(config);
    }

    private static void verifyDelivery(WorkerTransport writer, WorkerTransport reader) {
        var identity = new ExecutionIdentityDTO("job", "document:1", UUID.randomUUID(), "worker-1", UUID.randomUUID(), 0);
        var event = new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", identity.workerIncarnation(), 1, Instant.now(), WorkerEventType.FINISHED, identity, true,
                "sha256:" + "a".repeat(64), null, "result", new WorkerCapacityDTO(1, List.of()), new WorkloadCapabilityDTO("document-check", 1, "text"));
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
    }
}

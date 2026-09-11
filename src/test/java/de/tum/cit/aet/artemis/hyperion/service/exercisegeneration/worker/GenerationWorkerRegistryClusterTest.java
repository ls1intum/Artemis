package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
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

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionWorkerProperties;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCapacity;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;

class GenerationWorkerRegistryClusterTest {

    @Test
    void twoDataMembersShareFourSlotsWithoutDoubleAdmission() throws Exception {
        String cluster = "worker-slots-" + UUID.randomUUID();
        var first = Hazelcast.newHazelcastInstance(config(cluster, List.of()));
        try {
            int port = first.getCluster().getLocalMember().getSocketAddress().getPort();
            var second = Hazelcast.newHazelcastInstance(config(cluster, List.of("127.0.0.1:" + port)));
            try {
                await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(first.getCluster().getMembers()).hasSize(2));
                var properties = new HyperionWorkerProperties("tcp://broker:61617?sslEnabled=true", "core", "test-password", List.of("worker"), Duration.ofSeconds(30),
                        Duration.ofSeconds(45));
                var firstCore = new GenerationWorkerRegistryService(properties, mock(ConnectionFactory.class), new WorkerMessageCodec(),
                        new HazelcastDistributedDataProviderService(first));
                var secondCore = new GenerationWorkerRegistryService(properties, mock(ConnectionFactory.class), new WorkerMessageCodec(),
                        new HazelcastDistributedDataProviderService(second));
                firstCore.recordPresence("worker", new WorkerEvent(WorkerCommand.PROTOCOL_VERSION, "worker", UUID.randomUUID(), 1, Instant.now(), WorkerEvent.Type.HEARTBEAT, null,
                        true, "sha256:" + "a".repeat(64), null, null, null).withCapacity(new WorkerCapacity(4, List.of())));
                var barrier = new CyclicBarrier(4);
                try (var executor = Executors.newFixedThreadPool(4)) {
                    var claims = new java.util.ArrayList<java.util.concurrent.Future<GenerationWorkerRegistryService.Claim>>();
                    for (int index = 0; index < 4; index++) {
                        final int id = index;
                        claims.add(executor.submit(() -> {
                            barrier.await(10, TimeUnit.SECONDS);
                            return (id % 2 == 0 ? firstCore : secondCore).claim("job-" + id, id + 1);
                        }));
                    }
                    var admitted = new java.util.ArrayList<GenerationWorkerRegistryService.Claim>();
                    for (var claim : claims) {
                        admitted.add(claim.get(10, TimeUnit.SECONDS));
                    }
                    assertThat(admitted).extracting(claim -> claim.identity().slot()).containsExactlyInAnyOrder(0, 1, 2, 3);
                    assertThatThrownBy(() -> secondCore.claim("overflow", 5)).isInstanceOf(ServiceUnavailableAlertException.class);
                    var released = admitted.removeFirst();
                    secondCore.release(released);
                    var replacement = firstCore.claim("replacement", 6);
                    firstCore.release(released);
                    assertThat(secondCore.renew(replacement)).isTrue();
                    assertThat(admitted).allMatch(secondCore::renew);
                }
            }
            finally {
                second.shutdown();
            }
        }
        finally {
            first.shutdown();
        }
    }

    private Config config(String name, List<String> seeds) {
        var config = new Config().setClusterName(name);
        config.setProperty("hazelcast.operation.thread.count", "2").setProperty("hazelcast.operation.generic.thread.count", "2");
        var network = config.getNetworkConfig();
        network.setPortAutoIncrement(true);
        network.getInterfaces().setEnabled(true).setInterfaces(List.of("127.0.0.1"));
        network.getJoin().getAutoDetectionConfig().setEnabled(false);
        network.getJoin().getMulticastConfig().setEnabled(false);
        network.getJoin().getTcpIpConfig().setEnabled(true).setMembers(seeds);
        return config;
    }
}

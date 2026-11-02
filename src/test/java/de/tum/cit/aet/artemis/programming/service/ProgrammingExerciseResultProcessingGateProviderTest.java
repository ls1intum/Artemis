package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.testcontainers.DockerClientFactory;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.Hazelcast;

import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

/** Verifies shared admission and exclusive draining across independent provider clients. */
class ProgrammingExerciseResultProcessingGateProviderTest {

    private static final long EXERCISE_ID = 42L;

    private static final String LOCK_NAME = "programming-exercise-result-processing-42";

    @Test
    void hazelcastClientsShareWriterPermitsAndDeletion() throws Exception {
        var config = new com.hazelcast.config.Config().setClusterName("result-processing-" + UUID.randomUUID());
        config.getNetworkConfig().setPort(0).setPortAutoIncrement(false);
        config.getNetworkConfig().getInterfaces().setEnabled(true).addInterface("127.0.0.1");
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        var member = Hazelcast.newHazelcastInstance(config);
        try {
            var clientConfig = new ClientConfig().setClusterName(config.getClusterName());
            clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(10_000);
            clientConfig.getNetworkConfig().addAddress("127.0.0.1:" + member.getCluster().getLocalMember().getSocketAddress().getPort());
            var client = HazelcastClient.newHazelcastClient(clientConfig);
            try {
                verifyCoordination(new HazelcastDistributedDataProviderService(member), new HazelcastDistributedDataProviderService(client));
            }
            finally {
                client.shutdown();
            }
        }
        finally {
            member.shutdown();
        }
    }

    @Test
    @EnabledIf("dockerAvailable")
    void redisClientsShareWriterPermitsAndDeletion() throws Exception {
        try (var store = ValkeyTestContainerFactory.create()) {
            store.start();
            String address = "redis://" + store.getHost() + ":" + store.getMappedPort(6379);
            var first = redis(address);
            var second = redis(address);
            try {
                verifyCoordination(new RedissonDistributedDataProviderService(first, new RedisClientListResolver(new RedissonConnectionFactory(first))),
                        new RedissonDistributedDataProviderService(second, new RedisClientListResolver(new RedissonConnectionFactory(second))));
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

    private static RedissonClient redis(String address) {
        var config = new org.redisson.config.Config();
        config.useSingleServer().setAddress(address).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer().customize(config);
        return Redisson.create(config);
    }

    private static void verifyCoordination(DistributedDataProvider first, DistributedDataProvider second) throws Exception {
        var firstGate = new ProgrammingExerciseResultProcessingGate(first, 2, Duration.ofSeconds(10));
        var secondGate = new ProgrammingExerciseResultProcessingGate(second, 2, Duration.ofSeconds(10));
        DistributedMap<Long, Boolean> firstMarker = first.getExpiringMap("result-processing-test-deletions", Duration.ofMinutes(1));
        DistributedMap<Long, Boolean> secondMarker = second.getExpiringMap("result-processing-test-deletions", Duration.ofMinutes(1));
        CountDownLatch admitted = new CountDownLatch(2);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        try (var workers = Executors.newCachedThreadPool()) {
            var firstWriter = workers.submit(() -> holdWriter(firstGate, firstMarker, admitted, releaseFirst));
            var secondWriter = workers.submit(() -> holdWriter(secondGate, secondMarker, admitted, releaseSecond));
            try {
                assertThat(admitted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(firstGate.tryAdmit(EXERCISE_ID, () -> firstMarker.get(EXERCISE_ID) != null)).isEmpty();
                var deletion = workers.submit(() -> {
                    try (var permit = secondGate.awaitDeletion(EXERCISE_ID, () -> secondMarker.putIfAbsent(EXERCISE_ID, true) == null, () -> secondMarker.remove(EXERCISE_ID))) {
                        assertThat(releaseFirst.getCount()).isZero();
                        assertThat(releaseSecond.getCount()).isZero();
                    }
                });
                await().atMost(Duration.ofSeconds(5)).until(() -> firstMarker.get(EXERCISE_ID) != null);
                releaseFirst.countDown();
                firstWriter.get(5, TimeUnit.SECONDS);
                assertThat(deletion.isDone()).as("the second client's writer still excludes deletion").isFalse();
                releaseSecond.countDown();
                secondWriter.get(5, TimeUnit.SECONDS);
                deletion.get(5, TimeUnit.SECONDS);
                assertThat(firstMarker.get(EXERCISE_ID)).isNull();
            }
            finally {
                releaseFirst.countDown();
                releaseSecond.countDown();
            }

            CountDownLatch held = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var writer = workers.submit(() -> {
                var lock = first.getLock(LOCK_NAME + "-permit-1");
                lock.lock();
                try {
                    held.countDown();
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                }
                finally {
                    lock.unlock();
                }
                return null;
            });
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                var shortGate = new ProgrammingExerciseResultProcessingGate(second, 2, Duration.ofMillis(100));
                assertThatThrownBy(() -> shortGate.awaitDeletion(EXERCISE_ID, () -> secondMarker.putIfAbsent(EXERCISE_ID, true) == null, () -> secondMarker.remove(EXERCISE_ID)))
                        .isInstanceOf(ServiceUnavailableException.class);
                assertThat(firstMarker.get(EXERCISE_ID)).isNull();
                for (String name : new String[] { LOCK_NAME, LOCK_NAME + "-permit-0" }) {
                    var lock = first.getLock(name);
                    assertThat(lock.tryLock(Duration.ZERO)).as("partial ownership released across clients: %s", name).isTrue();
                    lock.unlock();
                }
            }
            finally {
                release.countDown();
                writer.get(5, TimeUnit.SECONDS);
            }
        }
    }

    private static void holdWriter(ProgrammingExerciseResultProcessingGate gate, DistributedMap<Long, Boolean> marker, CountDownLatch admitted, CountDownLatch release) {
        try (var permit = gate.tryAdmit(EXERCISE_ID, () -> marker.get(EXERCISE_ID) != null).orElseThrow()) {
            admitted.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}

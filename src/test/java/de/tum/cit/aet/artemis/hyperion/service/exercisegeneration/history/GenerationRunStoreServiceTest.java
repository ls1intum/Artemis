package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.core.Hazelcast;

import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisNodeIdentity;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

class GenerationRunStoreServiceTest {

    @Test
    void ownerListingUsesItsIndexAfterAdmission() {
        var store = new GenerationRunStoreService(new LocalDataProviderService(), Duration.ofMinutes(1));
        store.save(run("indexed"));
        @SuppressWarnings("unchecked")
        DistributedMap<String, AuthoringRun> activity = (DistributedMap<String, AuthoringRun>) ReflectionTestUtils.getField(store, "runs");
        var observedActivity = spy(activity);
        ReflectionTestUtils.setField(store, "runs", observedActivity);

        assertThat(store.findOwnedBefore(7, null, PageRequest.of(0, 1))).extracting(AuthoringRun::getJobId).containsExactly("indexed");
        verify(observedActivity, never()).values();
    }

    @Test
    void failedOwnerIndexWriteRejectsAdmissionAndRemovesTheUnlistedRun() {
        var store = new GenerationRunStoreService(new LocalDataProviderService(), Duration.ofMinutes(1));
        @SuppressWarnings("unchecked")
        DistributedMap<Long, List<GenerationRunStoreService.RunReference>> index = (DistributedMap<Long, List<GenerationRunStoreService.RunReference>>) ReflectionTestUtils
                .getField(store, "ownerRuns");
        var failingIndex = spy(index);
        doThrow(new IllegalStateException("index unavailable")).when(failingIndex).put(eq(7L), any(), any(Duration.class));
        ReflectionTestUtils.setField(store, "ownerRuns", failingIndex);

        assertThatThrownBy(() -> store.save(run("not-listed"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("index unavailable");
        assertThat(store.findByJobId("not-listed")).isEmpty();
    }

    @Test
    void firstOwnerLookupBackfillsRunsAdmittedBeforeTheIndexExisted() {
        var provider = new LocalDataProviderService();
        var oldRun = run("old-run");
        oldRun.setId(1L);
        provider.<String, AuthoringRun>getExpiringMap("hyperion-authoring-activity", Duration.ofMinutes(1)).put("old-run", oldRun);
        var store = new GenerationRunStoreService(provider, Duration.ofMinutes(1));

        assertThat(store.findOwnedBefore(7, null, PageRequest.of(0, 50))).extracting(AuthoringRun::getJobId).containsExactly("old-run");
    }

    @Test
    void savedRunRefreshesTheUndoWindowAtCompletion() throws Exception {
        Duration retention = Duration.ofMinutes(1);
        var provider = new LocalDataProviderService();
        var store = new GenerationRunStoreService(provider, retention);
        store.save(run("long-run"));
        assertThat(store.linkBeforeVersion("long-run", 1, "main", Instant.now())).isOne();
        provider.<String, AuthoringRun>getExpiringMap("hyperion-authoring-activity", retention).refreshTimeToLive("long-run", Duration.ofSeconds(2));
        provider.<Long, GenerationRunStoreService.Mutation>getExpiringMap("hyperion-authoring-latest-mutation", retention).refreshTimeToLive(12L, Duration.ofSeconds(2));
        assertThat(store.complete("long-run", AuthoringRun.Status.SAVED, Instant.now(), true)).isOne();

        Thread.sleep(2200);
        assertThat(store.findByJobId("long-run")).isPresent();
        assertThat(store.findLatestMutation(12)).extracting(AuthoringRun::getJobId).containsExactly("long-run");
    }

    @Test
    void localProviderRetainsOnlyBoundedDetachedActivity() throws Exception {
        verifyStore(new LocalDataProviderService());
    }

    @Test
    void hazelcastSupportsAtomicActivityAndExpiry() throws Exception {
        var config = new com.hazelcast.config.Config().setClusterName("activity-" + UUID.randomUUID());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        var instance = Hazelcast.newHazelcastInstance(config);
        try {
            verifyStore(new HazelcastDistributedDataProviderService(instance));
        }
        finally {
            instance.shutdown();
        }
    }

    @Test
    void redisSupportsAtomicActivityAndExpiry() throws Exception {
        try (var redis = ValkeyTestContainerFactory.create()) {
            redis.start();
            var identity = new RedisNodeIdentity("artemis-core");
            var config = new Config();
            config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
            new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer(identity).customize(config);
            var client = Redisson.create(config);
            try {
                verifyStore(new RedissonDistributedDataProviderService(client, new RedisClientListResolver(new RedissonConnectionFactory(client)), identity));
            }
            finally {
                client.shutdown();
            }
        }
    }

    private void verifyStore(DistributedDataProvider provider) throws Exception {
        var store = new GenerationRunStoreService(provider, Duration.ofMinutes(1));
        var otherNode = new GenerationRunStoreService(provider, Duration.ofMinutes(1));
        var source = run("first");
        var saved = store.save(source);
        source.setOwnerId(99L);
        saved.setOwnerId(99L);
        assertThat(otherNode.findByJobId("first").orElseThrow().getOwnerId()).isEqualTo(7L);
        assertThatThrownBy(() -> otherNode.save(run("first"))).isInstanceOf(IllegalStateException.class);
        assertThat(store.findByOwnerIdAndJobIdIn(99, List.of("first"))).isEmpty();
        assertThat(store.findByOwnerIdAndJobIdIn(7, List.of("first", "first"))).hasSize(1);
        assertThat(store.findOwnedBefore(7, null, PageRequest.of(0, 50))).hasSize(1);
        assertThat(otherNode.findOwnedBefore(7, null, PageRequest.of(0, 50))).hasSize(1);

        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return store.linkBeforeVersion("first", 1, "main", Instant.now());
            });
            var second = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return otherNode.linkBeforeVersion("first", 2, "other", Instant.now());
            });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isOne();
        }
        assertThat(store.linkAfterVersion("first", 3)).isOne();
        assertThat(store.complete("first", AuthoringRun.Status.SAVED, Instant.now(), true)).isOne();
        assertThat(otherNode.complete("first", AuthoringRun.Status.ERROR, Instant.now(), false)).isZero();
        assertThat(otherNode.markRestoreStarted("first", 4, Instant.now())).isZero();
        assertThat(otherNode.markRestoreStarted("first", 3, Instant.now())).isOne();
        assertThat(store.findByJobId("first").orElseThrow().getRestoreStartedAt()).isNotNull();
        assertThat(store.markReverted("first", 4, Instant.now())).isZero();

        var next = store.save(run("second"));
        assertThat(store.findOwnedBefore(7, next.getId(), PageRequest.of(0, 50))).extracting(AuthoringRun::getJobId).containsExactly("first");
        var anotherOwner = run("another-owner");
        anotherOwner.setOwnerId(8L);
        store.save(anotherOwner);
        assertThat(otherNode.findOwnedBefore(7, null, PageRequest.of(0, 1))).extracting(AuthoringRun::getJobId).containsExactly("second");
        assertThat(otherNode.findOwnedBefore(8, null, PageRequest.of(0, 50))).extracting(AuthoringRun::getJobId).containsExactly("another-owner");
        assertThat(store.linkBeforeVersion("second", 5, "main", Instant.now())).isOne();
        assertThat(store.findLatestMutation(12)).extracting(AuthoringRun::getJobId).containsExactly("second");
        var activity = provider.<String, AuthoringRun>getExpiringMap("hyperion-authoring-activity", Duration.ofMinutes(1));
        activity.refreshTimeToLive("second", Duration.ofMillis(100));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(otherNode.findByJobId("second")).isEmpty());
        assertThat(otherNode.complete("second", AuthoringRun.Status.SAVED, Instant.now(), true)).isZero();
        assertThat(store.findLatestMutation(12)).isEmpty();
        assertThat(store.markReverted("first", 3, Instant.now())).isOne();
        assertThat(store.markReverted("first", 3, Instant.now())).isZero();
        assertThat(store.findLatestMutation(12)).isEmpty();
        var concurrentStart = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> {
                assertThat(concurrentStart.await(10, TimeUnit.SECONDS)).isTrue();
                return store.save(run("parallel-one"));
            });
            var two = executor.submit(() -> {
                assertThat(concurrentStart.await(10, TimeUnit.SECONDS)).isTrue();
                return otherNode.save(run("parallel-two"));
            });
            concurrentStart.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
        assertThat(store.findOwnedBefore(7, null, PageRequest.of(0, 50))).extracting(AuthoringRun::getJobId).contains("parallel-one", "parallel-two");
        activity.clear();
        assertThat(store.findOwnedBefore(7, null, PageRequest.of(0, 50))).isEmpty();
    }

    private AuthoringRun run(String jobId) {
        var run = new AuthoringRun();
        run.setJobId(jobId);
        run.setExerciseId(12L);
        run.setOwnerId(7L);
        run.setKind(AuthoringRun.Kind.CREATE);
        run.setStartedAt(Instant.now());
        return run;
    }
}

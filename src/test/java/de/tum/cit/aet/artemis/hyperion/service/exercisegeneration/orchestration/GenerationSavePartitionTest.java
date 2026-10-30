package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.impl.HazelcastInstanceProxy;
import com.hazelcast.splitbrainprotection.SplitBrainProtectionException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.EurekaInstanceHelper;
import de.tum.cit.aet.artemis.core.config.HazelcastConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Real Hazelcast partitions while an actual generation job is paused in its non-cancellable save phase. */
class GenerationSavePartitionTest {

    @Test
    void configuredSingletonCanAdmitAndSave() {
        HazelcastInstance member = Hazelcast.newHazelcastInstance(configuration("generation-single-" + UUID.randomUUID(), 1, List.of()));
        try {
            var local = member.getCluster().getLocalMember();
            assertThat(local.getAttribute("artemis.hyperion.expected-data-members")).isEqualTo("1");
            assertThat(local.getAttribute("artemis.hyperion.exercise-generation-enabled")).isEqualTo("false");
            String policyName = member.getConfig().getMapConfig(Constants.HYPERION_GENERATION_JOBS_MAP).getSplitBrainProtectionName();
            var configured = member.getConfig().getSplitBrainProtectionConfig(policyName);
            assertThat(configured.getFunctionImplementation().apply(member.getCluster().getMembers())).isTrue();
            // Hazelcast evaluates this function asynchronously; do not race its first heartbeat assessment.
            await().atMost(Duration.ofSeconds(15)).until(() -> member.getSplitBrainProtectionService().getSplitBrainProtection(policyName).hasMinimumSize());
            var jobs = jobs(member);
            long exerciseId = 90_001;
            String jobId = jobs.startJob(user(), exercise(exerciseId), "generate", GenerationMode.GENERATE);
            assertThat(jobs.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
            assertThat(jobs.isOwnedActiveJob(exerciseId, jobId)).isTrue();
            var writes = member.<String, String>getMap(Constants.HYPERION_LIVE_SAVE_COORDINATION_MAP);
            writes.lock("exercise");
            try {
                writes.put("exercise", "save");
                assertThat(writes.get("exercise")).isEqualTo("save");
            }
            finally {
                writes.unlock("exercise");
            }
        }
        finally {
            member.getLifecycleService().terminate();
        }
    }

    @Test
    void ownerChecksCurrentMembersBeforeTheCachedProtectionUpdates() throws Exception {
        String cluster = "generation-policy-lag-" + UUID.randomUUID();
        HazelcastInstance owner = Hazelcast.newHazelcastInstance(configuration(cluster, 2, List.of()));
        HazelcastInstance peer = null;
        var assessmentBlocked = new CountDownLatch(1);
        var releaseAssessment = new CountDownLatch(1);
        try {
            String seed = "127.0.0.1:" + owner.getCluster().getLocalMember().getSocketAddress().getPort();
            peer = Hazelcast.newHazelcastInstance(configuration(cluster, 2, List.of(seed)));
            String mapName = Constants.HYPERION_LIVE_SAVE_COORDINATION_MAP;
            String policyName = owner.getConfig().getMapConfig(mapName).getSplitBrainProtectionName();
            var cachedPolicy = owner.getSplitBrainProtectionService().getSplitBrainProtection(policyName);
            await().atMost(Duration.ofSeconds(30)).until(cachedPolicy::hasMinimumSize);
            await().atMost(Duration.ofSeconds(30)).until(() -> owner.getPartitionService().isClusterSafe());
            var guarded = new HazelcastDistributedDataProviderService(owner).<String, String>getMap(mapName);
            // These traverse the real processor serialization and backup paths while membership is complete.
            assertThat(guarded.putIfAbsent("existing", "one")).isNull();
            assertThat(guarded.replace("existing", "one", "two")).isTrue();
            assertThat(guarded.get("existing")).isEqualTo("two");
            assertThat(guarded.remove("existing", "two")).isTrue();
            ((HazelcastInstanceProxy) owner).getOriginal().node.nodeEngine.getExecutionService().execute("hz:splitBrainProtection", () -> {
                assessmentBlocked.countDown();
                try {
                    releaseAssessment.await(90, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(assessmentBlocked.await(10, TimeUnit.SECONDS)).isTrue();
            peer.getLifecycleService().terminate();
            await().atMost(Duration.ofSeconds(30)).until(() -> owner.getCluster().getMembers().size() == 1);
            assertThat(cachedPolicy.hasMinimumSize()).isTrue();
            // Native protection still accepts a fresh key. The owner-side processor must reject it now.
            assertThat(owner.<String, String>getMap(mapName).putIfAbsent("native-control", "accepted")).isNull();
            assertThatThrownBy(() -> guarded.putIfAbsent("fresh-owner", "forbidden")).isInstanceOf(SplitBrainProtectionException.class);
            assertThatThrownBy(() -> guarded.get("fresh-owner")).isInstanceOf(SplitBrainProtectionException.class);
            assertThat(owner.<String, String>getMap(mapName).containsKey("fresh-owner")).isFalse();
        }
        finally {
            releaseAssessment.countDown();
            if (peer != null && peer.getLifecycleService().isRunning()) {
                peer.getLifecycleService().terminate();
            }
            owner.getLifecycleService().terminate();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 2, 3 })
    void partitionDuringSaveBlocksBothOwnersAndOrdinaryWrites(int expectedMembers) throws Exception {
        String cluster = "generation-partition-" + UUID.randomUUID();
        var members = new ArrayList<HazelcastInstance>();
        var saveEntered = new CountDownLatch(1);
        var resumeSave = new CountDownLatch(1);
        var durableWrite = new AtomicBoolean();
        var executor = Executors.newSingleThreadExecutor();
        try {
            members.add(Hazelcast.newHazelcastInstance(configuration(cluster, expectedMembers, List.of())));
            String seed = "127.0.0.1:" + members.getFirst().getCluster().getLocalMember().getSocketAddress().getPort();
            for (int i = 1; i < expectedMembers; i++) {
                members.add(Hazelcast.newHazelcastInstance(configuration(cluster, expectedMembers, List.of(seed))));
            }
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> members.forEach(member -> assertThat(member.getCluster().getMembers()).hasSize(expectedMembers)));
            HazelcastInstance owner = members.getFirst();
            HazelcastInstance island = members.getLast();
            var ownerJobs = jobs(owner);
            var islandJobs = jobs(island);
            await().atMost(Duration.ofSeconds(60)).until(() -> owner.getPartitionService().isClusterSafe());
            long exerciseId = expectedMembers == 3 ? exerciseWithoutReplicaOn(owner, island) : 90_002;
            String jobId = ownerJobs.startJob(user(), exercise(exerciseId), "generate", GenerationMode.GENERATE);
            var saved = executor.submit(() -> {
                assertThat(ownerJobs.enterNonCancellablePhase(exerciseId, jobId)).isTrue();
                saveEntered.countDown();
                assertThat(resumeSave.await(180, TimeUnit.SECONDS)).isTrue();
                // The production persistence boundary performs this ownership check before durable writes.
                if (ownerJobs.isOwnedActiveJob(exerciseId, jobId)) {
                    durableWrite.set(true);
                }
                return null;
            });
            assertThat(saveEntered.await(30, TimeUnit.SECONDS)).isTrue();
            // Stop only the isolated member's networking, not its JVM/node engine or either application's save thread.
            ((HazelcastInstanceProxy) island).getOriginal().node.getServer().shutdown();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                assertThat(owner.getCluster().getMembers()).hasSize(expectedMembers - 1);
                assertThat(island.getCluster().getMembers()).hasSize(1);
            });
            for (HazelcastInstance member : List.of(owner, island)) {
                var jobMap = member.<String, Object>getMap(Constants.HYPERION_GENERATION_JOBS_MAP);
                var writes = member.<String, String>getMap(Constants.HYPERION_LIVE_SAVE_COORDINATION_MAP);
                await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                    assertThatThrownBy(() -> jobMap.get(Long.toString(exerciseId))).isInstanceOf(SplitBrainProtectionException.class);
                    assertThatThrownBy(() -> writes.lock("exercise")).isInstanceOf(SplitBrainProtectionException.class);
                    assertThatThrownBy(() -> writes.putIfAbsent("exercise", "ordinary edit")).isInstanceOf(SplitBrainProtectionException.class);
                });
            }
            assertThatThrownBy(() -> islandJobs.startJob(user(), exercise(exerciseId), "replacement", GenerationMode.GENERATE)).isInstanceOf(SplitBrainProtectionException.class);
            assertThatThrownBy(() -> islandJobs.claimRevertSlot(user(), exerciseId)).isInstanceOf(SplitBrainProtectionException.class);
            resumeSave.countDown();
            assertThatThrownBy(() -> saved.get(30, TimeUnit.SECONDS)).hasCauseInstanceOf(SplitBrainProtectionException.class);
            assertThat(durableWrite).isFalse();
        }
        finally {
            resumeSave.countDown();
            executor.shutdownNow();
            for (int i = members.size() - 1; i >= 0; i--) {
                members.get(i).getLifecycleService().terminate();
            }
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static long exerciseWithoutReplicaOn(HazelcastInstance owner, HazelcastInstance island) {
        UUID excluded = island.getCluster().getLocalMember().getUuid();
        var partitions = ((HazelcastInstanceProxy) owner).getOriginal().node.nodeEngine.getPartitionService();
        for (long id = 91_000; id < 101_000; id++) {
            int partitionId = owner.getPartitionService().getPartition(Long.toString(id)).getPartitionId();
            var partition = partitions.getPartition(partitionId);
            var primary = partition.getReplica(0);
            var backup = partition.getReplica(1);
            if (primary != null && backup != null && !excluded.equals(primary.uuid()) && !excluded.equals(backup.uuid())) {
                return id;
            }
        }
        throw new AssertionError("No exercise partition without a primary or backup on the isolated member");
    }

    private static GenerationJobService jobs(HazelcastInstance member) {
        var service = new GenerationJobService(HyperionDistributedDataTestProvider.provider(member), event -> {
        }, mock(GenerationTokenUsageService.class), null, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run);
        service.init();
        return service;
    }

    private static User user() {
        var user = new User();
        user.setLogin("owner");
        return user;
    }

    private static ProgrammingExercise exercise(long id) {
        var exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }

    private static Config configuration(String cluster, int expectedMembers, List<String> seeds) {
        var environment = new MockEnvironment().withProperty("jhipster.cache.hazelcast.expected-data-member-count", Integer.toString(expectedMembers));
        var bean = new HazelcastConfiguration(mock(ApplicationContext.class), new ServerProperties(), Optional.empty(), mock(EurekaInstanceHelper.class), environment,
                Optional.empty());
        Config config = new Config().setClusterName(cluster);
        config.addMapConfig(new MapConfig("default").setBackupCount(1));
        ReflectionTestUtils.invokeMethod(bean, "configureSplitBrainProtection", config);
        config.setProperty("hazelcast.heartbeat.interval.seconds", "1");
        config.setProperty("hazelcast.max.no.heartbeat.seconds", "5");
        config.setProperty("hazelcast.max.no.master.confirmation.seconds", "5");
        config.setProperty("hazelcast.merge.first.run.delay.seconds", "300");
        config.setProperty("hazelcast.merge.next.run.delay.seconds", "300");
        config.getNetworkConfig().setPortAutoIncrement(true).getInterfaces().setEnabled(true).setInterfaces(List.of("127.0.0.1"));
        var join = config.getNetworkConfig().getJoin();
        join.getAutoDetectionConfig().setEnabled(false);
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).setMembers(seeds);
        return config;
    }
}

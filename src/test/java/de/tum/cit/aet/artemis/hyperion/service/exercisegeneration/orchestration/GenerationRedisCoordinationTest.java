package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisNodeIdentity;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

@EnabledIf("isDockerAvailable")
class GenerationRedisCoordinationTest {

    static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Test
    void independentIncarnationsFenceWritersAndRetainDepartedOwnersUntilExactRecovery() throws Exception {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var firstIdentity = new RedisNodeIdentity("artemis-core", true, true);
            var secondIdentity = new RedisNodeIdentity("artemis-core", true, true);
            RedissonClient firstClient = null;
            RedissonClient secondClient = null;
            try {
                String address = "redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379);
                firstClient = client(address, firstIdentity);
                secondClient = client(address, secondIdentity);
                var resolver = new RedisClientListResolver(new RedissonConnectionFactory(secondClient));
                var firstProvider = new RedissonDistributedDataProviderService(firstClient, resolver, firstIdentity);
                var secondProvider = new RedissonDistributedDataProviderService(secondClient, resolver, secondIdentity);
                var first = new GenerationExternalMutationService(firstProvider, 1, true);
                var second = new GenerationExternalMutationService(secondProvider, 1, true);

                assertThat(firstProvider.getLocalNodeId()).isNotEqualTo(secondProvider.getLocalNodeId());
                assertThat(firstProvider.getCoordinationSnapshot().orElseThrow().ownerNodeIds()).containsExactlyInAnyOrder(firstIdentity.connectionName(),
                        secondIdentity.connectionName());
                assertThat(resolver.getUniqueClients()).containsExactly("artemis-core");
                assertThat(resolver.getClientAddressesByName().orElseThrow().get("artemis-core")).isNotEmpty();

                var start = new CountDownLatch(1);
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var firstClaim = executor.submit(() -> claimAfter(start, first));
                    var secondClaim = executor.submit(() -> claimAfter(start, second));
                    start.countDown();
                    String firstToken = firstClaim.get(10, TimeUnit.SECONDS);
                    String secondToken = secondClaim.get(10, TimeUnit.SECONDS);
                    assertThat(firstToken == null ^ secondToken == null).isTrue();
                    if (firstToken != null) {
                        first.clearExternalMutationSlot(42, firstToken);
                    }
                    else {
                        second.clearExternalMutationSlot(42, secondToken);
                    }
                }

                String departedToken = first.claimExternalMutationSlot(42);
                assertThat(second.recoverWedgedSlot(42, departedToken)).isFalse();
                firstClient.shutdown();
                await().atMost(Duration.ofSeconds(10))
                        .untilAsserted(() -> assertThat(secondProvider.getCoordinationSnapshot().orElseThrow().ownerNodeIds()).containsExactly(secondIdentity.connectionName()));
                assertThat(second.getWedgedSlotInfo(42).orElseThrow().ownerLeftCluster()).isTrue();
                assertThatThrownBy(() -> second.claimExternalMutationSlot(42)).isInstanceOf(ConflictException.class);
                assertThat(second.recoverWedgedSlot(42, "wrong-token")).isFalse();
                assertThat(second.recoverWedgedSlot(42, departedToken)).isTrue();
                String replacementToken = second.claimExternalMutationSlot(42);
                second.clearExternalMutationSlot(42, departedToken);
                assertThat(second.isGenerationActive(42)).isTrue();
                assertThatThrownBy(() -> second.claimExternalMutationSlot(42)).isInstanceOf(ConflictException.class);
                second.clearExternalMutationSlot(42, replacementToken);
                assertThat(second.isGenerationActive(42)).isFalse();
            }
            finally {
                if (firstClient != null) {
                    firstClient.shutdown();
                }
                if (secondClient != null) {
                    secondClient.shutdown();
                }
            }
        }
    }

    @Test
    void unfencedWriterRejectsAdmissionButWorkerOnlyProcessDoesNot() {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            String address = "redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379);
            var identity = new RedisNodeIdentity("artemis-core", true, true);
            var writerClient = client(address, identity);
            var workerClient = client(address, new RedisNodeIdentity("aiworker", false, false));
            RedissonClient unfencedClient = null;
            try {
                var resolver = new RedisClientListResolver(new RedissonConnectionFactory(writerClient));
                var provider = new RedissonDistributedDataProviderService(writerClient, resolver, identity);
                var writer = new GenerationExternalMutationService(provider, 1, true);
                String token = writer.claimExternalMutationSlot(42);
                writer.clearExternalMutationSlot(42, token);
                unfencedClient = client(address, new RedisNodeIdentity("artemis-core", true, false));
                assertThat(provider.getCoordinationSnapshot().orElseThrow().allWritersFenced()).isFalse();
                assertThatThrownBy(() -> writer.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
                assertThatThrownBy(() -> new GenerationClusterTopology(provider, 1).verifyAllMembers()).isInstanceOf(ServiceUnavailableAlertException.class);
            }
            finally {
                if (unfencedClient != null) {
                    unfencedClient.shutdown();
                }
                workerClient.shutdown();
                writerClient.shutdown();
            }
        }
    }

    @Test
    void failedUnlockAfterSuccessfulRedisAcquisitionCannotWedgeTheExercise() {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var identity = new RedisNodeIdentity("artemis-core", true, true);
            var redis = client("redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379), identity);
            try {
                var resolver = new RedisClientListResolver(new RedissonConnectionFactory(redis));
                var provider = spy(new RedissonDistributedDataProviderService(redis, resolver, identity));
                var slots = spy(provider.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
                doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
                var writer = new GenerationExternalMutationService(provider, 1, true);
                for (boolean participation : new boolean[] { false, true }) {
                    var failure = new IllegalStateException("unlock response lost");
                    doAnswer(invocation -> {
                        invocation.callRealMethod();
                        throw failure;
                    }).doCallRealMethod().when(slots).unlock("42");
                    assertThatThrownBy(() -> {
                        if (participation) {
                            writer.claimParticipationSlot(42);
                        }
                        else {
                            writer.claimExternalMutationSlot(42);
                        }
                    }).isSameAs(failure);
                    assertThat(slots.get("42")).isNull();
                    String token = writer.claimExternalMutationSlot(42);
                    writer.clearExternalMutationSlot(42, token);
                }
            }
            finally {
                redis.shutdown();
            }
        }
    }

    @Test
    void failedRedisRevertAcquisitionRestoresRecoveryInsteadOfLeavingAnActiveRetry() {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var identity = new RedisNodeIdentity("artemis-core", true, true);
            var redis = client("redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379), identity);
            try {
                var resolver = new RedisClientListResolver(new RedissonConnectionFactory(redis));
                var provider = spy(new RedissonDistributedDataProviderService(redis, resolver, identity));
                var slots = spy(provider.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
                doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
                var guard = new GenerationExternalMutationService(provider, 1, true);
                var jobs = new GenerationJobService(provider, _ -> {
                }, org.mockito.Mockito.mock(GenerationTokenUsageService.class), null, new HyperionAgentProperties(), org.mockito.Mockito.mock(HyperionEffortProfileService.class),
                        Runnable::run, 1, Duration.ofHours(4), 0, null, guard);
                jobs.init();
                var user = new User();
                user.setLogin("editor");
                for (boolean recovering : new boolean[] { false, true }) {
                    if (recovering) {
                        jobs.retainRevertRecoverySlot(42, jobs.claimRevertSlot(user, 42));
                    }
                    var previous = slots.get("42");
                    var failure = new IllegalStateException("unlock response lost");
                    var unlocks = new AtomicInteger();
                    doAnswer(invocation -> {
                        invocation.callRealMethod();
                        if (unlocks.incrementAndGet() == (recovering ? 1 : 2)) {
                            throw failure;
                        }
                        return null;
                    }).when(slots).unlock("42");
                    assertThatThrownBy(() -> jobs.claimRevertSlot(user, 42)).isSameAs(failure);
                    assertThat(slots.get("42")).isEqualTo(previous);
                    String next = jobs.claimRevertSlot(user, 42);
                    jobs.clearRevertSlot(42, next);
                    assertThat(slots.get("42")).isNull();
                    String partialUndo = jobs.claimRevertSlot(user, 42);
                    doThrow(new IllegalStateException("conversion unavailable")).doCallRealMethod().when(slots).lock("42");
                    jobs.retainRevertRecoverySlot(42, partialUndo);
                    assertThat(slots.get("42").jobId()).isEqualTo(partialUndo);
                    guard.retryPendingReleases();
                    assertThat(jobs.isRevertRecoveryPending(42)).isTrue();
                    assertThat(jobs.recoverWedgedSlot(42, slots.get("42").jobId())).isTrue();
                }
            }
            finally {
                redis.shutdown();
            }
        }
    }

    @Test
    void generationAdmissionAndWorkerExitRecoverAfterRedisCleanupFailures() {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var identity = new RedisNodeIdentity("artemis-core", true, true);
            var redis = client("redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379), identity);
            try {
                var resolver = new RedisClientListResolver(new RedissonConnectionFactory(redis));
                var provider = spy(new RedissonDistributedDataProviderService(redis, resolver, identity));
                var slots = spy(provider.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
                doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
                var events = new java.util.ArrayList<Object>();
                var jobs = new GenerationJobService(provider, events::add, org.mockito.Mockito.mock(GenerationTokenUsageService.class));
                jobs.init();
                var user = new User();
                user.setLogin("editor");
                var exercise = new de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise();
                exercise.setId(42L);
                var failure = new IllegalStateException("unlock response lost");
                doAnswer(invocation -> {
                    invocation.callRealMethod();
                    doThrow(new IllegalStateException("cleanup unavailable")).doCallRealMethod().when(slots).lock("42");
                    throw failure;
                }).doCallRealMethod().when(slots).unlock("42");
                assertThatThrownBy(() -> jobs.startJob(user, exercise, "brief", GenerationMode.GENERATE)).isSameAs(failure);
                assertThat(events).noneMatch(GenerationStartedEvent.class::isInstance);
                assertThat(slots.get("42")).isNotNull();
                jobs.retryPendingCleanup();
                assertThat(slots.get("42")).isNull();
                String job = jobs.startJob(user, exercise, "new brief", GenerationMode.GENERATE);
                doThrow(new IllegalStateException("cleanup unavailable")).doCallRealMethod().when(slots).lock("42");
                jobs.clearJob(42, job);
                assertThat(slots.get("42").jobId()).isEqualTo(job);
                jobs.retryPendingCleanup();
                assertThat(slots.get("42")).isNull();
                assertThat(jobs.getStatus(user, exercise)).hasValueSatisfying(status -> assertThat(status.running()).isFalse());
                String partialJob = jobs.startJob(user, exercise, "partial save", GenerationMode.GENERATE);
                jobs.enterNonCancellablePhase(42, partialJob);
                doThrow(new IllegalStateException("conversion unavailable")).doCallRealMethod().when(slots).lock("42");
                jobs.retainIncompleteGenerationSlot(42, partialJob);
                assertThat(slots.get("42").jobId()).isEqualTo(partialJob);
                jobs.retryPendingCleanup();
                var recovery = slots.get("42");
                assertThat(GenerationRecoverySlots.isGenerationRecoveryOf(recovery, partialJob)).isTrue();
                assertThat(jobs.recoverWedgedSlot(42, recovery.jobId())).isTrue();
            }
            finally {
                redis.shutdown();
            }
        }
    }

    @Test
    void cancellationRejectsLateFileUpdatesThroughTheExpiringRedisMap() {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var identity = new RedisNodeIdentity("artemis-core", true, true);
            var redis = client("redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379), identity);
            try {
                var resolver = new RedisClientListResolver(new RedissonConnectionFactory(redis));
                var provider = new RedissonDistributedDataProviderService(redis, resolver, identity);
                var cancellations = provider.<String, Boolean>getExpiringMap(GenerationJobService.CANCEL_MAP_NAME, Duration.ofHours(1));
                var replay = new GenerationJobReplayStore(provider, Duration.ofHours(4), Duration.ofHours(4), cancellations);
                String jobId = "cancelled-run";
                Instant now = Instant.now();
                provider.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME).put("42",
                        new GenerationJobService.JobInfo(jobId, "owner", 42, now, null, identity.connectionName(), now, true, null));
                replay.initializeStart(42, jobId, "owner", GenerationMode.GENERATE, null);
                var first = ExerciseGenerationFileChangeDTO.of("solution/Stack.java", "write", 1);
                assertThat(replay.recordFileChange(42, jobId, first)).isTrue();
                cancellations.put(jobId, true);
                var late = ExerciseGenerationFileChangeDTO.of("template/Stack.java", "write", 2);
                assertThat(replay.recordFileChange(42, jobId, late)).isFalse();
                var index = provider.<String, GenerationJobService.JobFileChangeIndex>getExpiringMap(GenerationJobReplayStore.FILE_CHANGE_MAP_NAME, Duration.ofHours(4)).get("42");
                assertThat(index.changes()).containsExactly(first);
            }
            finally {
                redis.shutdown();
            }
        }
    }

    private static String claimAfter(CountDownLatch start, GenerationExternalMutationService service) throws InterruptedException {
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Concurrent claims were not released");
        }
        try {
            return service.claimExternalMutationSlot(42);
        }
        catch (ConflictException expected) {
            return null;
        }
    }

    private static RedissonClient client(String address, RedisNodeIdentity identity) {
        var config = new Config();
        config.useSingleServer().setAddress(address).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer(identity).customize(config);
        return Redisson.create(config);
    }
}

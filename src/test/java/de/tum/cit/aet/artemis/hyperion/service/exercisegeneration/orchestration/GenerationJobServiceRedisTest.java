package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;

@EnabledIf("isDockerAvailable")
class GenerationJobServiceRedisTest {

    static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void cancellationRecordStillBlocksSaveAfterFastSignalOrAcknowledgementIsLost(boolean acknowledgementLost) {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            var redis = client("redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379), "generation-cancellation-test");
            try {
                var provider = spy(provider(redis, "generation-cancellation-test"));
                var slots = spy(provider.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
                doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
                var jobs = jobs(provider);
                String job = jobs.startJob(owner(), exercise(), "brief", GenerationMode.GENERATE);
                if (acknowledgementLost) {
                    doAnswer(call -> {
                        call.callRealMethod();
                        throw new IllegalStateException("CAS acknowledgement lost");
                    }).doCallRealMethod().when(slots).replace(eq("42"), any(), any());
                    assertThatThrownBy(() -> jobs.requestCancellation(42, job, owner())).isInstanceOf(ServiceUnavailableAlertException.class);
                }
                else {
                    assertThat(jobs.requestCancellation(42, job, owner())).isTrue();
                }
                var fastSignal = provider.<String, Boolean>getExpiringMap(GenerationJobService.CANCEL_MAP_NAME, Duration.ofHours(1));
                fastSignal.remove(job);

                assertThat(jobs.isCancelled(job)).isFalse();
                assertThat(slots.get("42").cancellationRequested()).isTrue();
                assertThat(jobs.heartbeat(42, job)).isTrue();
                assertThat(slots.get("42").cancellationRequested()).isTrue();
                assertThat(jobs.enterNonCancellablePhase(42, job)).isFalse();
                assertThat(jobs.requestCancellation(42, job, owner())).isTrue();
            }
            finally {
                redis.shutdown();
            }
        }
    }

    @Test
    void concurrentNodesAdmitOnlyOneJobAndPermitANewRunAfterCompletion() throws Exception {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            String address = "redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379);
            var firstRedis = client(address, "generation-first-node");
            var secondRedis = client(address, "generation-second-node");
            try {
                var first = jobs(provider(firstRedis, "generation-first-node"));
                var second = jobs(provider(secondRedis, "generation-second-node"));
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var firstAttempt = executor.submit(() -> admitAfter(ready, start, first));
                    var secondAttempt = executor.submit(() -> admitAfter(ready, start, second));
                    try {
                        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    }
                    finally {
                        start.countDown();
                    }
                    String firstJob = firstAttempt.get(10, TimeUnit.SECONDS);
                    String secondJob = secondAttempt.get(10, TimeUnit.SECONDS);
                    assertThat(firstJob == null).isNotEqualTo(secondJob == null);
                    var winner = firstJob == null ? second : first;
                    var other = firstJob == null ? first : second;
                    String job = firstJob == null ? secondJob : firstJob;
                    assertThatThrownBy(() -> other.startJob(owner(), exercise(), "duplicate", GenerationMode.GENERATE)).isInstanceOf(ConflictException.class);

                    winner.clearJob(42, job);
                    String replacement = other.startJob(owner(), exercise(), "next", GenerationMode.GENERATE);
                    assertThat(replacement).isNotBlank().isNotEqualTo(job);
                    winner.clearJob(42, job);
                    assertThat(other.isOwnedActiveJob(42, replacement)).isTrue();
                }
            }
            finally {
                secondRedis.shutdown();
                firstRedis.shutdown();
            }
        }
    }

    @Test
    void liveSaveReadersAndRecoveryTokensRoundTripThroughTheRealRedisCodec() throws Exception {
        try (var valkey = ValkeyTestContainerFactory.create()) {
            valkey.start();
            String address = "redis://" + valkey.getHost() + ":" + valkey.getMappedPort(6379);
            var firstRedis = client(address, "artemis-live-save-first");
            var secondRedis = client(address, "artemis-live-save-second");
            try (var reader = Executors.newSingleThreadExecutor()) {
                var first = new GenerationLiveSaveService(provider(firstRedis, "artemis-live-save-first"), true);
                var second = new GenerationLiveSaveService(provider(secondRedis, "artemis-live-save-second"), true);
                String firstReader = first.claimReader(42L);
                String oldGroupToken = second.getSlotInfo(42L).orElseThrow().token();

                String secondReader = reader.submit(() -> second.claimReader(42L)).get(10, TimeUnit.SECONDS);

                var shared = first.getSlotInfo(42L).orElseThrow();
                assertThat(shared.readers()).containsOnlyKeys(firstReader, secondReader);
                assertThat(shared.readers().values()).doesNotHaveDuplicates();
                assertThat(shared.token()).isNotEqualTo(oldGroupToken);
                assertThatThrownBy(() -> first.claim(42L)).isInstanceOf(ConflictException.class);
                assertThat(second.recoverSlot(42L, oldGroupToken)).isFalse();
                assertThat(second.getSlotInfo(42L).orElseThrow().readers()).containsOnlyKeys(firstReader, secondReader);

                first.releaseReader(42L, firstReader);
                assertThat(second.getSlotInfo(42L).orElseThrow().readers()).containsOnlyKeys(secondReader);
                assertThatThrownBy(() -> first.claim(42L)).isInstanceOf(ConflictException.class);
                second.releaseReader(42L, secondReader);
                assertThat(first.getSlotInfo(42L)).isEmpty();

                String writer = first.claim(42L);
                assertThat(second.getSlotInfo(42L).orElseThrow().token()).isEqualTo(writer);
                assertThatThrownBy(() -> second.claimReader(42L)).isInstanceOf(ConflictException.class);
                first.release(42L, writer);
                assertThat(second.getSlotInfo(42L)).isEmpty();
            }
            finally {
                secondRedis.shutdown();
                firstRedis.shutdown();
            }
        }
    }

    private static String admitAfter(CountDownLatch ready, CountDownLatch start, GenerationJobService jobs) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Concurrent starts were not released");
        }
        try {
            return jobs.startJob(owner(), exercise(), "brief", GenerationMode.GENERATE);
        }
        catch (ConflictException expected) {
            return null;
        }
    }

    private static GenerationJobService jobs(RedissonDistributedDataProviderService provider) {
        var jobs = new GenerationJobService(provider, event -> {
        }, mock(GenerationTokenUsageService.class));
        jobs.init();
        return jobs;
    }

    private static RedissonDistributedDataProviderService provider(RedissonClient redis, String name) {
        var provider = new RedissonDistributedDataProviderService(redis, new RedisClientListResolver(new RedissonConnectionFactory(redis)));
        ReflectionTestUtils.setField(provider, "redisClientName", name);
        return provider;
    }

    private static RedissonClient client(String address, String name) {
        var config = new Config();
        config.useSingleServer().setAddress(address).setClientName(name).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer().customize(config);
        return Redisson.create(config);
    }

    private static User owner() {
        var user = new User();
        user.setLogin("editor");
        return user;
    }

    private static ProgrammingExercise exercise() {
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        return exercise;
    }
}

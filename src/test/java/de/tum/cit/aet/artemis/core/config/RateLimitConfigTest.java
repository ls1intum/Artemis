package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.testcontainers.DockerClientFactory;

import com.redis.testcontainers.RedisContainer;

import de.tum.cit.aet.artemis.shared.ValkeyTestContainerFactory;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;

/**
 * Guards that every distributed data provider accepted by {@link DistributedDataProviderResolver} also has bucket storage. A provider without a
 * {@link ProxyManager} bean would let a core node start up only until the rate limit service is constructed.
 */
class RateLimitConfigTest {

    /**
     * Only the Local provider can be constructed without external infrastructure. Hazelcast and Redis need their client beans, which is why they are asserted
     * through {@link #everySupportedProviderIsCoveredByBucketStorage()} instead of by starting a context.
     *
     * @param provider the configured provider name, in the spellings a deployment may use
     */
    @ParameterizedTest
    @ValueSource(strings = { "Local", "local", "LOCAL" })
    void contributesBucketStorageForTheLocalProvider(String provider) {
        new ApplicationContextRunner().withUserConfiguration(RateLimitConfig.class)
                .withPropertyValues("spring.profiles.active=" + PROFILE_CORE, DistributedDataProviderResolver.PROVIDER_PROPERTY + "=" + provider)
                .run(context -> assertThat(context).hasSingleBean(ProxyManager.class));
    }

    @Test
    void theLocalBucketStorageEnforcesTheConfiguredLimit() {
        ProxyManager<String> proxyManager = new LocalRateLimitProxyManager();
        Bucket bucket = proxyManager.getProxy("client", () -> RateLimitConfig.perMinute(2));

        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isFalse();

        // A removed bucket starts over, which is what the eviction of an idle entry amounts to.
        proxyManager.removeProxy("client");
        assertThat(proxyManager.getProxy("client", () -> RateLimitConfig.perMinute(2)).tryConsume(1)).isTrue();
    }

    @Test
    void everySupportedProviderIsCoveredByBucketStorage() {
        List<String> conditions = Arrays.stream(RateLimitConfig.class.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> ProxyManager.class.isAssignableFrom(method.getReturnType())).map(method -> method.getAnnotation(Conditional.class).value()[0].getSimpleName())
                .toList();

        assertThat(conditions).containsExactlyInAnyOrder(HazelcastDistributedDataCondition.class.getSimpleName(), RedisDistributedDataCondition.class.getSimpleName(),
                LocalDataCondition.class.getSimpleName());
    }

    // Use a real Valkey instance: a mocked ProxyManager cannot verify Redis TTL or CAS behavior.
    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (Exception _) {
            return false;
        }
    }

    @Test
    @EnabledIf("isDockerAvailable")
    void redisBucketReceivesRefillAwareExpiration() {
        withRedis((client, _) -> {
            ProxyManager<String> manager = new RateLimitConfig().redisRateLimitProxyManager(client);
            String key = "type=OIDC_AUTHORIZATION#rpm=2#192.0.2.1";
            Bucket bucket = manager.getProxy(key, () -> RateLimitConfig.perMinute(2));

            assertThat(bucket.tryConsume(1)).isTrue();
            assertThat(client.getBucket(key).remainTimeToLive()).isBetween(1L, Duration.ofMinutes(2).toMillis());
            assertThat(bucket.tryConsume(1)).isTrue();
            assertThat(bucket.tryConsume(1)).isFalse();
            assertThat(client.getBucket(key).remainTimeToLive()).isGreaterThan(Duration.ofMinutes(1).toMillis());

            // A second Artemis node sees the same depleted state through Redis.
            Bucket otherNodeBucket = new RateLimitConfig().redisRateLimitProxyManager(client).getProxy(key, () -> RateLimitConfig.perMinute(2));
            assertThat(otherNodeBucket.tryConsume(1)).isFalse();
        });
    }

    @Test
    @EnabledIf("isDockerAvailable")
    void existingRedisBucketWithoutTtlGetsExpirationWithoutResettingItsTokens() {
        withRedis((client, _) -> {
            String key = "type=AUTHENTICATION#rpm=2#192.0.2.2";
            var legacyManager = Bucket4jRedisson.casBasedBuilder(((Redisson) client).getCommandExecutor()).build();
            Bucket legacyBucket = legacyManager.getProxy(key, () -> RateLimitConfig.perMinute(2));
            assertThat(legacyBucket.tryConsume(1)).isTrue();
            assertThat(client.getBucket(key).remainTimeToLive()).isEqualTo(-1);

            Bucket migratedBucket = new RateLimitConfig().redisRateLimitProxyManager(client).getProxy(key, () -> RateLimitConfig.perMinute(2));
            assertThat(migratedBucket.tryConsume(1)).isTrue();
            assertThat(migratedBucket.tryConsume(1)).isFalse();
            assertThat(client.getBucket(key).remainTimeToLive()).isPositive();
        });
    }

    @Test
    @EnabledIf("isDockerAvailable")
    void legacyExpirationMigrationPreservesLimitsAndUnrelatedKeys() {
        withRedis((client, container) -> {
            String key = "type=OIDC_AUTHORIZATION#rpm=2#192.0.2.3";
            var legacyManager = Bucket4jRedisson.casBasedBuilder(((Redisson) client).getCommandExecutor()).build();
            Bucket bucket = legacyManager.getProxy(key, () -> RateLimitConfig.perMinute(2));
            assertThat(bucket.tryConsume(1)).isTrue();
            assertThat(bucket.tryConsume(1)).isTrue();
            assertThat(bucket.tryConsume(1)).isFalse();
            assertThat(client.getBucket(key).remainTimeToLive()).isEqualTo(-1);
            client.getBucket("unrelated-key").set("unrelated-value");

            // Matches the documented migration: EXPIRE only if the bucket has no TTL already.
            try {
                var result = container.execInContainer("valkey-cli", "EXPIRE", key, "120", "NX");
                assertThat(result.getExitCode()).isZero();
                assertThat(result.getStdout().trim()).isEqualTo("1");
            }
            catch (Exception e) {
                throw new AssertionError("Could not run Valkey migration command", e);
            }

            assertThat(client.getBucket(key).remainTimeToLive()).isPositive();
            assertThat(client.getBucket("unrelated-key").remainTimeToLive()).isEqualTo(-1);
            assertThat(client.getBucket("unrelated-key").get()).isEqualTo("unrelated-value");
            assertThat(new RateLimitConfig().redisRateLimitProxyManager(client).getProxy(key, () -> RateLimitConfig.perMinute(2)).tryConsume(1)).isFalse();
        });
    }

    private static void withRedis(java.util.function.BiConsumer<RedissonClient, RedisContainer> assertion) {
        try (RedisContainer container = ValkeyTestContainerFactory.create()) {
            container.start();
            Config config = new Config();
            config.useSingleServer().setAddress("redis://" + container.getHost() + ":" + container.getMappedPort(6379));
            RedissonClient client = Redisson.create(config);
            try {
                assertion.accept(client, container);
            }
            finally {
                client.shutdown();
            }
        }
    }
}

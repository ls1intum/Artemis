package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;

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
}

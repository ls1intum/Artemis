package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.openai.core.http.Headers;
import com.openai.errors.UnauthorizedException;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.hazelcast.HazelcastDistributedDataProviderService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HyperionProviderFailureCooldownServiceTest {

    private static final String MAP_NAME = "hyperion-provider-failure-cooldowns";

    private HazelcastInstance hazelcastInstance;

    private HyperionProviderFailureCooldownService firstService;

    private HyperionProviderFailureCooldownService secondService;

    @BeforeAll
    void startHazelcast() {
        Config config = new Config();
        config.setClusterName("hyperion-provider-cooldown-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @BeforeEach
    void setUp() {
        hazelcastInstance.getDistributedObjects().forEach(distributedObject -> distributedObject.destroy());
        DistributedDataProvider distributedDataProvider = new HazelcastDistributedDataProviderService(hazelcastInstance);
        firstService = new HyperionProviderFailureCooldownService(distributedDataProvider);
        firstService.init();
        secondService = new HyperionProviderFailureCooldownService(distributedDataProvider);
        secondService.init();
    }

    @AfterAll
    void stopHazelcast() {
        hazelcastInstance.shutdown();
    }

    @Test
    void cooldownIsSharedAcrossServiceInstances() {
        Instant deadline = Instant.now().plusSeconds(30);

        firstService.startCooldown("model", deadline);

        assertThat(secondService.cooldownUntil("model")).isEqualTo(deadline);
    }

    @Test
    void startCooldownKeepsTheLatestDeadline() {
        Instant firstDeadline = Instant.now().plusSeconds(30);
        Instant laterDeadline = firstDeadline.plusSeconds(30);

        firstService.startCooldown("model", firstDeadline);
        secondService.startCooldown("model", laterDeadline);
        firstService.startCooldown("model", firstDeadline);

        assertThat(firstService.cooldownUntil("model")).isEqualTo(laterDeadline);
    }

    @Test
    void cooldownUntilRemovesExpiredState() {
        firstService.startCooldown("model", Instant.now().minusSeconds(1));

        assertThat(firstService.cooldownUntil("model")).isNull();
        assertThat(hazelcastInstance.getMap(MAP_NAME).size()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void startCooldownPreservesSubsecondTtlPrecision() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        DistributedMap<String, Object> mockedMap = mock(DistributedMap.class);
        when(provider.<String, Object>getExpiringMap(eq(MAP_NAME), any(Duration.class))).thenReturn(mockedMap);
        HyperionProviderFailureCooldownService service = new HyperionProviderFailureCooldownService(provider);
        service.init();

        service.startCooldown("model", Instant.now().plusMillis(1500));

        verify(mockedMap).put(eq("model"), any(), any(Duration.class));
    }

    @Test
    void executeOpensSharedCooldownAfterHardFailureAndRejectsTheNextCall() {
        assertThatThrownBy(() -> firstService.execute("model", Duration.ofMinutes(5), () -> {
            throw new RuntimeException("HTTP 429 insufficient_quota: exceeded your current quota");
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("insufficient_quota");
        AtomicBoolean called = new AtomicBoolean();

        assertThatThrownBy(() -> secondService.execute("model", Duration.ofMinutes(5), () -> {
            called.set(true);
            return "response";
        })).isInstanceOf(ProviderFailureCooldown.ProviderInCooldownException.class);
        assertThat(called).isFalse();
    }

    @Test
    void executeDoesNotOpenCooldownForTransientRateLimit() {
        assertThatThrownBy(() -> firstService.execute("model", Duration.ofMinutes(5), () -> {
            throw new RuntimeException("HTTP 429 rate_limit_exceeded: too many requests");
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("rate_limit_exceeded");

        assertThat(secondService.execute("model", Duration.ofMinutes(5), () -> "response")).isEqualTo("response");
    }

    @Test
    void executeOpensCooldownForAMisconfiguredModel() {
        // A missing/renamed deployment is a configuration failure, not a transient one: every worker would burn the same request, so it must cool down like a quota failure.
        assertThatThrownBy(() -> firstService.execute("model", Duration.ofMinutes(5), () -> {
            throw new RuntimeException("HTTP 404 model_not_found: requested model does not exist");
        })).isInstanceOf(RuntimeException.class).hasMessageContaining("model_not_found");

        assertThatThrownBy(() -> secondService.execute("model", Duration.ofMinutes(5), () -> "response")).isInstanceOf(ProviderFailureCooldown.ProviderInCooldownException.class);
    }

    @Test
    void executeOpensCooldownForATypedUnauthorizedFailureWithoutAnyStatusInTheMessage() {
        // The typed SDK exception carries its 401 in statusCode(), not in the message, so classification must read the OpenAI exception rather than regex the text.
        assertThatThrownBy(() -> firstService.execute("model", Duration.ofMinutes(5), () -> {
            throw UnauthorizedException.builder().headers(Headers.builder().build()).build();
        })).isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> secondService.execute("model", Duration.ofMinutes(5), () -> "response")).isInstanceOf(ProviderFailureCooldown.ProviderInCooldownException.class);
    }

}

package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

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
        firstService = new HyperionProviderFailureCooldownService(hazelcastInstance);
        firstService.init();
        secondService = new HyperionProviderFailureCooldownService(hazelcastInstance);
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
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void startCooldownPreservesSubsecondTtlPrecision() {
        HazelcastInstance mockedHazelcast = mock(HazelcastInstance.class);
        IMap mockedMap = mock(IMap.class);
        when(mockedHazelcast.getMap(anyString())).thenReturn(mockedMap);
        HyperionProviderFailureCooldownService service = new HyperionProviderFailureCooldownService(mockedHazelcast);
        service.init();

        service.startCooldown("model", Instant.now().plusMillis(1500));

        verify(mockedMap).set(eq("model"), any(), anyLong(), eq(TimeUnit.MILLISECONDS));
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

}

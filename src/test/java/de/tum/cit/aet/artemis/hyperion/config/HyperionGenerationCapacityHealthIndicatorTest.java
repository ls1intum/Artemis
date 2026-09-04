package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.service.RemoteInteractiveSandboxClient;
import de.tum.cit.aet.artemis.buildagent.service.RemoteInteractiveSandboxClient.GenerationSandboxCapacity;

class HyperionGenerationCapacityHealthIndicatorTest {

    private static final String SLOTS_PROPERTY = "artemis.continuous-integration.build-agent.max-generation-sandbox-slots";

    @Test
    void reportsDownAndNamesTheOptInPropertyWhenNoAgentAdvertisesCapacity() {
        // With the default of zero slots the feature looks enabled in the startup banner and in /management/info while every request 503s; this is the one place an administrator
        // can see the gap, so it must be DOWN and name the property to set.
        Health health = indicatorFor(new GenerationSandboxCapacity(3, 0, 0, 0)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("hint")).asString().contains(SLOTS_PROPERTY);
    }

    @Test
    void reportsUpWhenTheFleetIsMerelyBusy() {
        // A full fleet resolves on its own when a run finishes; reporting DOWN would turn ordinary load into a false alarm and hide the configuration case above.
        Health health = indicatorFor(new GenerationSandboxCapacity(2, 1, 2, 2)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("hint")).asString().contains("currently occupied");
    }

    @Test
    void reportsUpWithoutAHintWhenSlotsAreFree() {
        Health health = indicatorFor(new GenerationSandboxCapacity(2, 2, 4, 1)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).doesNotContainKey("hint");
    }

    @Test
    void reportsDownWhenCapacityCannotBeDeterminedAtAll() {
        Health health = new HyperionGenerationCapacityHealthIndicator(Optional.empty()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void survivesARelayFailureInsteadOfPropagatingItIntoTheHealthEndpoint() {
        RemoteInteractiveSandboxClient sandboxClient = mock(RemoteInteractiveSandboxClient.class);
        when(sandboxClient.generationSandboxCapacity()).thenThrow(new IllegalStateException("cluster unavailable"));

        Health health = new HyperionGenerationCapacityHealthIndicator(Optional.of(sandboxClient)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void rejectionWarningIsRateLimitedButAlwaysRecordsTheFirstRejection() {
        HyperionGenerationCapacityHealthIndicator indicator = indicatorFor(new GenerationSandboxCapacity(1, 0, 0, 0));

        indicator.warnGenerationRejectedForMissingCapacity();
        long afterFirst = lastWarningMillis(indicator);
        assertThat(afterFirst).isNotEqualTo(Long.MIN_VALUE);

        // A client polling a permanently unconfigured instance must not turn one configuration problem into a log flood.
        indicator.warnGenerationRejectedForMissingCapacity();
        assertThat(lastWarningMillis(indicator)).isEqualTo(afterFirst);
    }

    private static HyperionGenerationCapacityHealthIndicator indicatorFor(GenerationSandboxCapacity capacity) {
        RemoteInteractiveSandboxClient sandboxClient = mock(RemoteInteractiveSandboxClient.class);
        when(sandboxClient.generationSandboxCapacity()).thenReturn(capacity);
        return new HyperionGenerationCapacityHealthIndicator(Optional.of(sandboxClient));
    }

    private static long lastWarningMillis(HyperionGenerationCapacityHealthIndicator indicator) {
        return ((AtomicLong) ReflectionTestUtils.getField(indicator, "lastRejectionWarningAtMillis")).get();
    }
}

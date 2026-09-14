package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class ProviderFailureCooldownTest {

    @Test
    void expiredStoredDeadlineDoesNotBlockExecution() {
        var cooldown = new TestProviderFailureCooldown();
        cooldown.startCooldown("model", Instant.EPOCH);
        assertThat(cooldown.execute("model", Duration.ofMinutes(1), () -> "called")).isEqualTo("called");
    }

    @Test
    void futureDeadlineBlocksBeforeCallingProvider() {
        var cooldown = new TestProviderFailureCooldown();
        cooldown.startCooldown("model", Instant.now().plusSeconds(3600));
        var called = new AtomicBoolean();
        assertThatThrownBy(() -> cooldown.execute("model", Duration.ofMinutes(1), () -> called.getAndSet(true)))
                .isInstanceOf(ProviderFailureCooldown.ProviderInCooldownException.class);
        assertThat(called).isFalse();
    }
}

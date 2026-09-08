package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.jms.ConnectionFactory;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionWorkerProperties;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;

class GenerationWorkerRegistryServiceTest {

    private final LocalDataProviderService data = new LocalDataProviderService();

    private final HyperionWorkerProperties properties = new HyperionWorkerProperties("tcp://broker:61617?sslEnabled=true", "core", "test-password", List.of("worker"),
            Duration.ofSeconds(30), Duration.ofSeconds(45));

    private final GenerationWorkerRegistryService registry = new GenerationWorkerRegistryService(properties, mock(ConnectionFactory.class), new WorkerMessageCodec(), data);

    private final UUID incarnation = UUID.randomUUID();

    @Test
    void requiresReadyWorkerAndExclusiveClaim() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, false));
        assertThat(registry.reachableWorkers()).isEqualTo(1);
        assertThat(registry.capableWorkers()).isZero();
        assertThatThrownBy(() -> registry.claim("job", 1)).isInstanceOf(ServiceUnavailableAlertException.class);
        registry.recordPresence("worker", heartbeat(incarnation, 2, true));
        var claim = registry.claim("job", 1);
        assertThat(registry.availableWorkers()).isZero();
        assertThat(registry.renew(claim)).isTrue();
        assertThatThrownBy(() -> registry.claim("other", 2)).isInstanceOf(ServiceUnavailableAlertException.class);
        registry.release(claim);
        assertThat(registry.availableWorkers()).isEqualTo(1);
    }

    @Test
    void staleOwnerCannotReleaseOrRenewReplacement() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var old = registry.claim("old", 1);
        registry.release(old);
        var replacement = registry.claim("new", 2);
        registry.release(old);
        assertThat(registry.renew(old)).isFalse();
        assertThat(registry.renew(replacement)).isTrue();
        assertThat(registry.availableWorkers()).isZero();
    }

    @Test
    void ignoresOldSequenceAndCompetingIncarnation() {
        registry.recordPresence("worker", heartbeat(incarnation, 2, true));
        registry.recordPresence("worker", heartbeat(incarnation, 1, false));
        registry.recordPresence("worker", heartbeat(UUID.randomUUID(), 3, false));
        assertThat(registry.claim("job", 1).identity().workerIncarnation()).isEqualTo(incarnation);
    }

    @Test
    void rejectsWrongDestination() {
        assertThatThrownBy(() -> registry.recordPresence("other", heartbeat(incarnation, 1, true))).isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.reachableWorkers()).isZero();
    }

    private WorkerEvent heartbeat(UUID workerIncarnation, long sequence, boolean ready) {
        return new WorkerEvent(1, "worker", workerIncarnation, sequence, Instant.now(), WorkerEvent.Type.HEARTBEAT, null, ready, "sha256:" + "a".repeat(64), null, null, null);
    }
}

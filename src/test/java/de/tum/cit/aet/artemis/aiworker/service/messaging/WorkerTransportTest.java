package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerCommandType;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class WorkerTransportTest {

    @Test
    void constructionDoesNotRequireAnAvailableDistributedProvider() {
        DistributedDataProvider unavailableProvider = mock(DistributedDataProvider.class);
        new WorkerTransport(unavailableProvider, new WorkerMessageCodecApi());
        verifyNoInteractions(unavailableProvider);
    }

    private final LocalDataProviderService provider = new LocalDataProviderService();

    private final WorkerTransport worker = new WorkerTransport(provider, new WorkerMessageCodecApi());

    private final WorkerTransport core = new WorkerTransport(provider, new WorkerMessageCodecApi());

    private final WorkloadCapabilityDTO capability = new WorkloadCapabilityDTO("document-check", 1, "text");

    private final ExecutionIdentityDTO identity = new ExecutionIdentityDTO("job", "document:1", UUID.randomUUID(), "worker-1", UUID.randomUUID(), 0);

    @Test
    void eventStaysAvailableUntilCallbackSucceedsAndCannotReappearAfterAck() {
        WorkerEventDTO event = event(WorkerEventType.FINISHED, "done", 1);
        worker.publish(event);

        assertThatThrownBy(() -> core.receive(identity, _ -> {
            throw new IllegalStateException("core stopped before applying the event");
        })).isInstanceOf(IllegalStateException.class);

        AtomicInteger applied = new AtomicInteger();
        core.receive(identity, result -> {
            assertThat(result).isEqualTo(event);
            applied.incrementAndGet();
        });
        worker.publish(event);
        core.receive(identity, _ -> applied.incrementAndGet());
        assertThat(applied).hasValue(1);
    }

    @Test
    void largeEventIsSplitAndCheckedBeforeApply() {
        String payload = "x".repeat(1024 * 1024 + 7);
        worker.publish(event(WorkerEventType.CHECKPOINT, payload, 2));
        assertThat(provider.<String, String>getExpiringMap("aiworker-event-chunks", java.time.Duration.ofHours(4)).size()).isGreaterThan(1);
        core.receive(identity, result -> assertThat(result.payload()).isEqualTo(payload));
        assertThat(provider.<String, String>getExpiringMap("aiworker-event-chunks", java.time.Duration.ofHours(4)).isEmpty()).isTrue();
    }

    @Test
    void failedCommandIsRetriedThenDeadLetteredWithoutBlockingOtherWorkers() {
        var command = new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, identity,
                new ExecutionAssignmentDTO(identity, capability, Instant.now().plusSeconds(30), "sha256:" + "a".repeat(64), "document"));
        core.send(command);
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> worker.receiveCommands("worker-1", _ -> true, _ -> {
                throw new IllegalStateException("not ready");
            })).isInstanceOf(IllegalStateException.class);
        }
        worker.receiveCommands("worker-1", _ -> true, _ -> {
            throw new IllegalStateException("not ready");
        });
        assertThat(provider.<String, String>getExpiringMap("aiworker-commands", java.time.Duration.ofMinutes(2)).isEmpty()).isTrue();
        assertThat(provider.<String, String>getExpiringMap("aiworker-dead-commands", java.time.Duration.ofHours(24)).size()).isEqualTo(1);
    }

    @Test
    void heartbeatDoesNotEnterExecutionInbox() {
        worker.publish(event(WorkerEventType.HEARTBEAT, null, 3));
        assertThat(core.heartbeat("worker-1").type()).isEqualTo(WorkerEventType.HEARTBEAT);
        core.receive(identity, _ -> {
            throw new AssertionError("heartbeat must not enter the execution inbox");
        });
    }

    @Test
    void oldWorkerLeavesReplacementCommandForTheNewIncarnation() {
        var command = new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.RENEW, identity, null);
        core.send(command);
        worker.receiveCommands("worker-1", _ -> false, _ -> {
            throw new AssertionError("old worker must not accept the command");
        });
        AtomicInteger accepted = new AtomicInteger();
        worker.receiveCommands("worker-1", _ -> true, _ -> accepted.incrementAndGet());
        assertThat(accepted).hasValue(1);
    }

    @Test
    void twoCoordinatorsDoNotApplyTheSameEventAtOnce() throws Exception {
        worker.publish(event(WorkerEventType.FINISHED, "done", 4));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> core.receive(identity, _ -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("callback was not released");
                    }
                }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                applied.incrementAndGet();
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> core.receive(identity, _ -> applied.incrementAndGet()));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        assertThat(applied).hasValue(1);
    }

    private WorkerEventDTO event(WorkerEventType type, String payload, long sequence) {
        return new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", identity.workerIncarnation(), sequence, Instant.now(), type,
                type == WorkerEventType.HEARTBEAT ? null : identity, true, "sha256:" + "a".repeat(64), null, payload, new WorkerCapacityDTO(1, List.of()), capability);
    }
}

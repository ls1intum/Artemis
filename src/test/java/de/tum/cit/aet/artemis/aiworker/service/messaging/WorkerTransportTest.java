package de.tum.cit.aet.artemis.aiworker.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
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
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class WorkerTransportTest {

    @Test
    void pollingOnlyOpensTheSelectedWorkersMaps() {
        DistributedDataProvider scopedProvider = mock(DistributedDataProvider.class);
        DistributedMap<String, String> commands = mock(DistributedMap.class);
        DistributedMap<String, String> events = mock(DistributedMap.class);
        when(scopedProvider.<String, String>getExpiringMap("aiworker-commands-worker-1", java.time.Duration.ofMinutes(2))).thenReturn(commands);
        when(scopedProvider.<String, String>getMap("aiworker-events-worker-1")).thenReturn(events);
        when(commands.keySet()).thenReturn(Set.of());
        when(events.keySet()).thenReturn(Set.of());
        WorkerTransport scoped = new WorkerTransport(scopedProvider, new WorkerMessageCodecApi());

        scoped.receiveCommands("worker-1", _ -> true, _ -> {
        });
        scoped.receive(identity, _ -> {
        });

        verify(commands).keySet();
        verify(events).keySet();
        verify(scopedProvider).getExpiringMap("aiworker-commands-worker-1", java.time.Duration.ofMinutes(2));
        verify(scopedProvider).getMap("aiworker-events-worker-1");
    }

    @Test
    void constructionDoesNotRequireAnAvailableDistributedProvider() {
        DistributedDataProvider unavailableProvider = mock(DistributedDataProvider.class);
        new WorkerTransport(unavailableProvider, new WorkerMessageCodecApi());
        verifyNoInteractions(unavailableProvider);
    }

    private final LocalDataProviderService provider = spy(new LocalDataProviderService());

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

        assertThat(provider.getMap("aiworker-events-worker-1").size()).isEqualTo(1);
        assertThat(provider.getMap("aiworker-event-chunks").isEmpty()).isFalse();
        verify(provider, never()).getExpiringMap(eq("aiworker-events-worker-1"), any());
        verify(provider, never()).getExpiringMap(eq("aiworker-event-chunks"), any());
        assertThat(provider.getExpiringMap("aiworker-event-acknowledgements", java.time.Duration.ofHours(4)).isEmpty()).isTrue();
        AtomicInteger applied = new AtomicInteger();
        core.receive(identity, result -> {
            assertThat(result).isEqualTo(event);
            applied.incrementAndGet();
        });
        worker.publish(event);
        core.receive(identity, _ -> applied.incrementAndGet());
        assertThat(applied).hasValue(1);
        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isTrue();
        assertThat(provider.getMap("aiworker-event-chunks").isEmpty()).isTrue();
        assertThat(provider.getExpiringMap("aiworker-event-acknowledgements", java.time.Duration.ofHours(4)).size()).isEqualTo(1);
    }

    @Test
    void largeEventIsSplitAndCheckedBeforeApply() {
        String payload = "x".repeat(1024 * 1024 + 7);
        worker.publish(event(WorkerEventType.CHECKPOINT, payload, 2));
        assertThat(provider.<String, String>getMap("aiworker-event-chunks").size()).isGreaterThan(1);
        core.receive(identity, result -> assertThat(result.payload()).isEqualTo(payload));
        assertThat(provider.<String, String>getMap("aiworker-event-chunks").isEmpty()).isTrue();
    }

    @Test
    void acknowledgedEventFinishesCleanupAfterReplayTombstoneExpires() {
        DistributedMap<String, String> chunks = spy(provider.getMap("aiworker-event-chunks"));
        DistributedDataProvider faultingProvider = provider;
        when(faultingProvider.<String, String>getMap("aiworker-event-chunks")).thenReturn(chunks);
        WorkerTransport faultingCore = new WorkerTransport(faultingProvider, new WorkerMessageCodecApi());
        worker.publish(event(WorkerEventType.CHECKPOINT, "x".repeat(1024 * 1024), 1));
        worker.publish(event(WorkerEventType.FINISHED, "done", 2));
        AtomicInteger applied = new AtomicInteger();
        org.mockito.Mockito.doCallRealMethod().doThrow(new IllegalStateException("cleanup interrupted")).doCallRealMethod().when(chunks).remove(any(String.class));

        assertThatThrownBy(() -> faultingCore.receive(identity, _ -> applied.incrementAndGet())).isInstanceOf(IllegalStateException.class);
        DistributedMap<String, String> inbox = provider.getMap("aiworker-events-worker-1");
        assertThat(inbox.values()).anyMatch(manifest -> manifest.startsWith("A:"));
        provider.getExpiringMap("aiworker-event-acknowledgements", java.time.Duration.ofHours(4)).clear();
        assertThat(faultingCore.receive(identity, _ -> applied.incrementAndGet())).isTrue();
        assertThat(applied).hasValue(1);
        assertThat(faultingCore.receive(identity, _ -> applied.incrementAndGet())).isTrue();
        assertThat(applied).hasValue(2);
        assertThat(inbox.isEmpty()).isTrue();
        assertThat(chunks.isEmpty()).isTrue();
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
        assertThat(provider.<String, String>getExpiringMap("aiworker-commands-worker-1", java.time.Duration.ofMinutes(2)).isEmpty()).isTrue();
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

    @Test
    void prunePreservesLiveExecutionAndRemovesAbandonedDelivery() {
        var live = new ExecutionIdentityDTO("live", "document:2", UUID.randomUUID(), "worker-1", identity.workerIncarnation(), 1);
        worker.publish(event(WorkerEventType.CHECKPOINT, "abandoned", 20));
        var liveEvent = new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", live.workerIncarnation(), 21, Instant.now(), WorkerEventType.FINISHED, live, true,
                "sha256:" + "a".repeat(64), null, "live output", new WorkerCapacityDTO(2, List.of()), capability);
        worker.publish(liveEvent);
        worker.pruneUnclaimed("worker-1", execution -> execution.equals(live.executionId()));

        assertThat(core.receive(identity, _ -> {
            throw new AssertionError("abandoned execution remained");
        })).isFalse();
        assertThat(provider.getMap("aiworker-events-worker-1").size()).isEqualTo(1);
        assertThat(core.receive(live, observed -> assertThat(observed).isEqualTo(liveEvent))).isTrue();
        assertThat(provider.getMap("aiworker-event-chunks").isEmpty()).isTrue();
    }

    @Test
    void pruneRemovesIncompleteManifestsAndIndependentOrphanChunksAndAcknowledgements() {
        DistributedMap<String, String> chunks = spy(provider.getMap("aiworker-event-chunks"));
        when(provider.<String, String>getMap("aiworker-event-chunks")).thenReturn(chunks);
        var transport = new WorkerTransport(provider, new WorkerMessageCodecApi());
        org.mockito.Mockito.doCallRealMethod().doThrow(new IllegalStateException("interrupted publication")).when(chunks).put(any(String.class), any(String.class));
        assertThatThrownBy(() -> transport.publish(event(WorkerEventType.CHECKPOINT, "x".repeat(1024 * 1024), 22))).isInstanceOf(IllegalStateException.class);
        assertThat(provider.getMap("aiworker-events-worker-1").values()).anyMatch(value -> value.toString().startsWith("W:"));
        assertThat(chunks.isEmpty()).isFalse();
        org.mockito.Mockito.doCallRealMethod().when(chunks).put(any(String.class), any(String.class));
        String orphan = "worker-1:" + UUID.randomUUID() + ":0000000000000000023";
        chunks.put(orphan + ":0", "orphan chunk");
        provider.getExpiringMap("aiworker-event-acknowledgements", java.time.Duration.ofHours(4)).put(orphan, "orphan acknowledgement");

        transport.pruneUnclaimed("worker-1", _ -> false);

        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isTrue();
        assertThat(chunks.isEmpty()).isTrue();
        assertThat(provider.getExpiringMap("aiworker-event-acknowledgements", java.time.Duration.ofHours(4)).isEmpty()).isTrue();
    }

    @Test
    void nextSweepReclaimsPhysicalWritesFromALatePublisher() {
        worker.publish(event(WorkerEventType.CHECKPOINT, "before owner loss", 24));
        worker.pruneUnclaimed("worker-1", _ -> false);
        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isTrue();

        // A paused publisher may still finish; cleanup does not claim to fence it.
        worker.publish(event(WorkerEventType.FINISHED, "late output", 25));
        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isFalse();
        provider.getMap("aiworker-events-worker-1").clear();
        assertThat(provider.getMap("aiworker-event-chunks").isEmpty()).isFalse();
        worker.pruneUnclaimed("worker-1", _ -> false);

        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isTrue();
        assertThat(provider.getMap("aiworker-event-chunks").isEmpty()).isTrue();
    }

    @Test
    void ownershipReadFailureNeverMeansAnUnclaimedExecution() {
        worker.publish(event(WorkerEventType.FINISHED, "keep evidence", 26));
        var unavailable = new IllegalStateException("lease store unavailable");

        assertThatThrownBy(() -> worker.pruneUnclaimed("worker-1", _ -> {
            throw unavailable;
        })).isSameAs(unavailable);
        assertThat(provider.getMap("aiworker-events-worker-1").size()).isEqualTo(1);
        assertThat(provider.getMap("aiworker-event-chunks").isEmpty()).isFalse();
        assertThat(core.receive(identity, observed -> assertThat(observed.payload()).isEqualTo("keep evidence"))).isTrue();
    }

    @Test
    void failedDeletionLeavesAbandonedEvidenceForTheNextSweep() {
        DistributedMap<String, String> chunks = spy(provider.getMap("aiworker-event-chunks"));
        when(provider.<String, String>getMap("aiworker-event-chunks")).thenReturn(chunks);
        var transport = new WorkerTransport(provider, new WorkerMessageCodecApi());
        transport.publish(event(WorkerEventType.FINISHED, "retry cleanup", 29));
        org.mockito.Mockito.doThrow(new IllegalStateException("delete unavailable")).doCallRealMethod().when(chunks).remove(any(String.class), any(String.class));

        assertThatThrownBy(() -> transport.pruneUnclaimed("worker-1", _ -> false)).isInstanceOf(IllegalStateException.class);
        assertThat(chunks.isEmpty()).isFalse();
        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isFalse();
        transport.pruneUnclaimed("worker-1", _ -> false);
        assertThat(chunks.isEmpty()).isTrue();
        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isTrue();
    }

    @Test
    void pruneUsesTheObservedValueAndRetriesAChangedEntryOnTheNextSweep() {
        DistributedMap<String, String> chunks = spy(provider.getMap("aiworker-event-chunks"));
        when(provider.<String, String>getMap("aiworker-event-chunks")).thenReturn(chunks);
        var transport = new WorkerTransport(provider, new WorkerMessageCodecApi());
        String key = "worker-1:" + identity.executionId() + ":0000000000000000027:0";
        chunks.put(key, "observed chunk");
        org.mockito.Mockito.doAnswer(invocation -> {
            chunks.put(key, "late chunk");
            return invocation.callRealMethod();
        }).when(chunks).remove(key, "observed chunk");

        transport.pruneUnclaimed("worker-1", _ -> false);
        assertThat(chunks.get(key)).isEqualTo("late chunk");
        transport.pruneUnclaimed("worker-1", _ -> false);
        assertThat(chunks.isEmpty()).isTrue();
    }

    @Test
    void pruneRequiresCanonicalExecutionKeysAndTheExactWorkerPrefix() {
        DistributedMap<String, String> chunks = provider.getMap("aiworker-event-chunks");
        List<String> keys = List.of("worker-10:" + identity.executionId() + ":0000000000000000028:0", "worker-1:1-1-1-1-1:0000000000000000028:0",
                "worker-1:" + identity.executionId() + ":28:0", "worker-1:" + identity.executionId() + ":0000000000000000028:-1",
                "worker-1:" + identity.executionId() + ":0000000000000000028:00", "worker-1:" + identity.executionId() + ":0000000000000000000:0:extra");
        keys.forEach(key -> chunks.put(key, "keep malformed or foreign entry"));

        worker.pruneUnclaimed("worker-1", _ -> {
            throw new AssertionError("malformed or foreign entry reached lease lookup");
        });

        assertThat(chunks.keySet()).containsExactlyInAnyOrderElementsOf(keys);
    }

    @Test
    void pruneDoesNotLoadLiveChunkPayloads() {
        DistributedMap<String, String> chunks = spy(provider.getMap("aiworker-event-chunks"));
        when(provider.<String, String>getMap("aiworker-event-chunks")).thenReturn(chunks);
        var transport = new WorkerTransport(provider, new WorkerMessageCodecApi());
        String key = "worker-1:" + identity.executionId() + ":0000000000000000032:0";
        chunks.put(key, "live chunk");

        transport.pruneUnclaimed("worker-1", identity.executionId()::equals);

        verify(chunks, never()).entrySet();
        verify(chunks, never()).get(any());
        assertThat(chunks.size()).isEqualTo(1);
    }

    @Test
    void pruneReclaimsZeroSequenceManifestsAndChunks() {
        String key = "worker-1:" + identity.executionId() + ":0000000000000000000";
        provider.<String, String>getMap("aiworker-events-worker-1").put(key, "W:1:1:unused:1");
        provider.<String, String>getMap("aiworker-event-chunks").put(key + ":0", "x");

        worker.pruneUnclaimed("worker-1", _ -> false);

        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isTrue();
        assertThat(provider.getMap("aiworker-event-chunks").isEmpty()).isTrue();
    }

    @Test
    void pruningAnAbandonedManifestRestoresTheLiveWorkersPublicationQuota() {
        String abandoned = "worker-1:" + identity.executionId() + ":0000000000000000030";
        provider.<String, String>getMap("aiworker-events-worker-1").put(abandoned, "W:1:1:" + "0".repeat(64) + ":268435456");
        var live = new ExecutionIdentityDTO("live", "document:2", UUID.randomUUID(), "worker-1", identity.workerIncarnation(), 1);
        var liveEvent = new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", live.workerIncarnation(), 31, Instant.now(), WorkerEventType.FINISHED, live, true,
                "sha256:" + "a".repeat(64), null, "live output", new WorkerCapacityDTO(2, List.of()), capability);

        assertThatThrownBy(() -> worker.publish(liveEvent)).isInstanceOf(IllegalStateException.class).hasMessage("Worker event storage is full");
        assertThat(provider.getMap("aiworker-events-worker-1").size()).isEqualTo(1);
        worker.pruneUnclaimed("worker-1", execution -> execution.equals(live.executionId()));
        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isTrue();

        worker.publish(liveEvent);
        assertThat(core.receive(live, observed -> assertThat(observed).isEqualTo(liveEvent))).isTrue();
        assertThat(provider.getMap("aiworker-events-worker-1").isEmpty()).isTrue();
        assertThat(provider.getMap("aiworker-event-chunks").isEmpty()).isTrue();
    }

    private WorkerEventDTO event(WorkerEventType type, String payload, long sequence) {
        return new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, "worker-1", identity.workerIncarnation(), sequence, Instant.now(), type,
                type == WorkerEventType.HEARTBEAT ? null : identity, true, "sha256:" + "a".repeat(64), null, payload, new WorkerCapacityDTO(1, List.of()), capability);
    }
}

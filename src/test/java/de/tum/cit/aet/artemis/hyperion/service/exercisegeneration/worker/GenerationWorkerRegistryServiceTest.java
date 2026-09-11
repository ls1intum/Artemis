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
import de.tum.cit.aet.artemis.hyperion.domain.GenerationWorkerState;
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

    @Test
    void completedWorkerCanBeClaimedAgainWithoutWaitingForHeartbeat() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var claim = registry.claim("first", 1);
        WorkerEvent busy = new WorkerEvent(de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(),
                WorkerEvent.Type.HEARTBEAT, claim.identity(), false, claim.imageDigest(), null, null, null);
        registry.recordPresence("worker", busy);

        registry.recordCompletion(claim, completion(claim, 3, true));
        assertThat(registry.availableWorkers()).isZero();
        registry.release(claim);
        registry.recordPresence("worker", busy);

        assertThat(registry.availableWorkers()).isEqualTo(1);
        assertThat(registry.claim("second", 2).identity().executionId()).isNotEqualTo(claim.identity().executionId());
    }

    @Test
    void completionCannotRestoreCapacityWhenCleanupFailedOrWorkerIsDraining() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var claim = registry.claim("first", 1);
        registry.recordCompletion(claim, completion(claim, 2, false));
        registry.release(claim);
        assertThat(registry.availableWorkers()).isZero();
        assertThatThrownBy(() -> registry.claim("second", 2)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void staleCompletionCannotClearReplacementWorkerActivity() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var old = registry.claim("old", 1);
        registry.release(old);
        var replacement = registry.claim("replacement", 2);
        registry.recordPresence("worker", new WorkerEvent(de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(),
                WorkerEvent.Type.HEARTBEAT, replacement.identity(), false, replacement.imageDigest(), null, null, null));

        registry.recordCompletion(old, completion(old, 3, true));

        assertThat(registry.renew(replacement)).isTrue();
        registry.release(replacement);
        assertThat(registry.availableWorkers()).isZero();
    }

    @Test
    void completionRequiresTerminalEvidenceForTheExactClaim() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var claim = registry.claim("first", 1);
        assertThatThrownBy(() -> registry.recordCompletion(claim, heartbeat(incarnation, 2, true))).isInstanceOf(IllegalArgumentException.class);
        registry.release(claim);
        var replacement = registry.claim("replacement", 2);
        assertThatThrownBy(() -> registry.recordCompletion(replacement, completion(claim, 3, true))).isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.renew(replacement)).isTrue();
    }

    private WorkerEvent completion(GenerationWorkerRegistryService.Claim claim, long sequence, boolean ready) {
        return new WorkerEvent(de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand.PROTOCOL_VERSION, "worker", incarnation, sequence, Instant.now(), WorkerEvent.Type.ERROR,
                claim.identity(), ready, claim.imageDigest(), "Generation failed", null, null);
    }

    @Test
    void coordinatorsShareFourAtomicSlotsAndCancellationReleasesOnlyOne() throws Exception {
        var otherCore = new GenerationWorkerRegistryService(properties, mock(ConnectionFactory.class), new WorkerMessageCodec(), data);
        registry.recordPresence("worker", heartbeat(incarnation, 1, true).withCapacity(new de.tum.cit.aet.artemis.hyperion.protocol.WorkerCapacity(4, List.of())));
        var barrier = new java.util.concurrent.CyclicBarrier(8);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<GenerationWorkerRegistryService.Claim>>();
            for (int index = 0; index < 8; index++) {
                final int number = index;
                futures.add(executor.submit(() -> {
                    barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    try {
                        return (number % 2 == 0 ? registry : otherCore).claim("job-" + number, number + 1);
                    }
                    catch (ServiceUnavailableAlertException full) {
                        return null;
                    }
                }));
            }
            var claims = new java.util.ArrayList<GenerationWorkerRegistryService.Claim>();
            for (var future : futures) {
                var claim = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                if (claim != null) {
                    claims.add(claim);
                }
            }
            assertThat(claims).hasSize(4);
            assertThat(claims).extracting(claim -> claim.identity().slot()).containsExactlyInAnyOrder(0, 1, 2, 3);
            assertThat(registry.hasAvailableGenerationSandboxSlot()).isFalse();
            var cancelled = claims.removeFirst();
            otherCore.release(cancelled);
            var replacement = registry.claim("replacement", 20);
            assertThat(replacement.identity().slot()).isEqualTo(cancelled.identity().slot());
            registry.release(cancelled);
            assertThat(registry.renew(cancelled)).isFalse();
            assertThat(otherCore.renew(replacement)).isTrue();
            assertThat(claims).allMatch(otherCore::renew);
        }
    }

    @Test
    void completingOneSlotDoesNotForgetOtherExecutionWhenItsCoreLeaseExpires() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true).withCapacity(new de.tum.cit.aet.artemis.hyperion.protocol.WorkerCapacity(4, List.of())));
        var first = registry.claim("first", 1);
        var second = registry.claim("second", 2);
        var terminal = new WorkerEvent(de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(), WorkerEvent.Type.CANCELLED,
                first.identity(), true, first.imageDigest(), null, null, null)
                .withCapacity(new de.tum.cit.aet.artemis.hyperion.protocol.WorkerCapacity(4, List.of(second.identity())));
        registry.recordCompletion(first, terminal);
        registry.release(first);
        registry.release(second);
        var replacements = List.of(registry.claim("third", 3), registry.claim("fourth", 4), registry.claim("fifth", 5));
        assertThat(replacements).noneMatch(claim -> claim.identity().slot() == second.identity().slot());
        assertThatThrownBy(() -> registry.claim("sixth", 6)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void reportsConfiguredOfflineAndUnreadyWorkersWithoutClaimingCapacity() {
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.workerId()).isEqualTo("worker");
            assertThat(status.state()).isEqualTo(GenerationWorkerState.OFFLINE);
            assertThat(status.lastHeartbeat()).isNull();
            assertThat(status.leaseHeld()).isFalse();
        });
        registry.recordPresence("worker", heartbeat(incarnation, 1, false));
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(GenerationWorkerState.NOT_READY);
            assertThat(status.lastHeartbeat()).isNotNull();
            assertThat(status.incarnation()).isEqualTo(incarnation);
            assertThat(status.imageDigest()).isEqualTo("sha256:" + "a".repeat(64));
        });
        registry.recordPresence("worker", heartbeat(incarnation, 2, true));
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> assertThat(status.state()).isEqualTo(GenerationWorkerState.AVAILABLE));
        assertThat(registry.availableWorkers()).isEqualTo(1);
    }

    @Test
    void distinguishesCoreReservationFromActiveWorkerExecutionAndExpiredPresence() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var claim = registry.claim("job", 1);
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(GenerationWorkerState.RESERVED);
            assertThat(status.activeExecution()).isNull();
            assertThat(status.leaseHeld()).isTrue();
        });
        registry.recordPresence("worker", new WorkerEvent(de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(),
                WorkerEvent.Type.HEARTBEAT, claim.identity(), false, claim.imageDigest(), null, null, null));
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(GenerationWorkerState.BUSY);
            assertThat(status.activeExecution()).isEqualTo(claim.identity().executionId());
        });
        data.getExpiringMap("hyperion-worker-presence", properties.presenceTtl()).clear();
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(GenerationWorkerState.OFFLINE);
            assertThat(status.leaseHeld()).isTrue();
            assertThat(status.activeExecution()).isNull();
        });
        assertThat(registry.availableWorkers()).isZero();
    }

    private WorkerEvent heartbeat(UUID workerIncarnation, long sequence, boolean ready) {
        return new WorkerEvent(de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand.PROTOCOL_VERSION, "worker", workerIncarnation, sequence, Instant.now(),
                WorkerEvent.Type.HEARTBEAT, null, ready, "sha256:" + "a".repeat(64), null, null, null);
    }
}

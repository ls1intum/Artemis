package de.tum.cit.aet.artemis.aiworker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.jms.ConnectionFactory;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.config.AiWorkerProperties;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerState;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionClaimDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class WorkerRegistryServiceTest {

    private final LocalDataProviderService data = new LocalDataProviderService();

    private final AiWorkerProperties properties = new AiWorkerProperties("tcp://broker:61617?sslEnabled=true", "core", "test-password", List.of("worker"), Duration.ofSeconds(30),
            Duration.ofSeconds(45));

    private final WorkerRegistryService registry = new WorkerRegistryService(properties, mock(ConnectionFactory.class), new WorkerMessageCodecApi(), data);

    private final UUID incarnation = UUID.randomUUID();

    @Test
    void routesOnlyToMatchingToolchainAndFencesChangedIdentity() {
        var python = new WorkloadCapabilityDTO("another-workload", 1, "python-pytest");
        var java = CAPABILITY;
        registry.recordPresence("worker", heartbeat(incarnation, 1, true).withCapability(python));
        assertThat(registry.hasAvailableSlot(java)).isFalse();
        assertThat(registry.hasAvailableSlot(python)).isTrue();
        assertThatThrownBy(() -> registry.claim("java", "resource-1", java)).isInstanceOf(ServiceUnavailableAlertException.class);
        var claim = registry.claim("python", "resource-2", python);
        assertThat(claim.capability()).isEqualTo(python);
        assertThat(registry.renew(claim)).isTrue();
        assertThatThrownBy(() -> registry.recordCompletion(claim, completion(claim, 2, true))).isInstanceOf(IllegalArgumentException.class);
        registry.recordPresence("worker", heartbeat(incarnation, 3, true));
        assertThat(registry.renew(claim)).isFalse();
    }

    @Test
    void requiresReadyWorkerAndExclusiveClaim() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, false));
        assertThat(registry.reachableWorkers()).isEqualTo(1);
        assertThat(registry.capableWorkers()).isZero();
        assertThatThrownBy(() -> registry.claim("job", "resource-1", CAPABILITY)).isInstanceOf(ServiceUnavailableAlertException.class);
        registry.recordPresence("worker", heartbeat(incarnation, 2, true));
        var claim = registry.claim("job", "resource-1", CAPABILITY);
        assertThat(registry.availableWorkers()).isZero();
        assertThat(registry.renew(claim)).isTrue();
        assertThatThrownBy(() -> registry.claim("other", "resource-2", CAPABILITY)).isInstanceOf(ServiceUnavailableAlertException.class);
        registry.release(claim);
        assertThat(registry.availableWorkers()).isEqualTo(1);
    }

    @Test
    void staleOwnerCannotReleaseOrRenewReplacement() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var old = registry.claim("old", "resource-1", CAPABILITY);
        registry.release(old);
        var replacement = registry.claim("new", "resource-2", CAPABILITY);
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
        assertThat(registry.claim("job", "resource-1", CAPABILITY).identity().workerIncarnation()).isEqualTo(incarnation);
    }

    @Test
    void rejectsWrongDestination() {
        assertThatThrownBy(() -> registry.recordPresence("other", heartbeat(incarnation, 1, true))).isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.reachableWorkers()).isZero();
    }

    @Test
    void completedWorkerCanBeClaimedAgainWithoutWaitingForHeartbeat() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var claim = registry.claim("first", "resource-1", CAPABILITY);
        WorkerEventDTO busy = event(WorkerCommandDTO.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(), WorkerEventType.HEARTBEAT, claim.identity(), false,
                claim.imageDigest(), null, null, null);
        registry.recordPresence("worker", busy);

        registry.recordCompletion(claim, completion(claim, 3, true));
        assertThat(registry.availableWorkers()).isZero();
        registry.release(claim);
        registry.recordPresence("worker", busy);

        assertThat(registry.availableWorkers()).isEqualTo(1);
        assertThat(registry.claim("second", "resource-2", CAPABILITY).identity().executionId()).isNotEqualTo(claim.identity().executionId());
    }

    @Test
    void completionCannotRestoreCapacityWhenCleanupFailedOrWorkerIsDraining() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var claim = registry.claim("first", "resource-1", CAPABILITY);
        registry.recordCompletion(claim, completion(claim, 2, false));
        registry.release(claim);
        assertThat(registry.availableWorkers()).isZero();
        assertThatThrownBy(() -> registry.claim("second", "resource-2", CAPABILITY)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void staleCompletionCannotClearReplacementWorkerActivity() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var old = registry.claim("old", "resource-1", CAPABILITY);
        registry.release(old);
        var replacement = registry.claim("replacement", "resource-2", CAPABILITY);
        registry.recordPresence("worker", event(WorkerCommandDTO.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(), WorkerEventType.HEARTBEAT, replacement.identity(),
                false, replacement.imageDigest(), null, null, null));

        registry.recordCompletion(old, completion(old, 3, true));

        assertThat(registry.renew(replacement)).isTrue();
        registry.release(replacement);
        assertThat(registry.availableWorkers()).isZero();
    }

    @Test
    void completionRequiresTerminalEvidenceForTheExactClaim() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var claim = registry.claim("first", "resource-1", CAPABILITY);
        assertThatThrownBy(() -> registry.recordCompletion(claim, heartbeat(incarnation, 2, true))).isInstanceOf(IllegalArgumentException.class);
        registry.release(claim);
        var replacement = registry.claim("replacement", "resource-2", CAPABILITY);
        assertThatThrownBy(() -> registry.recordCompletion(replacement, completion(claim, 3, true))).isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.renew(replacement)).isTrue();
    }

    private WorkerEventDTO completion(ExecutionClaimDTO claim, long sequence, boolean ready) {
        return event(WorkerCommandDTO.PROTOCOL_VERSION, "worker", incarnation, sequence, Instant.now(), WorkerEventType.ERROR, claim.identity(), ready, claim.imageDigest(),
                "Generation failed", null, null);
    }

    @Test
    void coordinatorsShareFourAtomicSlotsAndCancellationReleasesOnlyOne() throws Exception {
        var otherCore = new WorkerRegistryService(properties, mock(ConnectionFactory.class), new WorkerMessageCodecApi(), data);
        registry.recordPresence("worker", heartbeat(incarnation, 1, true).withCapacity(new WorkerCapacityDTO(4, List.of())));
        var barrier = new java.util.concurrent.CyclicBarrier(8);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<ExecutionClaimDTO>>();
            for (int index = 0; index < 8; index++) {
                final int number = index;
                futures.add(executor.submit(() -> {
                    barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    try {
                        return (number % 2 == 0 ? registry : otherCore).claim("job-" + number, "resource-" + number, CAPABILITY);
                    }
                    catch (ServiceUnavailableAlertException full) {
                        return null;
                    }
                }));
            }
            var claims = new java.util.ArrayList<ExecutionClaimDTO>();
            for (var future : futures) {
                var claim = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                if (claim != null) {
                    claims.add(claim);
                }
            }
            assertThat(claims).hasSize(4);
            assertThat(claims).extracting(claim -> claim.identity().slot()).containsExactlyInAnyOrder(0, 1, 2, 3);
            assertThat(registry.hasAvailableSlot()).isFalse();
            var cancelled = claims.removeFirst();
            otherCore.release(cancelled);
            var replacement = registry.claim("replacement", "resource-20", CAPABILITY);
            assertThat(replacement.identity().slot()).isEqualTo(cancelled.identity().slot());
            registry.release(cancelled);
            assertThat(registry.renew(cancelled)).isFalse();
            assertThat(otherCore.renew(replacement)).isTrue();
            assertThat(claims).allMatch(otherCore::renew);
        }
    }

    @Test
    void completingOneSlotDoesNotForgetOtherExecutionWhenItsCoreLeaseExpires() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true).withCapacity(new WorkerCapacityDTO(4, List.of())));
        var first = registry.claim("first", "resource-1", CAPABILITY);
        var second = registry.claim("second", "resource-2", CAPABILITY);
        var terminal = event(WorkerCommandDTO.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(), WorkerEventType.CANCELLED, first.identity(), true, first.imageDigest(),
                null, null, null).withCapacity(new WorkerCapacityDTO(4, List.of(second.identity())));
        registry.recordCompletion(first, terminal);
        registry.release(first);
        registry.release(second);
        var replacements = List.of(registry.claim("third", "resource-3", CAPABILITY), registry.claim("fourth", "resource-4", CAPABILITY),
                registry.claim("fifth", "resource-5", CAPABILITY));
        assertThat(replacements).noneMatch(claim -> claim.identity().slot() == second.identity().slot());
        assertThatThrownBy(() -> registry.claim("sixth", "resource-6", CAPABILITY)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void reportsConfiguredOfflineAndUnreadyWorkersWithoutClaimingCapacity() {
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.workerId()).isEqualTo("worker");
            assertThat(status.state()).isEqualTo(WorkerState.OFFLINE);
            assertThat(status.lastHeartbeat()).isNull();
            assertThat(status.capability()).isNull();
            assertThat(status.capacity()).isZero();
            assertThat(status.leaseHeld()).isFalse();
        });
        registry.recordPresence("worker", heartbeat(incarnation, 1, false));
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(WorkerState.NOT_READY);
            assertThat(status.lastHeartbeat()).isNotNull();
            assertThat(status.incarnation()).isEqualTo(incarnation);
            assertThat(status.capability()).isEqualTo(CAPABILITY);
            assertThat(status.imageDigest()).isEqualTo("sha256:" + "a".repeat(64));
        });
        registry.recordPresence("worker", heartbeat(incarnation, 2, true));
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> assertThat(status.state()).isEqualTo(WorkerState.AVAILABLE));
        assertThat(registry.availableWorkers()).isEqualTo(1);
    }

    @Test
    void distinguishesCoreReservationFromActiveWorkerExecutionAndExpiredPresence() {
        registry.recordPresence("worker", heartbeat(incarnation, 1, true));
        var claim = registry.claim("job", "resource-1", CAPABILITY);
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(WorkerState.RESERVED);
            assertThat(status.activeExecution()).isNull();
            assertThat(status.leaseHeld()).isTrue();
        });
        registry.recordPresence("worker", event(WorkerCommandDTO.PROTOCOL_VERSION, "worker", incarnation, 2, Instant.now(), WorkerEventType.HEARTBEAT, claim.identity(), false,
                claim.imageDigest(), null, null, null));
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(WorkerState.BUSY);
            assertThat(status.activeExecution()).isEqualTo(claim.identity().executionId());
        });
        data.getExpiringMap("aiworker-presence", properties.presenceTtl()).clear();
        assertThat(registry.workerStatuses()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(WorkerState.OFFLINE);
            assertThat(status.leaseHeld()).isTrue();
            assertThat(status.activeExecution()).isNull();
        });
        assertThat(registry.availableWorkers()).isZero();
    }

    private WorkerEventDTO heartbeat(UUID workerIncarnation, long sequence, boolean ready) {
        return event(WorkerCommandDTO.PROTOCOL_VERSION, "worker", workerIncarnation, sequence, Instant.now(), WorkerEventType.HEARTBEAT, null, ready, "sha256:" + "a".repeat(64),
                null, null, null);
    }

    private static final WorkloadCapabilityDTO CAPABILITY = new WorkloadCapabilityDTO("text-analysis", 1, "text");

    private static WorkerEventDTO event(int protocol, String worker, UUID incarnation, long sequence, Instant time, WorkerEventType type, ExecutionIdentityDTO identity,
            boolean ready, String image, String message, Object unusedActivity, Object unusedOutput) {
        return new WorkerEventDTO(protocol, worker, incarnation, sequence, time, type, identity, ready, image, message, null,
                new WorkerCapacityDTO(1, type == WorkerEventType.HEARTBEAT && identity != null ? List.of(identity) : List.of()), CAPABILITY);
    }

}

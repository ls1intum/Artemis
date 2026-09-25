package de.tum.cit.aet.artemis.aiworker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.api.WorkloadApi;
import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerCommandType;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;

class WorkerSupervisorTest {

    private static final String IMAGE = "sha256:" + "a".repeat(64);

    @Test
    void oversizedAccountingCannotFillTheRetainedDeliveryQueue() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        try (var worker = worker(events, (assignment, cancelled, observer, checkpoint) -> {
            observer.progress("Usage recorded", "x".repeat(WorkerEventDTO.MAX_ACCOUNTING_PAYLOAD_LENGTH + 1), true);
            return result();
        }, () -> {
        }, new AtomicLong())) {
            WorkerCommandDTO command = start(worker, events);
            worker.accept(command);

            WorkerEventDTO terminal = take(events, WorkerEventType.ERROR);
            assertThat(terminal.identity()).isEqualTo(command.identity());
            assertThat(terminal.payload()).isNull();
            assertThat(events).noneMatch(event -> event.type() == WorkerEventType.ACCOUNTING || event.type() == WorkerEventType.FINISHED);
        }
    }

    @Test
    void invalidWorkloadOutputRetainsAnErrorTerminal() throws InterruptedException {
        for (String output : new String[] { null, "", "x".repeat(WorkerEventDTO.MAX_PAYLOAD_LENGTH + 1) }) {
            var events = new LinkedBlockingQueue<WorkerEventDTO>();
            try (var worker = worker(events, (assignment, cancelled, observer, checkpoint) -> output, () -> {
            }, new AtomicLong())) {
                WorkerCommandDTO command = start(worker, events);
                worker.accept(command);
                WorkerEventDTO terminal = take(events, WorkerEventType.ERROR);
                assertThat(terminal.identity()).isEqualTo(command.identity());
                assertThat(terminal.payload()).isNull();
                assertThat(terminal.ready()).isTrue();
            }
        }
    }

    @Test
    void blankCheckpointRetainsAnErrorTerminal() throws InterruptedException {
        for (String checkpointText : new String[] { "", " " }) {
            var events = new LinkedBlockingQueue<WorkerEventDTO>();
            try (var worker = worker(events, (assignment, cancelled, observer, checkpoint) -> {
                checkpoint.accept(checkpointText);
                return result();
            }, () -> {
            }, new AtomicLong())) {
                WorkerCommandDTO command = start(worker, events);
                worker.accept(command);
                WorkerEventDTO terminal = take(events, WorkerEventType.ERROR);
                assertThat(terminal.identity()).isEqualTo(command.identity());
                assertThat(terminal.ready()).isTrue();
                assertThat(events).noneMatch(event -> event.type() == WorkerEventType.CHECKPOINT || event.type() == WorkerEventType.FINISHED);
            }
        }
    }

    @Test
    void blockedRejectionPublicationDoesNotHoldTheCommandListener() throws Exception {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var publishing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var commands = java.util.concurrent.Executors.newSingleThreadExecutor(); var worker = new WorkerSupervisorService(settings(), event -> {
            if (event.type() == WorkerEventType.ERROR) {
                publishing.countDown();
                try {
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            events.add(event);
        }, () -> (TestWorkload) (a, c, p, s) -> result(), () -> {
        }, () -> IMAGE, System::nanoTime)) {
            var valid = start(worker, events);
            var id = new ExecutionIdentityDTO("rejected", "resource-2", UUID.randomUUID(), valid.identity().workerId(), valid.identity().workerIncarnation(), 1);
            var rejected = new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, id,
                    new ExecutionAssignmentDTO(id, CAPABILITY, valid.assignment().deadline(), IMAGE, "input"));
            try {
                var admission = commands.submit(() -> worker.accept(rejected));
                assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
                admission.get(1, TimeUnit.SECONDS);
                commands.submit(() -> worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.RENEW, id, null))).get(1, TimeUnit.SECONDS);
                commands.submit(() -> worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, id, null))).get(1, TimeUnit.SECONDS);
            }
            finally {
                release.countDown();
            }
        }
    }

    @Test
    void finishRequestsCooperativeCompletionWithoutCancellingOrDiscardingEvidence() throws Exception {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var entered = new CountDownLatch(1);
        var finishSent = new CountDownLatch(1);
        try (var worker = worker(events, (assignment, stopping, observer, checkpoint) -> {
            entered.countDown();
            await(finishSent);
            assertThat(stopping.getAsBoolean()).isTrue();
            checkpoint.accept("checkpoint");
            return "completed";
        }, () -> {
        }, new AtomicLong())) {
            var command = start(worker, events);
            worker.accept(command);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.FINISH, command.identity(), null));
            finishSent.countDown();
            assertThat(take(events, WorkerEventType.CHECKPOINT).payload()).isEqualTo("checkpoint");
            assertThat(take(events, WorkerEventType.FINISHED).payload()).isEqualTo("completed");
            assertThat(events).noneMatch(event -> event.type() == WorkerEventType.CANCELLED);
        }
        finally {
            finishSent.countDown();
        }
    }

    @Test
    void rejectsAnotherToolchainBeforeInvokingTheEngine() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var calls = new AtomicInteger();
        try (var worker = worker(events, (a, c, p, s) -> {
            calls.incrementAndGet();
            return result();
        }, () -> {
        }, new AtomicLong())) {
            var valid = start(worker, events);
            var a = valid.assignment();
            var other = new WorkloadCapabilityDTO("another-workload", 1, "text");
            worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, a.identity(),
                    new ExecutionAssignmentDTO(a.identity(), other, a.deadline(), a.imageDigest(), a.payload())));
            assertThat(take(events, WorkerEventType.ERROR).identity()).isEqualTo(a.identity());
            worker.heartbeat();
            var heartbeat = take(events, WorkerEventType.HEARTBEAT);
            assertThat(heartbeat.capability()).isEqualTo(CAPABILITY);
            assertThat(heartbeat.capacity().executions()).isEmpty();
            assertThat(calls).hasValue(0);
        }
    }

    @Test
    void rejectedStartRetriesFailedDeliveryWithoutOccupyingASlot() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            calls.incrementAndGet();
            return result();
        };
        try (var worker = new WorkerSupervisorService(settings(), event -> {
            if (event.type() == WorkerEventType.ERROR && failures.getAndIncrement() == 0) {
                throw new IllegalStateException("publisher offline");
            }
            events.add(event);
        }, () -> engine, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            WorkerCommandDTO valid = start(worker, events);
            var id = new ExecutionIdentityDTO("rejected", "resource-2", UUID.randomUUID(), valid.identity().workerId(), valid.identity().workerIncarnation(), 1);
            var a = valid.assignment();
            var rejected = new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, id,
                    new ExecutionAssignmentDTO(id, a.capability(), a.deadline(), IMAGE, a.payload()));
            worker.accept(rejected);
            org.awaitility.Awaitility.await().untilAsserted(() -> {
                worker.heartbeat();
                assertThat(events).anyMatch(event -> event.type() == WorkerEventType.ERROR);
            });
            assertThat(take(events, WorkerEventType.ERROR).identity()).isEqualTo(id);
            worker.heartbeat();
            assertThat(take(events, WorkerEventType.HEARTBEAT).capacity().executions()).isEmpty();
            worker.accept(rejected);
            assertThat(calls).hasValue(0);
            assertThat(failures).hasValue(2);
        }
    }

    @Test
    void fourSlotsOverlapAndCancellationOnlyCleansItsOwnExecution() throws Exception {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var entered = new CountDownLatch(4);
        var releases = new java.util.concurrent.ConcurrentHashMap<UUID, CountDownLatch>();
        var releaseTerminalPublication = new CountDownLatch(1);
        var blockedCleanup = new java.util.concurrent.atomic.AtomicReference<UUID>();
        var cleanupEntered = new CountDownLatch(1);
        var releaseCleanup = new CountDownLatch(1);
        var cleaned = new java.util.concurrent.CopyOnWriteArrayList<UUID>();
        var settings = new WorkerSettings("worker-1", IMAGE, "runc", 128 * 1024 * 1024, 100_000, 32, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5), 4,
                "text-analysis", "text");
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            entered.countDown();
            await(releases.get(assignment.identity().executionId()));
            return result();
        };
        try (var worker = new WorkerSupervisorService(settings, event -> {
            events.add(event);
            if (event.type() == WorkerEventType.CANCELLED && event.identity().slot() == 0) {
                await(releaseTerminalPublication);
            }
        }, () -> engine, identity -> {
            if (identity.executionId().equals(blockedCleanup.get())) {
                cleanupEntered.countDown();
                await(releaseCleanup);
            }
            cleaned.add(identity.executionId());
            releases.get(identity.executionId()).countDown();
        }, () -> IMAGE, System::nanoTime)) {
            worker.prepare();
            worker.heartbeat();
            var heartbeat = take(events, WorkerEventType.HEARTBEAT);
            var commands = new java.util.ArrayList<WorkerCommandDTO>();
            var original = start(worker, events).assignment();
            try {
                for (int slot = 0; slot < 4; slot++) {
                    var id = new ExecutionIdentityDTO("parallel-" + slot, "resource-" + slot, UUID.randomUUID(), settings.id(), heartbeat.incarnation(), slot);
                    var assignment = new ExecutionAssignmentDTO(id, original.capability(), original.deadline(), IMAGE, original.payload());
                    releases.put(id.executionId(), new CountDownLatch(1));
                    var command = new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, id, assignment);
                    commands.add(command);
                    worker.accept(command);
                }
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                worker.heartbeat();
                var full = take(events, WorkerEventType.HEARTBEAT);
                assertThat(full.ready()).isFalse();
                assertThat(full.identity()).isNull();
                assertThat(full.capacity().slots()).isEqualTo(4);
                assertThat(full.capacity().executions()).hasSize(4);
                var first = commands.getFirst().identity();
                var cancellation = java.util.concurrent.CompletableFuture
                        .runAsync(() -> worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, first, null)));
                assertThat(take(events, WorkerEventType.CANCELLED).identity()).isEqualTo(first);
                assertThat(cleaned).containsOnly(first.executionId());
                // Receiving a terminal is not its publication acknowledgement: the slot stays occupied until the publisher returns.
                worker.heartbeat();
                var publishing = take(events, WorkerEventType.HEARTBEAT);
                assertThat(publishing.ready()).isFalse();
                assertThat(publishing.capacity().executions()).hasSize(4).contains(first);
                releaseTerminalPublication.countDown();
                cancellation.get(5, TimeUnit.SECONDS);
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    worker.heartbeat();
                    var remaining = take(events, WorkerEventType.HEARTBEAT);
                    assertThat(remaining.ready()).isTrue();
                    assertThat(remaining.capacity().executions()).hasSize(3).doesNotContain(first);
                });
                blockedCleanup.set(commands.get(1).identity().executionId());
                worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, commands.get(1).identity(), null));
                assertThat(cleanupEntered.await(5, TimeUnit.SECONDS)).isTrue();
                worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, commands.get(2).identity(), null));
                assertThat(take(events, WorkerEventType.CANCELLED).identity()).isEqualTo(commands.get(2).identity());
                assertThat(releaseCleanup.getCount()).isEqualTo(1);
            }
            finally {
                releaseTerminalPublication.countDown();
                releaseCleanup.countDown();
                releases.values().forEach(CountDownLatch::countDown);
            }
        }
    }

    @Test
    void failedCheckpointDeliveryDoesNotHoldAnotherExecutionsTerminal() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var checkpointRejected = new CountDownLatch(1);
        var settings = new WorkerSettings("worker-1", IMAGE, "runc", 128 * 1024 * 1024, 100_000, 32, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5), 2,
                "text-analysis", "text");
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            if (assignment.identity().slot() == 0) {
                checkpoint.accept(result());
            }
            return result();
        };
        try (var worker = new WorkerSupervisorService(settings, event -> {
            if (event.type() == WorkerEventType.CHECKPOINT) {
                checkpointRejected.countDown();
                throw new IllegalStateException("Checkpoint rejected by broker");
            }
            events.add(event);
        }, () -> engine, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            var first = start(worker, events);
            worker.accept(first);
            assertThat(checkpointRejected.await(5, TimeUnit.SECONDS)).isTrue();
            var a = first.assignment();
            var second = new ExecutionIdentityDTO("second", "resource-2", UUID.randomUUID(), settings.id(), first.identity().workerIncarnation(), 1);
            worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, second,
                    new ExecutionAssignmentDTO(second, a.capability(), a.deadline(), IMAGE, a.payload())));

            assertThat(take(events, WorkerEventType.FINISHED).identity()).isEqualTo(second);
            // Receiving the terminal event precedes the publisher returning and its pending entry being removed.
            // Observe the actual capacity transition, not the scheduling gap between those two operations.
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                worker.heartbeat();
                var heartbeat = take(events, WorkerEventType.HEARTBEAT);
                assertThat(heartbeat.capacity().executions()).containsExactly(first.identity());
                assertThat(heartbeat.ready()).isTrue();
            });
        }
    }

    @Test
    void slowCheckpointPublicationDoesNotBlockCancellationOrHeartbeat() throws Exception {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var publishing = new CountDownLatch(1);
        var releasePublisher = new CountDownLatch(1);
        var cleaned = new CountDownLatch(1);
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            checkpoint.accept(result());
            return result();
        };
        try (var worker = new WorkerSupervisorService(settings(), event -> {
            if (event.type() == WorkerEventType.CHECKPOINT) {
                publishing.countDown();
                await(releasePublisher);
            }
            events.add(event);
        }, () -> engine, cleaned::countDown, () -> IMAGE, System::nanoTime); var commands = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            try {
                var start = start(worker, events);
                worker.accept(start);
                assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
                commands.submit(() -> {
                    worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, start.identity(), null));
                    worker.heartbeat();
                }).get(2, TimeUnit.SECONDS);
                assertThat(cleaned.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(take(events, WorkerEventType.HEARTBEAT).capacity().executions()).containsExactly(start.identity());
            }
            finally {
                releasePublisher.countDown();
            }
            take(events, WorkerEventType.CANCELLED);
        }
    }

    @Test
    void duplicateStartCannotExecuteTwiceEvenAfterTerminalDelivery() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        AtomicInteger calls = new AtomicInteger();
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            calls.incrementAndGet();
            return result();
        };
        try (var worker = worker(events, engine, () -> {
        }, new AtomicLong())) {
            WorkerCommandDTO command = start(worker, events);
            worker.accept(command);
            worker.accept(command);
            assertThat(take(events, WorkerEventType.FINISHED).ready()).isTrue();
            worker.accept(command);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void cancellationCanArriveWhileGenerationIsBlocked() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelledSandbox = new CountDownLatch(1);
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            entered.countDown();
            await(cancelledSandbox);
            assertThat(cancelled.getAsBoolean()).isTrue();
            return result();
        };
        try (var worker = worker(events, engine, cancelledSandbox::countDown, new AtomicLong())) {
            WorkerCommandDTO command = start(worker, events);
            worker.accept(command);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, command.identity(), null));
            take(events, WorkerEventType.CANCELLED);
        }
    }

    @Test
    void coordinatorRenewalsNotBrokerConnectivityKeepExecutionAlive() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelledSandbox = new CountDownLatch(1);
        AtomicLong nanos = new AtomicLong();
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            entered.countDown();
            await(cancelledSandbox);
            assertThat(cancelled.getAsBoolean()).isTrue();
            return result();
        };
        try (var worker = worker(events, engine, cancelledSandbox::countDown, nanos)) {
            WorkerCommandDTO command = start(worker, events);
            worker.accept(command);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            nanos.set(Duration.ofSeconds(30).toNanos());
            worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.RENEW, command.identity(), null));
            nanos.set(Duration.ofSeconds(60).toNanos());
            worker.heartbeat();
            assertThat(cancelledSandbox.getCount()).isEqualTo(1);
            nanos.set(Duration.ofSeconds(80).toNanos());
            worker.heartbeat();
            take(events, WorkerEventType.CANCELLED);
        }
    }

    @Test
    void wrongIncarnationCannotStartOrCancelWork() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        try (var worker = worker(events, (a, c, p, s) -> result(), () -> {
        }, new AtomicLong())) {
            WorkerCommandDTO command = start(worker, events);
            ExecutionIdentityDTO wrong = new ExecutionIdentityDTO(command.identity().jobId(), "resource-1", command.identity().executionId(), "worker-1", UUID.randomUUID(), 0);
            assertThatIllegalArgumentException().isThrownBy(() -> worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, wrong, null)));
        }
    }

    @Test
    void retriesTheSameTerminalEventWithoutAdvertisingCapacityOrRepeatingGeneration() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var failedDelivery = new LinkedBlockingQueue<WorkerEventDTO>();
        java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            calls.incrementAndGet();
            return result();
        };
        try (var worker = new WorkerSupervisorService(settings(), event -> {
            if (event.type() == WorkerEventType.FINISHED && disconnected.get()) {
                failedDelivery.add(event);
                throw new IllegalStateException("Broker unavailable");
            }
            events.add(event);
        }, () -> engine, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            WorkerCommandDTO command = start(worker, events);
            worker.accept(command);
            WorkerEventDTO failed = failedDelivery.poll(5, TimeUnit.SECONDS);
            assertThat(failed).isNotNull();
            worker.heartbeat();
            assertThat(take(events, WorkerEventType.HEARTBEAT).ready()).isFalse();
            disconnected.set(false);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                worker.heartbeat();
                assertThat(events).anyMatch(event -> event.type() == WorkerEventType.FINISHED);
            });
            WorkerEventDTO retried = take(events, WorkerEventType.FINISHED);
            assertThat(retried).isEqualTo(failed);
            assertThat(take(events, WorkerEventType.HEARTBEAT).ready()).isTrue();
            worker.accept(command);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void rejectionDuringPublicationIsDeliveredWithoutAnotherCommandOrHeartbeat() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var publishing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var errors = new AtomicInteger();
        try (var worker = new WorkerSupervisorService(settings(), event -> {
            if (event.type() == WorkerEventType.ERROR && errors.incrementAndGet() == 1) {
                publishing.countDown();
                await(release);
            }
            events.add(event);
        }, () -> null, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            worker.heartbeat();
            var heartbeat = take(events, WorkerEventType.HEARTBEAT);
            var first = new ExecutionIdentityDTO("first", "resource-1", UUID.randomUUID(), heartbeat.workerId(), heartbeat.incarnation(), 0);
            var second = new ExecutionIdentityDTO("second", "resource-2", UUID.randomUUID(), heartbeat.workerId(), heartbeat.incarnation(), 0);
            worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, first,
                    new ExecutionAssignmentDTO(first, CAPABILITY, Instant.now().plusSeconds(300), IMAGE, "input")));
            assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
            worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, second,
                    new ExecutionAssignmentDTO(second, CAPABILITY, Instant.now().plusSeconds(300), IMAGE, "input")));
            release.countDown();
            assertThat(take(events, WorkerEventType.ERROR).identity()).isEqualTo(first);
            assertThat(take(events, WorkerEventType.ERROR).identity()).isEqualTo(second);
        }
        finally {
            release.countDown();
        }
    }

    @Test
    void rejectedAssignmentCannotStartAfterCapacityBecomesAvailable() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            return result();
        };
        try (var worker = worker(events, engine, release::countDown, new AtomicLong())) {
            WorkerCommandDTO first = start(worker, events);
            var identity = new ExecutionIdentityDTO(UUID.randomUUID().toString(), "resource-2", UUID.randomUUID(), first.identity().workerId(),
                    first.identity().workerIncarnation(), 0);
            var assignment = first.assignment();
            var second = new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, identity,
                    new ExecutionAssignmentDTO(identity, assignment.capability(), assignment.deadline(), IMAGE, assignment.payload()));
            worker.accept(first);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.accept(second);
            assertThat(take(events, WorkerEventType.ERROR).identity()).isEqualTo(identity);
            release.countDown();
            take(events, WorkerEventType.FINISHED);
            worker.accept(second);
            assertThat(calls).hasValue(1);
        }
        finally {
            release.countDown();
        }
    }

    @Test
    void failedCheckpointDeliveryIsRetriedBeforeTerminalDelivery() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        var failures = new LinkedBlockingQueue<WorkerEventDTO>();
        var disconnected = new java.util.concurrent.atomic.AtomicBoolean(true);
        TestWorkload engine = (assignment, cancelled, progress, checkpoint) -> {
            checkpoint.accept(result());
            return result();
        };
        try (var worker = new WorkerSupervisorService(settings(), event -> {
            if (event.type() == WorkerEventType.CHECKPOINT && disconnected.get()) {
                failures.add(event);
                throw new IllegalStateException("Broker unavailable");
            }
            events.add(event);
        }, () -> engine, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            worker.accept(start(worker, events));
            WorkerEventDTO checkpoint = failures.poll(5, TimeUnit.SECONDS);
            assertThat(checkpoint).isNotNull();
            // The second failed delivery is made after generation returns, before terminal delivery.
            assertThat(failures.poll(5, TimeUnit.SECONDS)).isEqualTo(checkpoint);
            worker.heartbeat();
            assertThat(take(events, WorkerEventType.HEARTBEAT).ready()).isFalse();
            disconnected.set(false);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                worker.heartbeat();
                assertThat(events).anyMatch(event -> event.type() == WorkerEventType.FINISHED);
            });
            assertThat(events.stream().map(WorkerEventDTO::type).filter(type -> type == WorkerEventType.CHECKPOINT || type == WorkerEventType.FINISHED))
                    .containsExactly(WorkerEventType.CHECKPOINT, WorkerEventType.FINISHED);
            assertThat(take(events, WorkerEventType.CHECKPOINT)).isEqualTo(checkpoint);
            take(events, WorkerEventType.FINISHED);
        }
    }

    @Test
    void cancelDuringFinalCleanupDoesNotScheduleCleanupAgainstTheNextJob() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        AtomicInteger cleanups = new AtomicInteger();
        try (var worker = worker(events, (a, c, p, s) -> result(), () -> {
            cleanups.incrementAndGet();
            cleanupEntered.countDown();
            await(releaseCleanup);
        }, new AtomicLong())) {
            WorkerCommandDTO command = start(worker, events);
            worker.accept(command);
            assertThat(cleanupEntered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.heartbeat();
            assertThat(take(events, WorkerEventType.HEARTBEAT).ready()).isFalse();
            worker.accept(new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.CANCEL, command.identity(), null));
            releaseCleanup.countDown();
            assertThat(take(events, WorkerEventType.CANCELLED).ready()).isTrue();
        }
        finally {
            releaseCleanup.countDown();
        }
        assertThat(cleanups).hasValue(1);
    }

    @Test
    void terminalDoesNotAdvertiseCapacityAfterCleanupFailure() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        try (var worker = worker(events, (a, c, p, s) -> result(), () -> {
            throw new IllegalStateException("Cleanup failed");
        }, new AtomicLong())) {
            worker.accept(start(worker, events));
            assertThat(take(events, WorkerEventType.FINISHED).ready()).isFalse();
        }
    }

    @Test
    void missingPolicyDoesNotAdvertiseUsableCapacity() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEventDTO>();
        try (var worker = new WorkerSupervisorService(settings(), events::add, () -> null, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            worker.prepare();
            worker.heartbeat();
            assertThat(take(events, WorkerEventType.HEARTBEAT).ready()).isFalse();
        }
    }

    private static WorkerSupervisorService worker(LinkedBlockingQueue<WorkerEventDTO> events, TestWorkload engine, Runnable cleanup, AtomicLong nanos) {
        return new WorkerSupervisorService(settings(), events::add, () -> engine, cleanup, () -> IMAGE, nanos::get);
    }

    private static WorkerSettings settings() {
        return new WorkerSettings("worker-1", IMAGE, "runc", 128 * 1024 * 1024, 100_000, 32, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5), 1,
                "text-analysis", "text");
    }

    private static WorkerCommandDTO start(WorkerSupervisorService worker, LinkedBlockingQueue<WorkerEventDTO> events) throws InterruptedException {
        worker.prepare();
        worker.heartbeat();
        WorkerEventDTO heartbeat = take(events, WorkerEventType.HEARTBEAT);
        assertThat(heartbeat.ready()).isTrue();
        var identity = new ExecutionIdentityDTO(UUID.randomUUID().toString(), "resource-1", UUID.randomUUID(), heartbeat.workerId(), heartbeat.incarnation(), 0);
        var assignment = new ExecutionAssignmentDTO(identity, CAPABILITY, Instant.now().plusSeconds(300), IMAGE, "input");
        return new WorkerCommandDTO(WorkerCommandDTO.PROTOCOL_VERSION, WorkerCommandType.START, identity, assignment);
    }

    private static String result() {
        return "completed";
    }

    private static final WorkloadCapabilityDTO CAPABILITY = new WorkloadCapabilityDTO("text-analysis", 1, "text");

    @FunctionalInterface
    private interface TestWorkload extends WorkloadApi {

        @Override
        default WorkloadCapabilityDTO capability() {
            return CAPABILITY;
        }
    }

    private static WorkerEventDTO take(LinkedBlockingQueue<WorkerEventDTO> events, WorkerEventType type) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            WorkerEventDTO event = events.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            assertThat(event).as("Expected event %s", type).isNotNull();
            if (event.type() == type) {
                return event;
            }
        }
        throw new AssertionError("Missing " + type);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release generation");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

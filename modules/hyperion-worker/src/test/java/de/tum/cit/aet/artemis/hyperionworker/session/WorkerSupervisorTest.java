package de.tum.cit.aet.artemis.hyperionworker.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.ExerciseBrief;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationParameters;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperionworker.config.WorkerSettings;

class WorkerSupervisorTest {

    private static final String IMAGE = "sha256:" + "a".repeat(64);

    @Test
    void fourSlotsOverlapAndCancellationOnlyCleansItsOwnExecution() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        var entered = new CountDownLatch(4);
        var releases = new java.util.concurrent.ConcurrentHashMap<UUID, CountDownLatch>();
        var blockedCleanup = new java.util.concurrent.atomic.AtomicReference<UUID>();
        var cleanupEntered = new CountDownLatch(1);
        var releaseCleanup = new CountDownLatch(1);
        var cleaned = new java.util.concurrent.CopyOnWriteArrayList<UUID>();
        var settings = new WorkerSettings("worker-1", IMAGE, "runc", 128 * 1024 * 1024, 100_000, 32, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5), 4);
        GenerationEngine engine = (assignment, cancelled, progress, checkpoint) -> {
            entered.countDown();
            await(releases.get(assignment.identity().executionId()));
            return result();
        };
        try (var worker = new WorkerSupervisor(settings, events::add, () -> engine, identity -> {
            if (identity.executionId().equals(blockedCleanup.get())) {
                cleanupEntered.countDown();
                await(releaseCleanup);
            }
            cleaned.add(identity.executionId());
            releases.get(identity.executionId()).countDown();
        }, () -> IMAGE, System::nanoTime)) {
            worker.prepare();
            worker.heartbeat();
            var heartbeat = take(events, WorkerEvent.Type.HEARTBEAT);
            var commands = new java.util.ArrayList<WorkerCommand>();
            var original = start(worker, events).assignment();
            try {
                for (int slot = 0; slot < 4; slot++) {
                    var id = new ExecutionIdentity("parallel-" + slot, 42 + slot, UUID.randomUUID(), settings.id(), heartbeat.incarnation(), slot);
                    var assignment = new GenerationAssignment(id, original.brief(), original.parameters(), original.seed(), original.authoringDeadline(), IMAGE, original.gradingContext());
                    releases.put(id.executionId(), new CountDownLatch(1));
                    var command = new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.START, id, assignment);
                    commands.add(command);
                    worker.accept(command);
                }
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                worker.heartbeat();
                var full = take(events, WorkerEvent.Type.HEARTBEAT);
                assertThat(full.ready()).isFalse();
                assertThat(full.capacity().slots()).isEqualTo(4);
                assertThat(full.capacity().executions()).hasSize(4);
                var first = commands.getFirst().identity();
                worker.accept(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, first, null));
                assertThat(take(events, WorkerEvent.Type.CANCELLED).identity()).isEqualTo(first);
                assertThat(cleaned).containsOnly(first.executionId());
                worker.heartbeat();
                var remaining = take(events, WorkerEvent.Type.HEARTBEAT);
                assertThat(remaining.ready()).isTrue();
                assertThat(remaining.capacity().executions()).hasSize(3).doesNotContain(first);
                blockedCleanup.set(commands.get(1).identity().executionId());
                worker.accept(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, commands.get(1).identity(), null));
                assertThat(cleanupEntered.await(5, TimeUnit.SECONDS)).isTrue();
                worker.accept(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, commands.get(2).identity(), null));
                assertThat(take(events, WorkerEvent.Type.CANCELLED).identity()).isEqualTo(commands.get(2).identity());
                assertThat(releaseCleanup.getCount()).isEqualTo(1);
            }
            finally {
                releaseCleanup.countDown();
                releases.values().forEach(CountDownLatch::countDown);
            }
        }
    }

    @Test
    void duplicateStartCannotExecuteTwiceEvenAfterTerminalDelivery() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        AtomicInteger calls = new AtomicInteger();
        GenerationEngine engine = (assignment, cancelled, progress, checkpoint) -> {
            calls.incrementAndGet();
            return result();
        };
        try (var worker = worker(events, engine, () -> {
        }, new AtomicLong())) {
            WorkerCommand command = start(worker, events);
            worker.accept(command);
            worker.accept(command);
            assertThat(take(events, WorkerEvent.Type.FINISHED).ready()).isTrue();
            worker.accept(command);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void cancellationCanArriveWhileGenerationIsBlocked() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelledSandbox = new CountDownLatch(1);
        GenerationEngine engine = (assignment, cancelled, progress, checkpoint) -> {
            entered.countDown();
            await(cancelledSandbox);
            assertThat(cancelled.getAsBoolean()).isTrue();
            return result();
        };
        try (var worker = worker(events, engine, cancelledSandbox::countDown, new AtomicLong())) {
            WorkerCommand command = start(worker, events);
            worker.accept(command);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.accept(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, command.identity(), null));
            take(events, WorkerEvent.Type.CANCELLED);
        }
    }

    @Test
    void coordinatorRenewalsNotBrokerConnectivityKeepExecutionAlive() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch cancelledSandbox = new CountDownLatch(1);
        AtomicLong nanos = new AtomicLong();
        GenerationEngine engine = (assignment, cancelled, progress, checkpoint) -> {
            entered.countDown();
            await(cancelledSandbox);
            assertThat(cancelled.getAsBoolean()).isTrue();
            return result();
        };
        try (var worker = worker(events, engine, cancelledSandbox::countDown, nanos)) {
            WorkerCommand command = start(worker, events);
            worker.accept(command);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            nanos.set(Duration.ofSeconds(30).toNanos());
            worker.accept(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.RENEW, command.identity(), null));
            nanos.set(Duration.ofSeconds(60).toNanos());
            worker.heartbeat();
            assertThat(cancelledSandbox.getCount()).isEqualTo(1);
            nanos.set(Duration.ofSeconds(80).toNanos());
            worker.heartbeat();
            take(events, WorkerEvent.Type.CANCELLED);
        }
    }

    @Test
    void wrongIncarnationCannotStartOrCancelWork() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        try (var worker = worker(events, (a, c, p, s) -> result(), () -> {
        }, new AtomicLong())) {
            WorkerCommand command = start(worker, events);
            ExecutionIdentity wrong = new ExecutionIdentity(command.identity().jobId(), 1, command.identity().executionId(), "worker-1", UUID.randomUUID());
            assertThatIllegalArgumentException().isThrownBy(() -> worker.accept(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, wrong, null)));
        }
    }

    @Test
    void retriesTheSameTerminalEventWithoutAdvertisingCapacityOrRepeatingGeneration() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        var failedDelivery = new LinkedBlockingQueue<WorkerEvent>();
        java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        GenerationEngine engine = (assignment, cancelled, progress, checkpoint) -> {
            calls.incrementAndGet();
            return result();
        };
        try (var worker = new WorkerSupervisor(settings(), event -> {
            if (event.type() == WorkerEvent.Type.FINISHED && disconnected.get()) {
                failedDelivery.add(event);
                throw new IllegalStateException("Broker unavailable");
            }
            events.add(event);
        }, () -> engine, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            WorkerCommand command = start(worker, events);
            worker.accept(command);
            WorkerEvent failed = failedDelivery.poll(5, TimeUnit.SECONDS);
            assertThat(failed).isNotNull();
            worker.heartbeat();
            assertThat(take(events, WorkerEvent.Type.HEARTBEAT).ready()).isFalse();
            disconnected.set(false);
            worker.heartbeat();
            WorkerEvent retried = take(events, WorkerEvent.Type.FINISHED);
            assertThat(retried).isEqualTo(failed);
            assertThat(take(events, WorkerEvent.Type.HEARTBEAT).ready()).isTrue();
            worker.accept(command);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void rejectedAssignmentCannotStartAfterCapacityBecomesAvailable() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        GenerationEngine engine = (assignment, cancelled, progress, checkpoint) -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            return result();
        };
        try (var worker = worker(events, engine, release::countDown, new AtomicLong())) {
            WorkerCommand first = start(worker, events);
            var identity = new ExecutionIdentity(UUID.randomUUID().toString(), 2, UUID.randomUUID(), first.identity().workerId(), first.identity().workerIncarnation());
            var assignment = first.assignment();
            var second = new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.START, identity, new GenerationAssignment(identity, assignment.brief(), assignment.parameters(), assignment.seed(),
                    assignment.authoringDeadline(), IMAGE, assignment.gradingContext()));
            worker.accept(first);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.accept(second);
            assertThat(take(events, WorkerEvent.Type.ERROR).identity()).isEqualTo(identity);
            release.countDown();
            take(events, WorkerEvent.Type.FINISHED);
            worker.accept(second);
            assertThat(calls).hasValue(1);
        }
        finally {
            release.countDown();
        }
    }

    @Test
    void failedCheckpointDeliveryIsRetriedBeforeTerminalDelivery() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        var failures = new LinkedBlockingQueue<WorkerEvent>();
        var disconnected = new java.util.concurrent.atomic.AtomicBoolean(true);
        GenerationEngine engine = (assignment, cancelled, progress, checkpoint) -> {
            checkpoint.accept(result());
            return result();
        };
        try (var worker = new WorkerSupervisor(settings(), event -> {
            if (event.type() == WorkerEvent.Type.CHECKPOINT && disconnected.get()) {
                failures.add(event);
                throw new IllegalStateException("Broker unavailable");
            }
            events.add(event);
        }, () -> engine, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            worker.accept(start(worker, events));
            WorkerEvent checkpoint = failures.poll(5, TimeUnit.SECONDS);
            assertThat(checkpoint).isNotNull();
            // The second failed delivery is made after generation returns, before terminal delivery.
            assertThat(failures.poll(5, TimeUnit.SECONDS)).isEqualTo(checkpoint);
            worker.heartbeat();
            assertThat(take(events, WorkerEvent.Type.HEARTBEAT).ready()).isFalse();
            disconnected.set(false);
            worker.heartbeat();
            assertThat(take(events, WorkerEvent.Type.CHECKPOINT)).isEqualTo(checkpoint);
            take(events, WorkerEvent.Type.FINISHED);
        }
    }

    @Test
    void cancelDuringFinalCleanupDoesNotScheduleCleanupAgainstTheNextJob() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        AtomicInteger cleanups = new AtomicInteger();
        try (var worker = worker(events, (a, c, p, s) -> result(), () -> {
            cleanups.incrementAndGet();
            cleanupEntered.countDown();
            await(releaseCleanup);
        }, new AtomicLong())) {
            WorkerCommand command = start(worker, events);
            worker.accept(command);
            assertThat(cleanupEntered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.heartbeat();
            assertThat(take(events, WorkerEvent.Type.HEARTBEAT).ready()).isFalse();
            worker.accept(new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.CANCEL, command.identity(), null));
            releaseCleanup.countDown();
            assertThat(take(events, WorkerEvent.Type.CANCELLED).ready()).isTrue();
        }
        finally {
            releaseCleanup.countDown();
        }
        assertThat(cleanups).hasValue(1);
    }

    @Test
    void terminalDoesNotAdvertiseCapacityAfterCleanupFailure() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        try (var worker = worker(events, (a, c, p, s) -> result(), () -> {
            throw new IllegalStateException("Cleanup failed");
        }, new AtomicLong())) {
            worker.accept(start(worker, events));
            assertThat(take(events, WorkerEvent.Type.FINISHED).ready()).isFalse();
        }
    }

    @Test
    void missingPolicyDoesNotAdvertiseUsableCapacity() throws InterruptedException {
        var events = new LinkedBlockingQueue<WorkerEvent>();
        try (var worker = new WorkerSupervisor(settings(), events::add, () -> null, () -> {
        }, () -> IMAGE, System::nanoTime)) {
            worker.prepare();
            worker.heartbeat();
            assertThat(take(events, WorkerEvent.Type.HEARTBEAT).ready()).isFalse();
        }
    }

    private static WorkerSupervisor worker(LinkedBlockingQueue<WorkerEvent> events, GenerationEngine engine, Runnable cleanup, AtomicLong nanos) {
        return new WorkerSupervisor(settings(), events::add, () -> engine, cleanup, () -> IMAGE, nanos::get);
    }

    private static WorkerSettings settings() {
        return new WorkerSettings("worker-1", IMAGE, "runc", 128 * 1024 * 1024, 100_000, 32, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5));
    }

    private static WorkerCommand start(WorkerSupervisor worker, LinkedBlockingQueue<WorkerEvent> events) throws InterruptedException {
        worker.prepare();
        worker.heartbeat();
        WorkerEvent heartbeat = take(events, WorkerEvent.Type.HEARTBEAT);
        assertThat(heartbeat.ready()).isTrue();
        var identity = new ExecutionIdentity(UUID.randomUUID().toString(), 1, UUID.randomUUID(), heartbeat.workerId(), heartbeat.incarnation());
        var brief = new ExerciseBrief("Stack", "stack", "de.example", null, "Create a stack", ExerciseBrief.Mode.GENERATE);
        var parameters = new GenerationParameters("standard", 10, 100_000, Duration.ofMinutes(5), 128_000, null, null, null, null, null, true, "CONTINUOUS");
        var assignment = new GenerationAssignment(identity, brief, parameters, new WorkspaceSnapshot(List.of()), Instant.now().plusSeconds(300), IMAGE,
                new de.tum.cit.aet.artemis.hyperion.protocol.GradingContext(false, java.util.Set.of()));
        return new WorkerCommand(WorkerCommand.PROTOCOL_VERSION, WorkerCommand.Type.START, identity, assignment);
    }

    private static GenerationOutput result() {
        return new GenerationOutput(new WorkspaceSnapshot(List.of()), new VerificationResult(false, false, false, 0, List.of("not verified")), null,
                new SpecFidelityReport(List.of()), "RUN_FAILED", null, GenerationOutput.AccountingState.INCOMPLETE, "standard");
    }

    private static WorkerEvent take(LinkedBlockingQueue<WorkerEvent> events, WorkerEvent.Type type) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            WorkerEvent event = events.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
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

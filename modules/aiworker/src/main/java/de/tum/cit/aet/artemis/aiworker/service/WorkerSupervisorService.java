package de.tum.cit.aet.artemis.aiworker.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.aiworker.api.ExecutionObserver;
import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.aiworker.api.WorkloadApi;
import de.tum.cit.aet.artemis.aiworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerCommandType;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCapacityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;

/** Bounded, independently cancellable executions per worker. The message listener never waits for a model/tool call to complete. */
@Service
public class WorkerSupervisorService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkerSupervisorService.class);

    private static final int MAX_RECENT_ASSIGNMENTS = 1_024;

    private final WorkerSettings settings;

    private final WorkerEventPublisher publisher;

    private final Supplier<@Nullable WorkloadApi> engine;

    private final Consumer<ExecutionIdentityDTO> cancelSandboxes;

    private final Supplier<String> prepareSandbox;

    private final ExecutorService executor;

    private final ExecutorService cancellationExecutor;

    private final ExecutorService deliveryExecutor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("aiworker-delivery").factory());

    private boolean deliveryScheduled;

    private boolean deliveryRequested;

    private final LongSupplier nanoTime;

    private final UUID incarnation = UUID.randomUUID();

    private final AtomicLong sequence = new AtomicLong();

    private final Map<UUID, Instant> admitted = new HashMap<>();

    private final Map<UUID, ActiveExecution> active = new HashMap<>();

    private final Map<UUID, WorkerEventDTO> pendingTerminals = new java.util.LinkedHashMap<>();

    private final Map<UUID, WorkerEventDTO> pendingCheckpoints = new java.util.LinkedHashMap<>();

    private final Map<UUID, WorkerEventDTO> pendingRejections = new java.util.LinkedHashMap<>();

    private final java.util.Deque<WorkerEventDTO> pendingAccounting = new java.util.ArrayDeque<>();

    private final java.util.Set<UUID> publishingPending = new java.util.HashSet<>();

    private boolean draining;

    private boolean prepared;

    private String imageDigest = "sha256:" + "0".repeat(64);

    private static final class ActiveExecution {

        final ExecutionAssignmentDTO assignment;

        final WorkloadApi engine;

        final AtomicBoolean cancelled = new AtomicBoolean();

        final AtomicBoolean finishRequested = new AtomicBoolean();

        volatile long renewedAt;

        @Nullable
        volatile CompletableFuture<Void> cleanup;

        boolean finishing;

        ActiveExecution(ExecutionAssignmentDTO assignment, WorkloadApi engine, long renewedAt) {
            this.assignment = assignment;
            this.engine = engine;
            this.renewedAt = renewedAt;
        }
    }

    @Autowired
    public WorkerSupervisorService(WorkerSettings settings, WorkerEventPublisher publisher, ObjectProvider<WorkloadApi> engine, SandboxApi sandbox) {
        this(settings, publisher, engine::getIfAvailable, identity -> sandbox.destroyExecution(identity.executionId()), sandbox::prepare, System::nanoTime);
    }

    WorkerSupervisorService(WorkerSettings settings, WorkerEventPublisher publisher, Supplier<@Nullable WorkloadApi> engine, Runnable cancelSandboxes,
            Supplier<String> prepareSandbox, LongSupplier nanoTime) {
        this(settings, publisher, engine, identity -> cancelSandboxes.run(), prepareSandbox, nanoTime);
    }

    WorkerSupervisorService(WorkerSettings settings, WorkerEventPublisher publisher, Supplier<@Nullable WorkloadApi> engine, Consumer<ExecutionIdentityDTO> cancelSandboxes,
            Supplier<String> prepareSandbox, LongSupplier nanoTime) {
        this.cancellationExecutor = Executors.newFixedThreadPool(settings.maxConcurrentExecutions(), Thread.ofPlatform().name("aiworker-cancel-", 0).factory());
        this.executor = Executors.newFixedThreadPool(settings.maxConcurrentExecutions(), Thread.ofPlatform().name("aiworker-execution-", 0).factory());
        this.nanoTime = nanoTime;
        this.settings = settings;
        this.publisher = publisher;
        this.engine = engine;
        this.cancelSandboxes = cancelSandboxes;
        this.prepareSandbox = prepareSandbox;
    }

    /** Reconciles only this worker's old containers before it advertises capacity. */
    @PostConstruct
    public synchronized void prepare() {
        imageDigest = prepareSandbox.get();
        prepared = true;
    }

    /**
     * Admits commands for this incarnation; redelivery cannot launch the same job twice.
     *
     * @param command the validated job-level command
     */
    public synchronized void accept(WorkerCommandDTO command) {
        acceptCommand(command);
        if (!pendingRejections.isEmpty()) {
            deliveryRequested = true;
            if (!deliveryScheduled) {
                deliveryScheduled = true;
                deliveryExecutor.submit(this::deliverRejections);
            }
        }
    }

    private void deliverRejections() {
        while (true) {
            synchronized (this) {
                if (!deliveryRequested) {
                    deliveryScheduled = false;
                    return;
                }
                deliveryRequested = false;
            }
            // Publish outside the admission lock. Requests received during this batch require another pass.
            flushPending(pendingRejections);
        }
    }

    private void acceptCommand(WorkerCommandDTO command) {
        ExecutionIdentityDTO identity = command.identity();
        if (!settings.id().equals(identity.workerId()) || !incarnation.equals(identity.workerIncarnation())) {
            throw new IllegalArgumentException("Command targets another worker incarnation");
        }
        if (command.type() != WorkerCommandType.START) {
            ActiveExecution execution = active.get(identity.executionId());
            if (execution != null && execution.assignment.identity().equals(identity)) {
                if (command.type() == WorkerCommandType.CANCEL) {
                    cancel(execution);
                }
                else if (command.type() == WorkerCommandType.FINISH) {
                    execution.finishRequested.set(true);
                }
                else {
                    execution.renewedAt = nanoTime.getAsLong();
                }
            }
            return;
        }
        ExecutionAssignmentDTO assignment = command.assignment();
        admitted.values().removeIf(deadline -> !deadline.isAfter(Instant.now()));
        if (!assignment.deadline().isAfter(Instant.now()) || admitted.containsKey(identity.executionId())) {
            return;
        }
        if (pendingRejections.size() >= MAX_RECENT_ASSIGNMENTS) {
            // Do not acknowledge another START when its rejection cannot be retained. The transacted listener must redeliver it.
            throw new IllegalStateException("Execution rejection delivery backlog is full");
        }
        if (admitted.size() >= MAX_RECENT_ASSIGNMENTS) {
            // Retire this incarnation rather than forget rejection identities and allow a delayed replay.
            draining = true;
        }
        else {
            admitted.put(identity.executionId(), assignment.deadline());
        }
        WorkloadApi policy = engine.get();
        if (!ready() || policy == null || identity.slot() >= settings.maxConcurrentExecutions() || occupiedExecutions().stream().anyMatch(id -> id.slot() == identity.slot())
                || !imageDigest.equals(assignment.imageDigest()) || !policy.capability().equals(assignment.capability())) {
            pendingRejections.put(identity.executionId(), event(WorkerEventType.ERROR, identity, "AI worker is not available for this assignment.", null));
            return;
        }
        ActiveExecution execution = new ActiveExecution(assignment, policy, nanoTime.getAsLong());
        active.put(identity.executionId(), execution);
        executor.submit(() -> execute(execution, policy));
    }

    private record TerminalResult(WorkerEventType type, @Nullable String message, @Nullable String output) {
    }

    private void execute(ActiveExecution execution, WorkloadApi policy) {
        ExecutionIdentityDTO identity = execution.assignment.identity();
        TerminalResult terminal;
        try {
            publishBestEffort(event(WorkerEventType.STARTED, identity, "Starting execution on the isolated worker.", null));
            ExecutionObserver progress = (message, payload, reliable) -> {
                WorkerEventDTO update = event(reliable ? WorkerEventType.ACCOUNTING : WorkerEventType.PROGRESS, identity, bounded(message), payload);
                if (reliable) {
                    retainAccounting(update);
                }
                else {
                    publishBestEffort(update);
                }
            };
            String result = policy.execute(execution.assignment, () -> execution.cancelled.get() || execution.finishRequested.get(), progress,
                    checkpoint -> retainCheckpoint(identity, checkpoint));
            if (result == null || result.isBlank() || result.length() > WorkerEventDTO.MAX_PAYLOAD_LENGTH) {
                throw new IllegalArgumentException("Workload returned invalid terminal output");
            }
            terminal = new TerminalResult(WorkerEventType.FINISHED, null, result);
        }
        catch (RuntimeException failure) {
            log.warn("Worker execution {} stopped ({})", identity.executionId(), failure.getClass().getSimpleName());
            terminal = new TerminalResult(WorkerEventType.ERROR, "Execution failed on the worker.", null);
        }
        finally {
            try {
                CompletableFuture<Void> cleanup;
                synchronized (this) {
                    execution.finishing = true;
                    cleanup = execution.cleanup;
                }
                if (cleanup != null) {
                    cleanup.join();
                }
                cancelSandboxes.accept(identity);
            }
            catch (RuntimeException cleanupFailure) {
                synchronized (this) {
                    prepared = false;
                }
                log.error("Worker {} is not ready after sandbox cleanup failed", settings.id());
            }
        }
        synchronized (this) {
            active.remove(identity.executionId());
            // Retain this slot until cleanup and delivery both finish, without blocking other slots.
            pendingTerminals.put(identity.executionId(), event(execution.cancelled.get() ? WorkerEventType.CANCELLED : terminal.type(), identity,
                    execution.cancelled.get() ? "Execution cancelled." : terminal.message(), execution.cancelled.get() ? null : terminal.output()));
        }
        flushTerminal();
    }

    /** Retries an undelivered terminal event and expires work only when core stops renewing its exact assignment. */
    @Scheduled(fixedDelayString = "${artemis.aiworker.heartbeat-interval:PT10S}")
    public void heartbeat() {
        synchronized (this) {
            for (ActiveExecution execution : active.values()) {
                if (nanoTime.getAsLong() - execution.renewedAt > settings.connectionGrace().toNanos()) {
                    cancel(execution);
                }
            }
            admitted.values().removeIf(deadline -> !deadline.isAfter(Instant.now()));
        }
        flushTerminal();
        publishBestEffort(event(WorkerEventType.HEARTBEAT, currentIdentity(), null, null));
    }

    private void retainAccounting(WorkerEventDTO update) {
        synchronized (this) {
            if (pendingAccounting.size() >= 4_096) {
                throw new IllegalStateException("Worker accounting delivery backlog is full");
            }
            pendingAccounting.addLast(update);
        }
        flushTerminal();
    }

    private void retainCheckpoint(ExecutionIdentityDTO identity, String checkpoint) {
        if (checkpoint == null || checkpoint.isBlank() || checkpoint.length() > WorkerEventDTO.MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Workload returned invalid checkpoint output");
        }
        synchronized (this) {
            pendingCheckpoints.put(identity.executionId(), event(WorkerEventType.CHECKPOINT, identity, null, checkpoint));
        }
        flushTerminal();
    }

    private void flushTerminal() {
        flushPending(pendingRejections);
        flushAccounting();
        flushPending(pendingCheckpoints);
        flushPending(pendingTerminals);
    }

    private void flushAccounting() {
        List<WorkerEventDTO> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(pendingAccounting);
        }
        for (WorkerEventDTO event : snapshot) {
            UUID executionId = event.identity().executionId();
            synchronized (this) {
                if (publishingPending.contains(executionId)
                        || pendingAccounting.stream().filter(update -> update.identity().executionId().equals(executionId)).findFirst().orElse(null) != event) {
                    continue;
                }
                publishingPending.add(executionId);
            }
            boolean published = false;
            try {
                published = publishBestEffort(event);
            }
            finally {
                synchronized (this) {
                    if (published) {
                        pendingAccounting.remove(event);
                    }
                    publishingPending.remove(executionId);
                }
            }
        }
    }

    private void flushPending(Map<UUID, WorkerEventDTO> pending) {
        List<WorkerEventDTO> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(pending.values());
        }
        for (WorkerEventDTO event : snapshot) {
            UUID executionId = event.identity().executionId();
            synchronized (this) {
                if (pending.get(executionId) != event || publishingPending.contains(executionId)
                        || pendingAccounting.stream().anyMatch(update -> update.identity().executionId().equals(executionId))
                        || pending == pendingTerminals && pendingCheckpoints.containsKey(executionId)) {
                    continue;
                }
                publishingPending.add(executionId);
            }
            boolean published = false;
            try {
                published = publishBestEffort(event);
            }
            finally {
                synchronized (this) {
                    if (published) {
                        pending.remove(executionId, event);
                    }
                    publishingPending.remove(executionId);
                }
            }
        }
    }

    private void cancel(ActiveExecution execution) {
        if (execution.cancelled.compareAndSet(false, true) && !execution.finishing) {
            execution.cleanup = CompletableFuture.runAsync(() -> {
                if (!execution.engine.requestCancel(execution.assignment.identity())) {
                    cancelSandboxes.accept(execution.assignment.identity());
                }
            }, cancellationExecutor);
        }
    }

    private synchronized boolean ready() {
        return prepared && !draining && (active.size() + pendingTerminals.size()) < settings.maxConcurrentExecutions() && admitted.size() < MAX_RECENT_ASSIGNMENTS
                && engine.get() != null && settings.capability().equals(engine.get().capability());
    }

    @Nullable
    private synchronized ExecutionIdentityDTO currentIdentity() {
        return active.size() == 1 ? active.values().iterator().next().assignment.identity() : null;
    }

    private synchronized WorkerEventDTO event(WorkerEventType type, @Nullable ExecutionIdentityDTO identity, @Nullable String message, @Nullable String payload) {
        return new WorkerEventDTO(WorkerCommandDTO.PROTOCOL_VERSION, settings.id(), incarnation, sequence.incrementAndGet(), Instant.now(), type, identity, ready(), imageDigest,
                message, payload, new WorkerCapacityDTO(settings.maxConcurrentExecutions(), occupiedExecutions()), settings.capability());
    }

    private synchronized List<ExecutionIdentityDTO> occupiedExecutions() {
        var identities = new java.util.ArrayList<ExecutionIdentityDTO>();
        active.values().forEach(value -> identities.add(value.assignment.identity()));
        pendingTerminals.values().forEach(value -> identities.add(value.identity()));
        return identities;
    }

    private boolean publishBestEffort(WorkerEventDTO event) {
        try {
            publisher.publish(event);
            return true;
        }
        catch (RuntimeException failure) {
            log.warn("Worker {} could not publish {} ({})", settings.id(), event.type(), failure.getClass().getSimpleName());
            return false;
        }
    }

    private static String bounded(String message) {
        return message.length() <= 8_192 ? message : message.substring(0, 8_192);
    }

    @Override
    @PreDestroy
    public void close() {
        synchronized (this) {
            draining = true;
            active.values().forEach(this::cancel);
        }
        deliveryExecutor.shutdownNow();
        executor.shutdown();
        cancellationExecutor.shutdown();
        try {
            if (!executor.awaitTermination(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                cancellationExecutor.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            executor.shutdownNow();
            cancellationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        flushTerminal();
    }
}

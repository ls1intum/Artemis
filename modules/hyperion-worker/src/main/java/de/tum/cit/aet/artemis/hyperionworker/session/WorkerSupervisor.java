package de.tum.cit.aet.artemis.hyperionworker.session;

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

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationActivity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCapacity;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerCommand;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.GenerationActivityTracker;
import de.tum.cit.aet.artemis.hyperionworker.config.WorkerSettings;
import de.tum.cit.aet.artemis.hyperionworker.messaging.WorkerEventPublisher;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.DockerSandbox;

/** Bounded, independently cancellable executions per worker. The message listener never waits for a model/tool call to complete. */
@Service
public class WorkerSupervisor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkerSupervisor.class);

    private static final int MAX_RECENT_ASSIGNMENTS = 1_024;

    private final WorkerSettings settings;

    private final WorkerEventPublisher publisher;

    private final Supplier<@Nullable GenerationEngine> engine;

    private final Consumer<ExecutionIdentity> cancelSandboxes;

    private final Supplier<String> prepareSandbox;

    private final ExecutorService executor;

    private final ExecutorService cancellationExecutor;

    private final LongSupplier nanoTime;

    private final UUID incarnation = UUID.randomUUID();

    private final AtomicLong sequence = new AtomicLong();

    private final Map<UUID, Instant> admitted = new HashMap<>();

    private final Map<UUID, ActiveExecution> active = new HashMap<>();

    private final Map<UUID, WorkerEvent> pendingTerminals = new java.util.LinkedHashMap<>();

    private final Map<UUID, WorkerEvent> pendingCheckpoints = new java.util.LinkedHashMap<>();

    private final java.util.Deque<WorkerEvent> pendingAccounting = new java.util.ArrayDeque<>();

    private boolean draining;

    private boolean prepared;

    private String imageDigest = "sha256:" + "0".repeat(64);

    private static final class ActiveExecution {

        final GenerationAssignment assignment;

        final AtomicBoolean cancelled = new AtomicBoolean();

        final AtomicBoolean stopAuthoring = new AtomicBoolean();

        volatile long renewedAt;

        @Nullable
        volatile CompletableFuture<Void> cleanup;

        boolean finishing;

        ActiveExecution(GenerationAssignment assignment, long renewedAt) {
            this.assignment = assignment;
            this.renewedAt = renewedAt;
        }
    }

    @Autowired
    public WorkerSupervisor(WorkerSettings settings, WorkerEventPublisher publisher, ObjectProvider<GenerationEngine> engine, DockerSandbox sandbox, DockerClient docker) {
        this(settings, publisher, engine::getIfAvailable, sandbox::destroyExecution, () -> {
            sandbox.removePreviousSessions();
            try (var inspect = docker.inspectImageCmd(settings.image())) {
                return inspect.exec().getId();
            }
        }, System::nanoTime);
    }

    WorkerSupervisor(WorkerSettings settings, WorkerEventPublisher publisher, Supplier<@Nullable GenerationEngine> engine, Runnable cancelSandboxes,
            Supplier<String> prepareSandbox, LongSupplier nanoTime) {
        this(settings, publisher, engine, identity -> cancelSandboxes.run(), prepareSandbox, nanoTime);
    }

    WorkerSupervisor(WorkerSettings settings, WorkerEventPublisher publisher, Supplier<@Nullable GenerationEngine> engine, Consumer<ExecutionIdentity> cancelSandboxes,
            Supplier<String> prepareSandbox, LongSupplier nanoTime) {
        this.cancellationExecutor = Executors.newFixedThreadPool(settings.maxConcurrentGenerations(), Thread.ofPlatform().name("hyperion-cancel-", 0).factory());
        this.executor = Executors.newFixedThreadPool(settings.maxConcurrentGenerations(), Thread.ofPlatform().name("hyperion-generation-", 0).factory());
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
    public synchronized void accept(WorkerCommand command) {
        ExecutionIdentity identity = command.identity();
        if (!settings.id().equals(identity.workerId()) || !incarnation.equals(identity.workerIncarnation())) {
            throw new IllegalArgumentException("Command targets another worker incarnation");
        }
        if (command.type() != WorkerCommand.Type.START) {
            ActiveExecution execution = active.get(identity.executionId());
            if (execution != null && execution.assignment.identity().equals(identity)) {
                if (command.type() == WorkerCommand.Type.CANCEL) {
                    cancel(execution);
                }
                else if (command.type() == WorkerCommand.Type.STOP_AUTHORING) {
                    execution.stopAuthoring.set(true);
                }
                else {
                    execution.renewedAt = nanoTime.getAsLong();
                }
            }
            return;
        }
        GenerationAssignment assignment = command.assignment();
        admitted.values().removeIf(deadline -> !deadline.isAfter(Instant.now()));
        if (!assignment.authoringDeadline().isAfter(Instant.now()) || admitted.containsKey(identity.executionId())) {
            return;
        }
        if (admitted.size() >= MAX_RECENT_ASSIGNMENTS) {
            // Retire this incarnation rather than forget rejection identities and allow a delayed replay.
            draining = true;
        }
        else {
            admitted.put(identity.executionId(), assignment.authoringDeadline());
        }
        GenerationEngine policy = engine.get();
        if (!ready() || policy == null || identity.slot() >= settings.maxConcurrentGenerations() || occupiedExecutions().stream().anyMatch(id -> id.slot() == identity.slot())
                || !imageDigest.equals(assignment.imageDigest())) {
            publishBestEffort(event(WorkerEvent.Type.ERROR, identity, "Generation worker is not available for this assignment.", null, null));
            return;
        }
        ActiveExecution execution = new ActiveExecution(assignment, nanoTime.getAsLong());
        active.put(identity.executionId(), execution);
        executor.submit(() -> execute(execution, policy));
    }

    private void execute(ActiveExecution execution, GenerationEngine policy) {
        ExecutionIdentity identity = execution.assignment.identity();
        WorkerEvent terminal;
        try {
            publishBestEffort(event(WorkerEvent.Type.STARTED, identity, "Starting generation on the isolated worker.", null, null));
            GenerationObserver progress = new GenerationObserver() {

                private final GenerationActivityTracker activity = new GenerationActivityTracker();

                @Override
                public GenerationActivityTracker activityTracker() {
                    return activity;
                }

                @Override
                public void accept(String message) {
                    publishBestEffort(event(WorkerEvent.Type.PROGRESS, identity, bounded(message), null, null));
                }

                @Override
                public void progress(String message, de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress detail) {
                    WorkerEvent update = event(WorkerEvent.Type.PROGRESS, identity, bounded(message), null, null).withProgress(detail);
                    if (detail.usage() == null) {
                        publishBestEffort(update);
                    }
                    else {
                        retainAccounting(update);
                    }
                }

                @Override
                public void activity(String message, GenerationActivity activity) {
                    publishBestEffort(event(WorkerEvent.Type.PROGRESS, identity, bounded(message), activity, null));
                }
            };
            GenerationOutput result = policy.generate(execution.assignment, () -> execution.cancelled.get() || execution.stopAuthoring.get(), progress,
                    checkpoint -> retainCheckpoint(identity, checkpoint));
            terminal = event(execution.cancelled.get() ? WorkerEvent.Type.CANCELLED : WorkerEvent.Type.FINISHED, identity, null, null, result);
        }
        catch (RuntimeException failure) {
            log.warn("Generation execution {} stopped ({})", identity.executionId(), failure.getClass().getSimpleName());
            terminal = event(execution.cancelled.get() ? WorkerEvent.Type.CANCELLED : WorkerEvent.Type.ERROR, identity,
                    execution.cancelled.get() ? "Generation cancelled." : "Generation failed on the worker.", null, null);
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
            pendingTerminals.put(identity.executionId(), event(execution.cancelled.get() ? WorkerEvent.Type.CANCELLED : terminal.type(), identity,
                    execution.cancelled.get() ? "Generation cancelled." : terminal.message(), null, terminal.output()));
        }
        flushTerminal();
    }

    /** Retries an undelivered terminal event and expires work only when core stops renewing its exact assignment. */
    @Scheduled(fixedDelayString = "${artemis.hyperion.worker.heartbeat-interval:PT10S}")
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
        publishBestEffort(event(WorkerEvent.Type.HEARTBEAT, currentIdentity(), null, null, null));
    }

    private synchronized void retainAccounting(WorkerEvent update) {
        if (pendingAccounting.size() >= 4_096) {
            throw new IllegalStateException("Worker accounting delivery backlog is full");
        }
        pendingAccounting.addLast(update);
        flushTerminal();
    }

    private synchronized void retainCheckpoint(ExecutionIdentity identity, GenerationOutput checkpoint) {
        pendingCheckpoints.put(identity.executionId(), event(WorkerEvent.Type.CHECKPOINT, identity, null, null, checkpoint));
        flushTerminal();
    }

    private synchronized void flushTerminal() {
        while (!pendingAccounting.isEmpty()) {
            if (!publishBestEffort(pendingAccounting.getFirst())) {
                return;
            }
            pendingAccounting.removeFirst();
        }
        flushPending(pendingCheckpoints);
        if (pendingCheckpoints.isEmpty()) {
            flushPending(pendingTerminals);
        }
    }

    private void flushPending(Map<UUID, WorkerEvent> pending) {
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            if (!publishBestEffort(iterator.next())) {
                return;
            }
            iterator.remove();
        }
    }

    private void cancel(ActiveExecution execution) {
        if (execution.cancelled.compareAndSet(false, true) && !execution.finishing) {
            execution.cleanup = CompletableFuture.runAsync(() -> cancelSandboxes.accept(execution.assignment.identity()), cancellationExecutor);
        }
    }

    private synchronized boolean ready() {
        return prepared && !draining && (active.size() + pendingTerminals.size()) < settings.maxConcurrentGenerations() && admitted.size() < MAX_RECENT_ASSIGNMENTS
                && engine.get() != null;
    }

    @Nullable
    private synchronized ExecutionIdentity currentIdentity() {
        return active.values().stream().findFirst().map(value -> value.assignment.identity()).orElse(null);
    }

    private synchronized WorkerEvent event(WorkerEvent.Type type, @Nullable ExecutionIdentity identity, @Nullable String message, @Nullable GenerationActivity activity,
            @Nullable GenerationOutput output) {
        return new WorkerEvent(WorkerCommand.PROTOCOL_VERSION, settings.id(), incarnation, sequence.incrementAndGet(), Instant.now(), type, identity, ready(), imageDigest, message,
                activity, output).withCapacity(new WorkerCapacity(settings.maxConcurrentGenerations(), occupiedExecutions()));
    }

    private synchronized List<ExecutionIdentity> occupiedExecutions() {
        var identities = new java.util.ArrayList<ExecutionIdentity>();
        active.values().forEach(value -> identities.add(value.assignment.identity()));
        pendingTerminals.values().forEach(value -> identities.add(value.identity()));
        return identities;
    }

    private boolean publishBestEffort(WorkerEvent event) {
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

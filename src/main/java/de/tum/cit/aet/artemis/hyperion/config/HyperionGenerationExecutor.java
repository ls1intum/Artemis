package de.tum.cit.aet.artemis.hyperion.config;

import java.io.Serial;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The generation worker pool, with a shutdown that distinguishes the runs it may interrupt from the ones it must not.
 * <p>
 * Spring's two stock behaviours are both wrong here. {@code waitForTasksToCompleteOnShutdown=true} would make a rolling deploy wait for every in-flight run, up to the full
 * {@code max-job-duration}, even for runs that are still only talking to the model and can be restarted for free. The default,
 * {@code waitForTasksToCompleteOnShutdown=false} with no await, calls {@code shutdownNow()} and interrupts everything — including a run inside
 * {@code GenerationPersistenceService.persist}, mid Git/DB write, whose {@code destroySession} then dies on the same interrupt and leaves the sandbox container to the idle
 * reaper.
 * <p>
 * So: refuse new work, interrupt only the runs that have not passed their point of no return (see {@link GenerationShutdownGuard}), give the rest a bounded drain sized to the
 * persistence path, and only then fall back to Spring's {@code shutdownNow()}. This is the core-node half of the drain in {@code InteractiveSandboxRelayHandler.shutdown}.
 */
public class HyperionGenerationExecutor extends ThreadPoolTaskExecutor {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(HyperionGenerationExecutor.class);

    /** The pool's own threads, captured as they are created because {@link ThreadPoolExecutor} does not expose them. */
    private final transient Set<Thread> workerThreads = ConcurrentHashMap.newKeySet();

    private final transient GenerationShutdownGuard shutdownGuard;

    private final long drainMillis;

    HyperionGenerationExecutor(GenerationShutdownGuard shutdownGuard, Duration drainTimeout) {
        this.shutdownGuard = shutdownGuard;
        this.drainMillis = drainTimeout.toMillis();
        // Never block the deploy on runs that are still restartable; the selective interrupt below is what keeps the unrestartable ones alive.
        setWaitForTasksToCompleteOnShutdown(false);
        setAwaitTerminationMillis(drainMillis);
    }

    @Override
    public Thread createThread(Runnable runnable) {
        Thread thread = super.createThread(runnable);
        workerThreads.add(thread);
        return thread;
    }

    @Override
    public void shutdown() {
        ThreadPoolExecutor executor = initializedExecutor();
        if (executor == null) {
            super.shutdown();
            return;
        }
        long startedAtNanos = System.nanoTime();
        // Stop accepting work without interrupting anyone; the queue has capacity 0, so there is nothing to drop.
        executor.shutdown();
        interruptRestartableRuns();
        awaitProtectedRuns(executor);
        long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        // Spend the drain budget once, not once here and again inside Spring's own await.
        setAwaitTerminationMillis(Math.max(0L, drainMillis - elapsedMillis));
        super.shutdown();
    }

    private void interruptRestartableRuns() {
        int protectedRuns = shutdownGuard.protectedRunCount();
        for (Thread worker : workerThreads) {
            if (worker.isAlive() && !shutdownGuard.isPastThePointOfNoReturn(worker)) {
                worker.interrupt();
            }
        }
        if (protectedRuns > 0) {
            log.info("Hyperion generation shutdown: waiting up to {} ms for {} run(s) past their point of no return; every other run was interrupted.", drainMillis, protectedRuns);
        }
    }

    private void awaitProtectedRuns(ThreadPoolExecutor executor) {
        try {
            if (!executor.awaitTermination(drainMillis, TimeUnit.MILLISECONDS)) {
                log.warn("Hyperion generation runs did not finish within the {} ms shutdown drain; interrupting them. An exercise saved by one of them may need review.",
                        drainMillis);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while draining Hyperion generation runs on shutdown.");
        }
    }

    private ThreadPoolExecutor initializedExecutor() {
        try {
            return getThreadPoolExecutor();
        }
        catch (IllegalStateException e) {
            // Never initialized (context failed before this bean started); nothing to drain.
            return null;
        }
    }
}

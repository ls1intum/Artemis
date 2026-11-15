package de.tum.cit.aet.artemis.hyperion.config;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Coordinates save admission with executor shutdown: registered workers receive a bounded grace period, and shutdown prevents new saves from starting.
 * <p>
 * Workers register before attempting the distributed non-cancellable transition and deregister if it is refused. Registration and shutdown admission share a monitor so a
 * worker cannot enter persistence after being selected for interruption.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationShutdownGuard {

    private final Set<Thread> threadsPastThePointOfNoReturn = new HashSet<>();

    private boolean shuttingDown;

    /** Marks the calling worker thread as not safe to interrupt. Call before attempting the non-cancellable transition. */
    public synchronized void enterPointOfNoReturn() {
        if (shuttingDown || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Generation stopped before saving because its worker is shutting down or interrupted");
        }
        threadsPastThePointOfNoReturn.add(Thread.currentThread());
    }

    /** Closes save admission before the executor decides which workers it may interrupt. */
    synchronized void beginShutdown() {
        shuttingDown = true;
    }

    /** Releases the calling worker thread's protection. Safe to call when it was never registered. */
    public synchronized void leavePointOfNoReturn() {
        threadsPastThePointOfNoReturn.remove(Thread.currentThread());
    }

    synchronized boolean isPastThePointOfNoReturn(Thread thread) {
        return threadsPastThePointOfNoReturn.contains(thread);
    }

    /** @return how many runs currently must not be interrupted; used only for shutdown logging */
    synchronized int protectedRunCount() {
        return threadsPastThePointOfNoReturn.size();
    }
}

package de.tum.cit.aet.artemis.hyperion.config;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Tracks which generation worker threads have passed their point of no return, so a rolling deploy interrupts only the runs it may safely interrupt.
 * <p>
 * A generation run is cancellable right up to the moment {@code GenerationJobService.enterNonCancellablePhase} succeeds; after that it is writing to Git and the database and an
 * interrupt can leave the exercise half-saved and its sandbox container behind. Spring's default executor shutdown interrupts every running task indiscriminately, so
 * {@link HyperionGenerationExecutor} consults this registry instead: cancellable runs are interrupted immediately, the rest get a bounded drain.
 * <p>
 * Registration is a superset: a run registers <em>before</em> it attempts the transition and deregisters if the transition is refused, so the registry can briefly over-protect
 * but never under-protect. Over-protecting costs at most one drain timeout; under-protecting corrupts an exercise.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationShutdownGuard {

    private final Set<Thread> threadsPastThePointOfNoReturn = ConcurrentHashMap.newKeySet();

    /** Marks the calling worker thread as not safe to interrupt. Call before attempting the non-cancellable transition. */
    public void enterPointOfNoReturn() {
        threadsPastThePointOfNoReturn.add(Thread.currentThread());
    }

    /** Releases the calling worker thread's protection. Safe to call when it was never registered. */
    public void leavePointOfNoReturn() {
        threadsPastThePointOfNoReturn.remove(Thread.currentThread());
    }

    boolean isPastThePointOfNoReturn(Thread thread) {
        return threadsPastThePointOfNoReturn.contains(thread);
    }

    /** @return how many runs currently must not be interrupted; used only for shutdown logging */
    int protectedRunCount() {
        return threadsPastThePointOfNoReturn.size();
    }
}

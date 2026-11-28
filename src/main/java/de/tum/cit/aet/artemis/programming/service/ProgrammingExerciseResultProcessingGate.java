package de.tum.cit.aet.artemis.programming.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService.ResultProcessingPermit;

/**
 * Allows concurrent result writers while deletion drains all of them before cleanup. Each writer owns one named
 * distributed lock; deletion owns every slot. This inherits the provider's ordinary AP lock guarantees.
 */
final class ProgrammingExerciseResultProcessingGate {

    // Cluster-wide protocol constants: changing capacity or names requires coordinating all result writers and deleters.
    private static final int RESULT_PROCESSING_PERMITS = 256;

    private static final Duration DELETION_WAIT = Duration.ofSeconds(60);

    private final DistributedDataProvider provider;

    private final int capacity;

    private final Duration deletionWait;

    ProgrammingExerciseResultProcessingGate(DistributedDataProvider provider) {
        this(provider, RESULT_PROCESSING_PERMITS, DELETION_WAIT);
    }

    ProgrammingExerciseResultProcessingGate(DistributedDataProvider provider, int capacity, Duration deletionWait) {
        this.provider = provider;
        this.capacity = capacity;
        this.deletionWait = deletionWait;
    }

    Optional<ResultProcessingPermit> tryAdmit(long exerciseId, BooleanSupplier deletionInProgress) {
        if (Thread.currentThread().isInterrupted() || deletionInProgress.getAsBoolean()) {
            return Optional.empty();
        }
        int start = ThreadLocalRandom.current().nextInt(capacity);
        for (int offset = 0; offset < capacity; offset++) {
            if (Thread.currentThread().isInterrupted()) {
                return Optional.empty();
            }
            var lock = provider.getLock(lockName(exerciseId) + "-permit-" + ((start + offset) % capacity));
            if (!lock.tryLock(Duration.ZERO)) {
                continue;
            }
            var permit = new Permit(List.of(lock), () -> {
            });
            try {
                if (!deletionInProgress.getAsBoolean()) {
                    return Optional.of(permit);
                }
            }
            catch (RuntimeException | Error failure) {
                closeAfterFailure(permit, failure);
                throw failure;
            }
            permit.close();
            return Optional.empty();
        }
        return Optional.empty();
    }

    ResultProcessingPermit awaitDeletion(long exerciseId, BooleanSupplier beginDeletion, Runnable clearMarker) {
        long started = System.nanoTime();
        List<DistributedLock> held = new ArrayList<>();
        boolean ownsMarker = false;
        try {
            // Serialize deletions before announcing their marker. Writers use only the separate permit slots.
            acquire(provider.getLock(lockName(exerciseId)), started, held);
            ownsMarker = beginDeletion.getAsBoolean();
            for (int slot = 0; slot < capacity; slot++) {
                acquire(provider.getLock(lockName(exerciseId) + "-permit-" + slot), started, held);
            }
            return new Permit(held, ownsMarker ? clearMarker : () -> {
            });
        }
        catch (RuntimeException | Error failure) {
            closeAfterFailure(new Permit(held, ownsMarker ? clearMarker : () -> {
            }), failure);
            throw failure;
        }
    }

    private void acquire(DistributedLock lock, long started, List<DistributedLock> held) {
        long remaining = deletionWait.toNanos() - (System.nanoTime() - started);
        if (remaining <= 0 || Thread.currentThread().isInterrupted() || !lock.tryLock(Duration.ofNanos(remaining))) {
            throw new ServiceUnavailableException("Exercise deletion could not wait for active result processing; retry the deletion.");
        }
        held.add(lock);
    }

    private static String lockName(long exerciseId) {
        return "programming-exercise-result-processing-" + exerciseId;
    }

    private static void closeAfterFailure(ResultProcessingPermit permit, Throwable failure) {
        try {
            permit.close();
        }
        catch (RuntimeException | Error cleanupFailure) {
            if (failure != cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static final class Permit implements ResultProcessingPermit {

        private final List<DistributedLock> held;

        private final Runnable clearMarker;

        private final Thread owner = Thread.currentThread();

        private boolean closed;

        private Permit(List<DistributedLock> held, Runnable clearMarker) {
            this.held = List.copyOf(held);
            this.clearMarker = clearMarker;
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Result-processing permits must be released by their acquiring thread");
            }
            if (closed) {
                return;
            }
            closed = true;
            Throwable failure = null;
            try {
                clearMarker.run();
            }
            catch (RuntimeException | Error exception) {
                failure = exception;
            }
            // The deletion mutex was acquired first, so it is always released last, even if an earlier cleanup fails.
            for (int index = held.size() - 1; index >= 0; index--) {
                try {
                    held.get(index).unlock();
                }
                catch (RuntimeException | Error exception) {
                    if (failure == null) {
                        failure = exception;
                    }
                    else if (failure != exception) {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure instanceof RuntimeException exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }
}

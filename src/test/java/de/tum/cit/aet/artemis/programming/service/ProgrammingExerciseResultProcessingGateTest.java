package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;

class ProgrammingExerciseResultProcessingGateTest {

    private static final long EXERCISE_ID = 42L;

    private static final String LOCK_NAME = "programming-exercise-result-processing-42";

    private final LocalDataProviderService provider = new LocalDataProviderService();

    private final AtomicBoolean deleting = new AtomicBoolean();

    private final ProgrammingExerciseResultProcessingGate gate = new ProgrammingExerciseResultProcessingGate(provider, 2, Duration.ofSeconds(5));

    @Test
    void independentInstancesAdmitTwoWritersAndDeletionWaitsForBoth() throws Exception {
        var otherGate = new ProgrammingExerciseResultProcessingGate(provider, 2, Duration.ofSeconds(5));
        CountDownLatch admitted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var workers = Executors.newCachedThreadPool()) {
            var first = workers.submit(() -> holdWriter(gate, admitted, release));
            var second = workers.submit(() -> holdWriter(otherGate, admitted, release));
            try {
                assertThat(admitted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(gate.tryAdmit(EXERCISE_ID, deleting::get)).as("both slots are occupied by other threads").isEmpty();
                var deletion = workers.submit(() -> {
                    try (var permit = otherGate.awaitDeletion(EXERCISE_ID, () -> !deleting.getAndSet(true), () -> deleting.set(false))) {
                        assertThat(release.getCount()).as("cleanup starts only after both writers leave").isZero();
                        assertThat(gate.tryAdmit(EXERCISE_ID, deleting::get)).isEmpty();
                    }
                });
                await().atMost(Duration.ofSeconds(5)).untilTrue(deleting);
                assertThat(deletion.isDone()).isFalse();
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                deletion.get(5, TimeUnit.SECONDS);
                assertThat(deleting).isFalse();
                assertAvailableFromAnotherThread();
            }
            finally {
                release.countDown();
            }
        }
    }

    @Test
    void markerAppearingAfterSlotAcquisitionReleasesTheSlot() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        assertThat(gate.tryAdmit(EXERCISE_ID, () -> checks.incrementAndGet() > 1)).isEmpty();

        // Occupy one slot on this thread: the other thread must still be able to acquire the released slot.
        try (var permit = gate.tryAdmit(EXERCISE_ID, () -> false).orElseThrow()) {
            assertAvailableFromAnotherThread();
        }
    }

    @Test
    void timeoutReleasesPartialOwnershipAndTheOwnedMarkerBeforeCleanup() throws Exception {
        var shortGate = new ProgrammingExerciseResultProcessingGate(provider, 2, Duration.ofMillis(100));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var writer = worker.submit(() -> holdLastSlot(held, release));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                AtomicBoolean cleanupStarted = new AtomicBoolean();
                assertThatThrownBy(() -> {
                    try (var permit = shortGate.awaitDeletion(EXERCISE_ID, () -> !deleting.getAndSet(true), () -> deleting.set(false))) {
                        cleanupStarted.set(true);
                    }
                }).isInstanceOf(ServiceUnavailableException.class);
                assertThat(cleanupStarted).isFalse();
                assertThat(deleting).isFalse();
                assertLockAvailableFromAnotherThread(LOCK_NAME + "-permit-0");
                assertLockAvailableFromAnotherThread(LOCK_NAME);
            }
            finally {
                release.countDown();
                writer.get(5, TimeUnit.SECONDS);
            }
        }
        try (var permit = shortGate.awaitDeletion(EXERCISE_ID, () -> !deleting.getAndSet(true), () -> deleting.set(false))) {
            assertThat(deleting).isTrue();
        }
    }

    @Test
    void interruptedDrainPreservesInterruptionAndReleasesPartialOwnership() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
        Thread deletion = Thread.ofPlatform().unstarted(() -> {
            try (var permit = gate.awaitDeletion(EXERCISE_ID, () -> !deleting.getAndSet(true), () -> deleting.set(false))) {
                interrupted.completeExceptionally(new AssertionError("Interrupted deletion must not start cleanup"));
            }
            catch (ServiceUnavailableException exception) {
                interrupted.complete(Thread.currentThread().isInterrupted());
            }
            catch (Throwable failure) {
                interrupted.completeExceptionally(failure);
            }
        });
        try (var worker = Executors.newSingleThreadExecutor()) {
            var writer = worker.submit(() -> holdLastSlot(held, release));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                deletion.start();
                await().atMost(Duration.ofSeconds(5)).untilTrue(deleting);
                deletion.interrupt();
                assertThat(interrupted.get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(deleting).isFalse();
                assertLockAvailableFromAnotherThread(LOCK_NAME + "-permit-0");
                assertLockAvailableFromAnotherThread(LOCK_NAME);
            }
            finally {
                deletion.interrupt();
                release.countDown();
                writer.get(5, TimeUnit.SECONDS);
                deletion.join(5000);
            }
        }
    }

    @Test
    void cancellationFailureReleasesTheDeletionMutex() throws Exception {
        assertThatThrownBy(() -> gate.awaitDeletion(EXERCISE_ID, () -> {
            throw new IllegalStateException("cancellation failed");
        }, () -> deleting.set(false))).isInstanceOf(IllegalStateException.class).hasMessage("cancellation failed");

        assertLockAvailableFromAnotherThread(LOCK_NAME);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void cleanupFailureStillReleasesEverySlotAndTheDeletionMutex(boolean sameFailure) {
        var mockedProvider = mock(DistributedDataProvider.class);
        var serial = mock(DistributedLock.class);
        var first = mock(DistributedLock.class);
        var last = mock(DistributedLock.class);
        when(mockedProvider.getLock(LOCK_NAME)).thenReturn(serial);
        when(mockedProvider.getLock(LOCK_NAME + "-permit-0")).thenReturn(first);
        when(mockedProvider.getLock(LOCK_NAME + "-permit-1")).thenReturn(last);
        when(serial.tryLock(any())).thenReturn(true);
        when(first.tryLock(any())).thenReturn(true);
        when(last.tryLock(any())).thenReturn(true);
        var markerFailure = new IllegalStateException("marker cleanup failed");
        var unlockFailure = sameFailure ? markerFailure : new IllegalStateException("slot release failed");
        doThrow(unlockFailure).when(last).unlock();
        var mockedGate = new ProgrammingExerciseResultProcessingGate(mockedProvider, 2, Duration.ofSeconds(5));
        var permit = mockedGate.awaitDeletion(EXERCISE_ID, () -> true, () -> {
            throw markerFailure;
        });

        assertThatThrownBy(permit::close).isSameAs(markerFailure);
        if (!sameFailure) {
            assertThat(markerFailure).hasSuppressedException(unlockFailure);
        }
        verify(last).unlock();
        verify(first).unlock();
        verify(serial).unlock();
        permit.close();
    }

    @Test
    void failedDeletionDoesNotClearAMarkerItDidNotCreate() {
        deleting.set(true);
        try (var permit = gate.awaitDeletion(EXERCISE_ID, () -> false, () -> deleting.set(false))) {
            assertThat(deleting).isTrue();
        }
        assertThat(deleting).isTrue();
    }

    private void holdWriter(ProgrammingExerciseResultProcessingGate writerGate, CountDownLatch admitted, CountDownLatch release) {
        try (var permit = writerGate.tryAdmit(EXERCISE_ID, deleting::get).orElseThrow()) {
            admitted.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private void holdLastSlot(CountDownLatch held, CountDownLatch release) {
        var lock = provider.getLock(LOCK_NAME + "-permit-1");
        lock.lock();
        try {
            held.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        finally {
            lock.unlock();
        }
    }

    private void assertAvailableFromAnotherThread() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            assertThat(worker.submit(() -> {
                var admission = gate.tryAdmit(EXERCISE_ID, deleting::get);
                admission.ifPresent(ProgrammingExerciseDeletionService.ResultProcessingPermit::close);
                return admission.isPresent();
            }).get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertLockAvailableFromAnotherThread(String name) throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            assertThat(worker.submit(() -> {
                var lock = provider.getLock(name);
                boolean acquired = lock.tryLock(Duration.ZERO);
                if (acquired) {
                    lock.unlock();
                }
                return acquired;
            }).get(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}

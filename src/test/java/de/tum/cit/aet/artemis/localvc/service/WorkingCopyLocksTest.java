package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.api.errors.CanceledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkingCopyLocksTest {

    @Test
    void ioFailureReleasesTheWorkingCopyForAnotherCheckout() throws Exception {
        WorkingCopyLocks locks = new WorkingCopyLocks();
        Path path = Path.of("working-copy");
        var failure = new IOException("Checkout directory cannot be deleted");
        assertThatThrownBy(() -> locks.withIOLock(path, () -> {
            throw failure;
        })).isSameAs(failure);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var checkout = executor.submit(() -> locks.withLock(path, () -> "checkout allowed"));
            try {
                assertThat(checkout.get(5, TimeUnit.SECONDS)).isEqualTo("checkout allowed");
            }
            finally {
                checkout.cancel(true);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void interruptedWaiterCancelsBeforeTheWorkingCopyBecomesAvailable(boolean ioWaiter) throws Exception {
        WorkingCopyLocks locks = new WorkingCopyLocks();
        Path path = Path.of("working-copy");
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interruptedAfterCancellation = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try {
                Runnable operation = () -> failure.set(new AssertionError("A cancelled waiter must not access the repository"));
                if (ioWaiter) {
                    locks.withIOLock(path.resolve("..").resolve("working-copy"), () -> {
                        operation.run();
                        return null;
                    });
                }
                else {
                    locks.withLock(path.resolve("..").resolve("working-copy"), () -> {
                        operation.run();
                        return null;
                    });
                }
            }
            catch (Throwable exception) {
                failure.set(exception);
                interruptedAfterCancellation.set(Thread.currentThread().isInterrupted());
            }
            finally {
                finished.countDown();
            }
        });
        try {
            locks.withLock(path, () -> {
                waiter.start();
                try {
                    assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
                    waiter.interrupt();
                    assertThat(finished.await(5, TimeUnit.SECONDS)).as("cancellation must not wait for the working-copy owner").isTrue();
                }
                catch (InterruptedException exception) {
                    throw new AssertionError(exception);
                }
                return null;
            });
            assertThat(failure.get()).isInstanceOf(ioWaiter ? InterruptedIOException.class : CanceledException.class).hasMessageContaining("interrupted");
            if (ioWaiter) {
                assertThat(failure.get()).hasCauseInstanceOf(InterruptedException.class);
            }
            assertThat(interruptedAfterCancellation.get()).isFalse();
        }
        finally {
            waiter.join(5_000);
        }
    }
}

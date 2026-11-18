package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.api.errors.CanceledException;
import org.junit.jupiter.api.Test;

class WorkingCopyLocksTest {

    @Test
    void interruptedWaiterCancelsBeforeTheWorkingCopyBecomesAvailable() throws Exception {
        WorkingCopyLocks locks = new WorkingCopyLocks();
        Path path = Path.of("working-copy");
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interruptedAfterCancellation = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try {
                locks.withLock(path.resolve("..").resolve("working-copy"), () -> {
                    failure.set(new AssertionError("A cancelled waiter must not check out the repository"));
                    return null;
                });
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
            assertThat(failure.get()).isInstanceOf(CanceledException.class).hasMessageContaining("interrupted");
            assertThat(interruptedAfterCancellation.get()).isFalse();
        }
        finally {
            waiter.join(5_000);
        }
    }
}

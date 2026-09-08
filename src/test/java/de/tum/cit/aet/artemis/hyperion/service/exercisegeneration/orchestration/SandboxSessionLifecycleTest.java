package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * The teardown/capture invariant on its own: a sandbox session is never destroyed while its work is being copied out.
 * <p>
 * The observed failure this guards is a cancellation dispatched on {@code GenerationJobService}'s cancellation executor destroying the container roughly 300 ms before the
 * generation thread's capture path issued its {@code copyOut} calls, so all three failed and the run retained nothing.
 */
class SandboxSessionLifecycleTest {

    private static final String SESSION_ID = "session-a1f1d61a";

    private final AtomicInteger teardowns = new AtomicInteger();

    private final SandboxSessionLifecycle lifecycle = new SandboxSessionLifecycle(SESSION_ID, teardowns::incrementAndGet);

    @Test
    void aFreshSessionIsActiveAndAdmitsCaptures() {
        assertThat(lifecycle.state()).isEqualTo(SandboxSessionLifecycle.State.ACTIVE);
        assertThat(lifecycle.beginCapture()).isTrue();
        assertThat(lifecycle.state()).isEqualTo(SandboxSessionLifecycle.State.CAPTURING);
    }

    @Test
    void withNoCaptureInFlight_aDestroyRequestTearsDownImmediately() {
        lifecycle.requestDestroy();

        assertThat(teardowns).hasValue(1);
        assertThat(lifecycle.state()).isEqualTo(SandboxSessionLifecycle.State.DESTROYED);
    }

    @Test
    void aDestroyRequestedDuringACaptureIsDeferredUntilTheCaptureEnds_andRunsExactlyOnce() {
        assertThat(lifecycle.beginCapture()).isTrue();

        lifecycle.requestDestroy();

        assertThat(teardowns).as("the work is still being copied out, so nothing may be torn down yet").hasValue(0);
        assertThat(lifecycle.state()).isEqualTo(SandboxSessionLifecycle.State.DESTROY_DEFERRED);

        lifecycle.endCapture();

        assertThat(teardowns).as("the deferred teardown happens once the capture releases the session, and only once").hasValue(1);
        assertThat(lifecycle.state()).isEqualTo(SandboxSessionLifecycle.State.DESTROYED);
    }

    @Test
    void aDestroyRequestedDuringACaptureNeverBlocksTheRequestingThread() throws Exception {
        assertThat(lifecycle.beginCapture()).isTrue();
        ExecutorService cancellationExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch returned = new CountDownLatch(1);
        try {
            cancellationExecutor.execute(() -> {
                lifecycle.requestDestroy();
                returned.countDown();
            });

            // The real executor also carries unrelated cancellations, so a destroy that waited for a multi-second repository copy here would stall them.
            assertThat(returned.await(2, TimeUnit.SECONDS)).as("requestDestroy returns without waiting for the in-flight capture").isTrue();
            assertThat(teardowns).hasValue(0);
        }
        finally {
            cancellationExecutor.shutdownNow();
        }

        lifecycle.endCapture();

        assertThat(teardowns).hasValue(1);
    }

    @Test
    void nestedCapturesDeferTheTeardownUntilTheOutermostOneEnds() {
        assertThat(lifecycle.beginCapture()).isTrue();
        assertThat(lifecycle.beginCapture()).as("a nested read-back of the same live session is admitted").isTrue();
        lifecycle.requestDestroy();

        lifecycle.endCapture();

        assertThat(teardowns).as("the outer capture still holds the session").hasValue(0);

        lifecycle.endCapture();

        assertThat(teardowns).hasValue(1);
    }

    @Test
    void aCaptureBeginningAfterADestroyIsRefusedSoTheCallerCanSkipItsCopyOuts() {
        lifecycle.requestDestroy();

        assertThat(lifecycle.beginCapture()).isFalse();
        assertThat(lifecycle.isDestroyed()).isTrue();
        assertThat(teardowns).as("a refused capture must not trigger another teardown").hasValue(1);
    }

    @Test
    void endCaptureWithoutAPendingDestroyLeavesTheSessionAlive() {
        assertThat(lifecycle.beginCapture()).isTrue();

        lifecycle.endCapture();

        assertThat(teardowns).hasValue(0);
        assertThat(lifecycle.state()).isEqualTo(SandboxSessionLifecycle.State.ACTIVE);
    }

    @Test
    void concurrentDestroyRequestsTearTheSessionDownOnce() throws Exception {
        int requesters = 16;
        ExecutorService executor = Executors.newFixedThreadPool(requesters);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requesters);
        try {
            for (int i = 0; i < requesters; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        lifecycle.requestDestroy();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(teardowns).hasValue(1);
    }

    @Test
    void aDestroyRacingACaptureThatEndsTearsDownOnce() throws Exception {
        int rounds = 200;
        for (int round = 0; round < rounds; round++) {
            AtomicInteger roundTeardowns = new AtomicInteger();
            SandboxSessionLifecycle raced = new SandboxSessionLifecycle(SESSION_ID, roundTeardowns::incrementAndGet);
            assertThat(raced.beginCapture()).isTrue();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                CountDownLatch requested = new CountDownLatch(1);
                executor.execute(() -> {
                    raced.requestDestroy();
                    requested.countDown();
                });
                raced.endCapture();
                assertThat(requested.await(5, TimeUnit.SECONDS)).isTrue();
            }
            finally {
                executor.shutdownNow();
            }
            assertThat(roundTeardowns).as("round %d tears the session down exactly once", round).hasValue(1);
            assertThat(raced.beginCapture()).isFalse();
        }
    }
}

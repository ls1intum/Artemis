package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class HyperionAsyncConfigurationTest {

    @Test
    void generationExecutor_acceptsConfiguredConcurrencyWithoutQueuingMoreJobs() throws InterruptedException {
        HyperionAsyncConfiguration configuration = new HyperionAsyncConfiguration();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configuration.hyperionGenerationExecutor(5, new GenerationShutdownGuard(), Duration.ofSeconds(1));
        CountDownLatch started = new CountDownLatch(5);
        CountDownLatch release = new CountDownLatch(1);

        try {
            for (int i = 0; i < 5; i++) {
                executor.execute(() -> {
                    started.countDown();
                    try {
                        release.await();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatExceptionOfType(TaskRejectedException.class).isThrownBy(() -> executor.execute(() -> {
            }));
        }
        finally {
            release.countDown();
            executor.shutdown();
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat((Set<?>) ReflectionTestUtils.getField(executor, "workerThreads")).as("terminated workers must not accumulate in the shutdown registry").isEmpty());
    }

    @Test
    void generationExecutorShutdown_interruptsARestartableRunButDrainsOneThatPassedItsPointOfNoReturn() throws Exception {
        GenerationShutdownGuard shutdownGuard = new GenerationShutdownGuard();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new HyperionAsyncConfiguration().hyperionGenerationExecutor(2, shutdownGuard, Duration.ofSeconds(60));
        CountDownLatch bothRunning = new CountDownLatch(2);
        CountDownLatch releaseProtectedRun = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean protectedRunWasInterrupted = new AtomicBoolean();
        AtomicBoolean restartableRunWasInterrupted = new AtomicBoolean();

        executor.execute(() -> {
            shutdownGuard.enterPointOfNoReturn();
            bothRunning.countDown();
            try {
                releaseProtectedRun.await(60, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                protectedRunWasInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
            finally {
                shutdownGuard.leavePointOfNoReturn();
            }
        });
        executor.execute(() -> {
            bothRunning.countDown();
            try {
                neverReleased.await(60, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                restartableRunWasInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        assertThat(bothRunning.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> shutdown = CompletableFuture.runAsync(executor::shutdown);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(restartableRunWasInterrupted).isTrue());
        // Still draining: shutdown is waiting for the protected run, and has not interrupted it to make it stop.
        assertThat(shutdown).isNotDone();
        assertThat(protectedRunWasInterrupted).isFalse();

        releaseProtectedRun.countDown();
        shutdown.get(30, TimeUnit.SECONDS);
        assertThat(protectedRunWasInterrupted).isFalse();
    }

    @Test
    void generationExecutorRejectsNonPositiveConcurrency() {
        HyperionAsyncConfiguration configuration = new HyperionAsyncConfiguration();

        assertThatIllegalArgumentException().isThrownBy(() -> configuration.hyperionGenerationExecutor(0, new GenerationShutdownGuard(), Duration.ofSeconds(1)))
                .withMessageContaining("at least 1");
    }

    @Test
    void shutdownPreventsAnInterruptedWorkerFromStartingASaveEvenIfItClearsTheInterrupt() throws Exception {
        GenerationShutdownGuard guard = new GenerationShutdownGuard();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new HyperionAsyncConfiguration().hyperionGenerationExecutor(1, guard, Duration.ofSeconds(5));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        CompletableFuture<Boolean> saveRejected = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    blocked.await();
                    saveRejected.completeExceptionally(new AssertionError("Worker was released without a shutdown interrupt"));
                }
                catch (InterruptedException ignored) {
                    // InterruptedException clears the flag; shutdown must still prevent a later save.
                    try {
                        guard.enterPointOfNoReturn();
                        saveRejected.complete(false);
                    }
                    catch (CancellationException expected) {
                        saveRejected.complete(true);
                    }
                    finally {
                        guard.leavePointOfNoReturn();
                    }
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            assertThat(saveRejected.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(guard.protectedRunCount()).isZero();
        }
        finally {
            blocked.countDown();
            executor.shutdown();
        }
    }

    @Test
    void interruptedWorkerCannotEnterSavePhase() {
        GenerationShutdownGuard guard = new GenerationShutdownGuard();
        Thread.currentThread().interrupt();
        try {
            assertThatExceptionOfType(CancellationException.class).isThrownBy(guard::enterPointOfNoReturn);
            assertThat(guard.protectedRunCount()).isZero();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }
}

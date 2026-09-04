package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
    }

    /**
     * A rolling deploy must not interrupt a run that is mid Git/DB write: an interrupt inside {@code GenerationPersistenceService.persist} also kills its {@code destroySession}
     * and leaves the sandbox container behind. Waiting for every run instead would block the deploy for up to the full {@code max-job-duration} on runs that are still only
     * talking to the model and cost nothing to restart.
     */
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
}

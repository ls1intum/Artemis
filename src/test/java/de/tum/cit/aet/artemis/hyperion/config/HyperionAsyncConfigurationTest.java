package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class HyperionAsyncConfigurationTest {

    @Test
    void generationExecutor_acceptsConfiguredConcurrencyWithoutQueuingMoreJobs() throws InterruptedException {
        HyperionAsyncConfiguration configuration = new HyperionAsyncConfiguration();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configuration.hyperionGenerationExecutor(5);
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

            org.assertj.core.api.Assertions.assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatExceptionOfType(TaskRejectedException.class).isThrownBy(() -> executor.execute(() -> {
            }));
        }
        finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void generationExecutorRejectsNonPositiveConcurrency() {
        HyperionAsyncConfiguration configuration = new HyperionAsyncConfiguration();

        assertThatIllegalArgumentException().isThrownBy(() -> configuration.hyperionGenerationExecutor(0)).withMessageContaining("at least 1");
    }
}

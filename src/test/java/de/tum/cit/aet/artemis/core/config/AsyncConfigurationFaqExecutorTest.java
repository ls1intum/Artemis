package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.mock.env.MockEnvironment;

class AsyncConfigurationFaqExecutorTest {

    @Test
    void saturationNeverRunsFaqSynchronizationOnCaller() throws Exception {
        var configuration = new AsyncConfiguration(new TaskExecutionProperties(), new MockEnvironment());
        var executor = configuration.faqTaskExecutor();
        ((InitializingBean) executor).afterPropertiesSet();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1000);
        var executed = new AtomicInteger();
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 1001; i++) {
                executor.execute(() -> {
                    executed.incrementAndGet();
                    finished.countDown();
                });
            }
            assertThat(executed).hasValue(0);
            release.countDown();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executed).hasValue(1000);
        }
        finally {
            release.countDown();
            ((DisposableBean) executor).destroy();
        }
    }
}

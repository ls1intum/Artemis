package de.tum.cit.aet.artemis.core.util.junit_extensions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import de.tum.cit.aet.artemis.core.async.ExceptionHandlingAsyncTaskExecutor;

class AsyncWorkDrainExtensionTest {

    private GenericApplicationContext applicationContext;

    private ExceptionHandlingAsyncTaskExecutor taskExecutor;

    @BeforeEach
    void setUp() throws Exception {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(10);
        taskExecutor = new ExceptionHandlingAsyncTaskExecutor(pool);
        taskExecutor.afterPropertiesSet();
        applicationContext = new GenericApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("taskExecutor", taskExecutor);
        applicationContext.refresh();
    }

    @AfterEach
    void tearDown() throws Exception {
        applicationContext.close();
        taskExecutor.destroy();
    }

    @Test
    void drainWaitsForARunningAndForAQueuedTask() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstFinished = new AtomicBoolean();
        AtomicBoolean secondFinished = new AtomicBoolean();
        taskExecutor.execute(() -> {
            await(release);
            firstFinished.set(true);
        });
        // the pool has one thread, so this one waits in the queue until the first has finished
        taskExecutor.execute(() -> secondFinished.set(true));

        // the first task is blocked until the other thread releases it, so a drain that does not wait returns while both tasks are unfinished
        Thread releaser = Thread.ofPlatform().start(() -> {
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            release.countDown();
        });
        AsyncWorkDrainExtension.drain(applicationContext, Duration.ofSeconds(30), "drainWaitsForARunningAndForAQueuedTask");
        releaser.join();

        assertThat(firstFinished).isTrue();
        assertThat(secondFinished).isTrue();
        assertThat(AsyncWorkDrainExtension.hasGivenUp(applicationContext)).isFalse();
    }

    @Test
    void drainReturnsAtOnceWhenThePoolIsIdle() {
        AsyncWorkDrainExtension.drain(applicationContext, Duration.ofSeconds(30), "drainReturnsAtOnceWhenThePoolIsIdle");

        assertThat(AsyncWorkDrainExtension.hasGivenUp(applicationContext)).isFalse();
    }

    @Test
    void drainGivesUpOnAContextWhoseTaskDoesNotFinish() {
        CountDownLatch neverReleased = new CountDownLatch(1);
        taskExecutor.execute(() -> await(neverReleased));

        AsyncWorkDrainExtension.drain(applicationContext, Duration.ofMillis(200), "drainGivesUpOnAContextWhoseTaskDoesNotFinish");

        assertThat(AsyncWorkDrainExtension.hasGivenUp(applicationContext)).isTrue();
        neverReleased.countDown();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

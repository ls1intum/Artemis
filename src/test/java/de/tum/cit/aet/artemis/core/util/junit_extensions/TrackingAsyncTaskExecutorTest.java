package de.tum.cit.aet.artemis.core.util.junit_extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import de.tum.cit.aet.artemis.core.async.ExceptionHandlingAsyncTaskExecutor;

class TrackingAsyncTaskExecutorTest {

    @Test
    void rejectedWorkDoesNotLeaveADrainReservation() {
        var executor = new TrackingAsyncTaskExecutor(new TaskExecutorAdapter(task -> {
            throw new RejectedExecutionException("full");
        }));

        assertThatThrownBy(() -> executor.execute(() -> {
        })).isInstanceOf(RejectedExecutionException.class);

        assertThat(executor.isIdle()).isTrue();
    }

    @Test
    void inlineFailureReleasesItsReservationExactlyOnce() {
        var executor = new TrackingAsyncTaskExecutor(new TaskExecutorAdapter(Runnable::run));

        assertThatThrownBy(() -> executor.execute(() -> {
            throw new IllegalStateException("task failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(executor.isIdle()).isTrue();
    }

    @Test
    void cancellingAQueuedFutureStillWaitsForItsQueuedWrapper() {
        var accepted = new LinkedBlockingQueue<Runnable>();
        var executor = new TrackingAsyncTaskExecutor(new TaskExecutorAdapter(accepted::add));
        AtomicBoolean ran = new AtomicBoolean();
        var future = executor.submit(() -> ran.set(true));

        assertThat(future.cancel(false)).isTrue();
        assertThat(executor.isIdle()).isFalse();
        accepted.remove().run();

        assertThat(ran).isFalse();
        assertThat(executor.isIdle()).isTrue();
    }

    @Test
    void cancellingARunningFutureWaitsUntilItsBodyExits() throws InterruptedException {
        Semaphore release = new Semaphore(0);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean exited = new AtomicBoolean();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var executor = new TrackingAsyncTaskExecutor(new TaskExecutorAdapter(pool));
            var future = executor.submit(() -> {
                entered.countDown();
                release.acquireUninterruptibly();
                exited.set(true);
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(future.cancel(true)).isTrue();
                assertThat(executor.isIdle()).isFalse();
                assertThat(exited).isFalse();
            }
            finally {
                release.release();
            }
            await().atMost(Duration.ofSeconds(5)).until(executor::isIdle);
            assertThat(exited).isTrue();
        }
    }

    @Test
    void callableFuturesRetainTheirValueAndFailure() throws Exception {
        var executor = new TrackingAsyncTaskExecutor(new TaskExecutorAdapter(Runnable::run));

        assertThat(executor.submit(() -> "answer").get()).isEqualTo("answer");
        var failed = executor.submit(() -> {
            throw new IllegalArgumentException("invalid");
        });

        assertThatThrownBy(failed::get).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(executor.isIdle()).isTrue();
    }

    @Test
    void springOwnsTheOriginalExecutorsLifecycleExactlyOnce() {
        AtomicInteger initialized = new AtomicInteger();
        AtomicInteger destroyed = new AtomicInteger();
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(AsyncWorkTrackingConfiguration.class);
            context.registerBean("taskExecutor", ExceptionHandlingAsyncTaskExecutor.class, () -> new ExceptionHandlingAsyncTaskExecutor(pool) {

                @Override
                public void afterPropertiesSet() throws Exception {
                    initialized.incrementAndGet();
                    super.afterPropertiesSet();
                }

                @Override
                public void destroy() throws Exception {
                    destroyed.incrementAndGet();
                    super.destroy();
                }
            });
            context.refresh();
            assertThat(initialized).hasValue(1);
            assertThat(destroyed).hasValue(0);
            assertThat(pool.getThreadPoolExecutor().isShutdown()).isFalse();
        }
        assertThat(initialized).hasValue(1);
        assertThat(destroyed).hasValue(1);
        assertThat(pool.getThreadPoolExecutor().isShutdown()).isTrue();
    }
}

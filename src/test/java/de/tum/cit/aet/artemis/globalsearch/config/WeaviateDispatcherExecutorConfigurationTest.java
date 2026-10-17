package de.tum.cit.aet.artemis.globalsearch.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class WeaviateDispatcherExecutorConfigurationTest {

    /**
     * Nudges arrive after every enqueue, often while a drain is running. A nudge must never run the drain on the caller's
     * thread (a request thread of a user), and nudges that cannot run must not pile up: one waiting nudge is enough,
     * because the drain re-checks for pending work before it ends.
     */
    @Test
    void nudgesNeverRunOnTheCallerThreadAndAtMostOneWaits() throws Exception {
        var executor = new WeaviateDispatcherExecutorConfiguration().weaviateOutboxDispatcherExecutor();
        try {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            AtomicInteger ranOnCaller = new AtomicInteger();
            AtomicInteger ran = new AtomicInteger();
            Thread caller = Thread.currentThread();
            Runnable nudge = () -> {
                if (Thread.currentThread() == caller) {
                    ranOnCaller.incrementAndGet();
                }
                ran.incrementAndGet();
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            executor.execute(nudge);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 10; i++) {
                executor.execute(nudge);
            }
            release.countDown();
            executor.getThreadPoolExecutor().shutdown();
            assertThat(executor.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            assertThat(ranOnCaller).hasValue(0);
            assertThat(ran).as("the running nudge and the one waiting nudge").hasValue(2);
        }
        finally {
            executor.shutdown();
        }
    }
}

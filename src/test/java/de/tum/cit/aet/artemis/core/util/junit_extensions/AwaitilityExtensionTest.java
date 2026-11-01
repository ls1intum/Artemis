package de.tum.cit.aet.artemis.core.util.junit_extensions;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Pins the {@link Awaitility} defaults the {@link AwaitilityExtension} applies to the whole suite. The extension is
 * picked up by JUnit's extension autodetection, so these tests run with it active like every other test class.
 */
class AwaitilityExtensionTest {

    /**
     * Starts a thread that dies with an uncaught exception once the caller is inside {@code await()}, the way a
     * background thread of an unrelated, already closed Spring context does in the shared test JVM.
     */
    private static void dieOnAnUnrelatedThreadWhileAwaiting() throws InterruptedException {
        var started = new CountDownLatch(1);
        var unrelated = new Thread(() -> {
            started.countDown();
            try {
                Thread.sleep(50);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Executor has been shut down");
        }, "unrelated-background-thread");
        unrelated.start();
        started.await(5, TimeUnit.SECONDS);
    }

    @Test
    void anUncaughtExceptionOnAnUnrelatedThreadDoesNotFailAnAwait() {
        assertThatCode(() -> {
            dieOnAnUnrelatedThreadWhileAwaiting();
            // Awaitility's own default would rethrow the exception of the other thread here, although this wait only
            // sleeps and has nothing to do with it.
            await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).until(() -> true);
        }).doesNotThrowAnyException();
    }
}

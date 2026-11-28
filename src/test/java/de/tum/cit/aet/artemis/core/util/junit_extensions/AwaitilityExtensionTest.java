package de.tum.cit.aet.artemis.core.util.junit_extensions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Pins the {@link org.awaitility.Awaitility} defaults the {@link AwaitilityExtension} applies to the whole suite. The
 * extension is picked up by JUnit's extension autodetection, so these tests run with it active like every other test
 * class.
 */
class AwaitilityExtensionTest {

    @Test
    void anUncaughtExceptionOnAnUnrelatedThreadDoesNotFailAnAwait() {
        var unrelated = new AtomicReference<Thread>();

        assertThatCode(() -> await().atMost(Duration.ofSeconds(10)).until(() -> {
            if (unrelated.get() == null) {
                // The condition only runs while await() is polling, so the thread below dies with Awaitility's
                // uncaught exception handling in place. It is the way a background thread of an unrelated, already
                // closed Spring context dies in the shared test JVM.
                var thread = new Thread(() -> {
                    throw new IllegalStateException("Executor has been shut down");
                }, "unrelated-background-thread");
                unrelated.set(thread);
                thread.start();
                // The uncaught exception handler runs before the thread ends, so after the join the exception is
                // known to be delivered. The wait is not over yet: it polls again, and Awaitility's own default would
                // rethrow the exception of the other thread there, although this wait has nothing to do with it.
                thread.join(Duration.ofSeconds(5));
                return false;
            }
            return true;
        })).doesNotThrowAnyException();

        assertThat(unrelated.get().isAlive()).as("the unrelated thread must have died before the await was satisfied").isFalse();
    }
}

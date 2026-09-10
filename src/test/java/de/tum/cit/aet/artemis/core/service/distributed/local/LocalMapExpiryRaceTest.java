package de.tum.cit.aet.artemis.core.service.distributed.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * {@link LocalMap} enforces expiry by purging on every read, so a purge runs concurrently with ordinary writes on
 * whichever thread happens to read. A value and its deadline therefore have to move together under the key lock.
 *
 * <p>
 * They did not: the purge decided an entry was expired and dropped its deadline, and only then took the key lock to
 * evict it. A write landing in that window stored a fresh value, and the purge deleted that instead of the expired one.
 * The key lock is used here to hold the purge inside exactly that window, so the test pins the ordering rather than
 * hoping to hit it.
 */
class LocalMapExpiryRaceTest {

    private static final Duration ALREADY_ELAPSED = Duration.ofNanos(1);

    @Test
    void testAValueWrittenWhileThePurgeIsMidEvictionSurvives() throws Exception {
        var map = new LocalMap<String, String>();
        map.put("victim", "expired", ALREADY_ELAPSED);
        map.put("trigger", "value");
        // Make sure the deadline really has elapsed before anything reads.
        Thread.sleep(5);

        var writerHoldsLock = new CountDownLatch(1);
        var writerMayFinish = new CountDownLatch(1);

        // Holds the key lock, so the purge cannot get past the point where it has already given up the deadline.
        var writer = new Thread(() -> {
            map.lock("victim");
            try {
                writerHoldsLock.countDown();
                await(writerMayFinish);
                // Reentrant: this thread already owns the lock, so the write lands while the purge is queued behind it.
                map.put("victim", "fresh");
            }
            finally {
                map.unlock("victim");
            }
        }, "writer");

        // Any read triggers the purge, which finds "victim" expired and tries to evict it.
        var purger = new Thread(() -> map.get("trigger"), "purger");

        writer.start();
        assertThat(writerHoldsLock.await(10, TimeUnit.SECONDS)).as("the writer should have taken the key lock").isTrue();
        purger.start();
        // Give the purge time to reach the lock and block on it, which is the window the race needs.
        Thread.sleep(300);
        writerMayFinish.countDown();

        writer.join(TimeUnit.SECONDS.toMillis(10));
        purger.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(map.get("victim")).as("the purge must not delete a value written after it released the deadline").isEqualTo("fresh");
    }

    @Test
    void testAnEntryWhoseLifetimeElapsedIsStillEvicted() {
        var map = new LocalMap<String, String>();
        map.put("victim", "expired", ALREADY_ELAPSED);
        map.put("trigger", "value");

        // The read purges; nothing raced it, so the expired entry has to be gone.
        map.get("trigger");

        assertThat(map.get("victim")).as("an entry nobody rewrote must still expire").isNull();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}

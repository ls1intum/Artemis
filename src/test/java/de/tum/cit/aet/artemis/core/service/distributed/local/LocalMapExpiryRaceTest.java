package de.tum.cit.aet.artemis.core.service.distributed.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Exercises expiry and lock lifetime under concurrent access to the single-node provider. */
class LocalMapExpiryRaceTest {

    private static final Duration ALREADY_ELAPSED = Duration.ofNanos(1);

    @Test
    void releasesKeyLocksAfterTheLastUser() {
        var map = new LocalMap<Integer, Integer>();
        var registry = ReflectionTestUtils.getField(map, "locks");
        var locks = (Map<?, ?>) ReflectionTestUtils.getField(registry, "entries");
        for (int key = 0; key < 1000; key++) {
            assertThat(map.get(key)).isNull();
            map.lock(key);
            try {
                map.put(key, key);
                map.remove(key);
                assertThat(locks).hasSize(1);
            }
            finally {
                map.unlock(key);
            }
        }
        assertThat(locks).isEmpty();
    }

    @Test
    void readingAnExpiredKeyWhileHoldingItsLockDoesNotWaitForAnotherKey() throws Exception {
        var map = new LocalMap<String, String>();
        map.put("first", "expired", Duration.ZERO);
        map.put("second", "expired", Duration.ZERO);
        map.lock("first");
        try {
            var read = new FutureTask<>(() -> {
                map.lock("second");
                try {
                    return map.get("second");
                }
                finally {
                    map.unlock("second");
                }
            });
            Thread.ofVirtual().start(read);
            assertThat(read.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(map.get("first")).isNull();
        }
        finally {
            map.unlock("first");
        }
    }

    @Test
    void namedLocksCoordinateSeparateHandlesAndReleaseTimedOutWaiters() throws Exception {
        var provider = new LocalDataProviderService();
        var owner = provider.getLock("session");
        var secondHandle = provider.getLock("session");
        owner.lock();
        try {
            assertThat(secondHandle.tryLock(Duration.ZERO)).isTrue();
            secondHandle.unlock();
            var attempt = new FutureTask<>(() -> secondHandle.tryLock(Duration.ZERO));
            Thread.ofVirtual().start(attempt);
            assertThat(attempt.get(5, TimeUnit.SECONDS)).isFalse();
        }
        finally {
            owner.unlock();
        }
        assertThat(secondHandle.tryLock(Duration.ZERO)).isTrue();
        secondHandle.unlock();
        for (int i = 0; i < 1000; i++) {
            var lock = provider.getLock("session-" + i);
            lock.lock();
            lock.unlock();
        }
        var registry = ReflectionTestUtils.getField(provider, "locks");
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(registry, "entries")).isEmpty();
    }

    @Test
    void interruptedAcquisitionPreservesInterruptStatusAndReleasesTheName() {
        var provider = new LocalDataProviderService();
        var lock = provider.getLock("session");
        Thread.currentThread().interrupt();
        try {
            assertThat(lock.tryLock(Duration.ZERO)).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
        assertThatExceptionOfType(IllegalMonitorStateException.class).isThrownBy(lock::unlock);
    }

    @Test
    void providerCleanupEvictsExpiredKeysWithoutSubsequentReads() {
        var provider = new LocalDataProviderService();
        var expiring = provider.<String, String>getExpiringMap("test", Duration.ofMinutes(1));
        expiring.put("expired", "value", Duration.ZERO);
        expiring.put("live", "value");
        provider.purgeExpiredEntries();
        var maps = (Map<?, ?>) ReflectionTestUtils.getField(provider, "expiringMaps");
        var entries = (Map<?, ?>) ReflectionTestUtils.getField(maps.get("test"), "map");
        assertThat(entries.keySet()).isEqualTo(java.util.Set.of("live"));
    }

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

        // A bulk read checks every deadline, including the locked key.
        var purger = new Thread(map::size, "purger");

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

        assertThat(map.get("trigger")).isEqualTo("value");

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

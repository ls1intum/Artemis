package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.atlas.domain.competency.ContentChangeAccumulator;
import de.tum.cit.aet.artemis.atlas.service.ContentChangeAccumulatorService.BatchClaim;

/**
 * Exercises the Hazelcast-backed accumulator against a real embedded Hazelcast instance so the
 * entry processors' serialization and ordering semantics are tested end-to-end. A
 * {@link MutableClock} lets us simulate the passage of time past the debounce window and across
 * day boundaries without touching {@link System#currentTimeMillis()}.
 */
@TestInstance(Lifecycle.PER_CLASS)
class ContentChangeAccumulatorServiceTest {

    private HazelcastInstance hazelcastInstance;

    private MutableClock clock;

    private ContentChangeAccumulatorService service;

    private static final int DEBOUNCE_WINDOW_SECONDS = 60;

    private static final int DAILY_CAP = 3;

    @BeforeAll
    void bootHazelcast() {
        Config config = new Config();
        config.setClusterName("atlas-accumulator-test");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        config.setProperty("hazelcast.logging.type", "slf4j");
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    void shutdownHazelcast() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-04-24T12:00:00Z"));
        service = new ContentChangeAccumulatorService(hazelcastInstance, clock, DEBOUNCE_WINDOW_SECONDS, DAILY_CAP);
        service.clearForTesting();
    }

    @Test
    void record_mergesMultipleEventsIntoSameCourseBucket() {
        service.record(1L, 10L, false);
        clock.advanceSeconds(5);
        service.record(1L, 11L, false);
        clock.advanceSeconds(5);
        service.record(1L, 20L, true);

        assertThat(service.listDueCourseIds()).isEmpty();
        assertThat(service.claimDueBatch(1L)).isEmpty();
    }

    @Test
    void claimDueBatch_drainsBucketOnceDebounceWindowElapses() {
        service.record(1L, 10L, false);
        service.record(1L, 20L, true);
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);

        assertThat(service.listDueCourseIds()).containsExactly(1L);

        Optional<BatchClaim> claim = service.claimDueBatch(1L);
        assertThat(claim).isPresent();
        assertThat(claim.get().exerciseIds()).containsExactly(10L);
        assertThat(claim.get().lectureUnitIds()).containsExactly(20L);

        assertThat(service.claimDueBatch(1L)).isEmpty();
    }

    @Test
    void claimDueBatch_respectsPerDayCap() {
        for (int i = 0; i < DAILY_CAP; i++) {
            service.record(1L, 100L + i, false);
            clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
            Optional<BatchClaim> claim = service.claimDueBatch(1L);
            assertThat(claim).as("run #%d", i + 1).isPresent();
        }

        service.record(1L, 999L, false);
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
        assertThat(service.claimDueBatch(1L)).isEmpty();

        clock.advanceSeconds(24 * 60 * 60);
        Optional<BatchClaim> nextDayClaim = service.claimDueBatch(1L);
        assertThat(nextDayClaim).isPresent();
        assertThat(nextDayClaim.get().exerciseIds()).containsExactly(999L);
    }

    @Test
    void listDueCourseIds_keepsCoursesIndependent() {
        service.record(1L, 10L, false);
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
        service.record(2L, 20L, false);

        assertThat(service.listDueCourseIds()).containsExactly(1L);
        assertThat(service.claimDueBatch(2L)).isEmpty();
    }

    @Test
    void mergeEntryProcessorReplaysAcrossSerialization() {
        service.record(1L, 10L, false);
        ContentChangeAccumulator first = hazelcastInstance.<Long, ContentChangeAccumulator>getMap(ContentChangeAccumulatorService.MAP_NAME).get(1L);
        assertThat(first).isNotNull();
        assertThat(first.exerciseIds()).containsExactly(10L);

        service.record(1L, 11L, false);
        ContentChangeAccumulator second = hazelcastInstance.<Long, ContentChangeAccumulator>getMap(ContentChangeAccumulatorService.MAP_NAME).get(1L);
        assertThat(second).isNotNull();
        assertThat(second.exerciseIds()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void tryClaimLock_secondAcquirerIsRejectedUntilRelease() {
        assertThat(service.tryClaimLock(42L)).isTrue();
        try {
            // Same JVM, same thread re-entering — Hazelcast IMap.tryLock is reentrant, so this would
            // succeed. Spawn a fresh thread to model a second scheduler tick on another node.
            Thread other = new Thread(() -> assertThat(service.tryClaimLock(42L)).as("second acquirer must be rejected while lock is held").isFalse());
            other.start();
            other.join();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        finally {
            service.releaseLock(42L);
        }

        // After release, a fresh acquirer succeeds.
        assertThat(service.tryClaimLock(42L)).isTrue();
        service.releaseLock(42L);
    }

    /**
     * Simple mutable clock for tests — fixed to UTC because the service computes
     * {@link java.time.LocalDate#now(Clock)} against the clock's zone.
     */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }
}

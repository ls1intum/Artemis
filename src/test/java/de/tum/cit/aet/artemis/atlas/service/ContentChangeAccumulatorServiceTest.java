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

import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
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
        AtlasOrchestratorProperties properties = new AtlasOrchestratorProperties("gpt-test", 1.0, "", DEBOUNCE_WINDOW_SECONDS, DAILY_CAP, 30000L);
        service = new ContentChangeAccumulatorService(hazelcastInstance, clock, properties);
        service.initMap();
        service.clearForTesting();
    }

    @Test
    void record_mergesMultipleEventsIntoSameCourseBucket() {
        service.record(1L, 10L);
        clock.advanceSeconds(5);
        service.record(1L, 11L);

        assertThat(service.listDueCourseIds()).isEmpty();
        assertThat(service.claimDueBatch(1L)).isEmpty();
    }

    @Test
    void claimDueBatch_drainsBucketOnceDebounceWindowElapses() {
        service.record(1L, 10L);
        service.record(1L, 11L);
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);

        assertThat(service.listDueCourseIds()).containsExactly(1L);

        Optional<BatchClaim> claim = service.claimDueBatch(1L);
        assertThat(claim).isPresent();
        assertThat(claim.get().exerciseIds()).containsExactlyInAnyOrder(10L, 11L);

        assertThat(service.claimDueBatch(1L)).isEmpty();
    }

    @Test
    void claimDueBatch_respectsPerDayCap() {
        for (int i = 0; i < DAILY_CAP; i++) {
            service.record(1L, 100L + i);
            clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
            Optional<BatchClaim> claim = service.claimDueBatch(1L);
            assertThat(claim).as("run #%d", i + 1).isPresent();
        }

        service.record(1L, 999L);
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
        assertThat(service.claimDueBatch(1L)).isEmpty();

        clock.advanceSeconds(24 * 60 * 60);
        Optional<BatchClaim> nextDayClaim = service.claimDueBatch(1L);
        assertThat(nextDayClaim).isPresent();
        assertThat(nextDayClaim.get().exerciseIds()).containsExactly(999L);
    }

    @Test
    void listDueCourseIds_keepsCoursesIndependent() {
        service.record(1L, 10L);
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
        service.record(2L, 20L);

        assertThat(service.listDueCourseIds()).containsExactly(1L);
        assertThat(service.claimDueBatch(2L)).isEmpty();
    }

    @Test
    void mergeEntryProcessorReplaysAcrossSerialization() {
        service.record(1L, 10L);
        ContentChangeAccumulator first = hazelcastInstance.<Long, ContentChangeAccumulator>getMap(ContentChangeAccumulatorService.MAP_NAME).get(1L);
        assertThat(first).isNotNull();
        assertThat(first.exerciseIds()).containsExactly(10L);

        service.record(1L, 11L);
        ContentChangeAccumulator second = hazelcastInstance.<Long, ContentChangeAccumulator>getMap(ContentChangeAccumulatorService.MAP_NAME).get(1L);
        assertThat(second).isNotNull();
        assertThat(second.exerciseIds()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void claimBatchNow_doesNotConsumeDailyCap() {
        for (int i = 0; i < DAILY_CAP; i++) {
            service.record(1L, 100L + i);
            clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
            assertThat(service.claimDueBatch(1L)).as("scheduled run #%d", i + 1).isPresent();
        }

        service.record(1L, 200L);
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
        assertThat(service.claimDueBatch(1L)).as("scheduled cap must be exhausted").isEmpty();

        Optional<BatchClaim> manualClaim = service.claimBatchNow(1L);
        assertThat(manualClaim).as("manual flush must bypass daily cap").isPresent();
        assertThat(manualClaim.get().exerciseIds()).containsExactly(200L);

        service.record(1L, 300L);
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
        assertThat(service.claimDueBatch(1L)).as("manual flush must not have freed scheduled quota").isEmpty();
    }

    @Test
    void requeueAfterConcurrentRun_refundsReservationSoRetriesDoNotBurnDailyCap() {
        service.record(1L, 200L);

        // More claim+requeue cycles than the daily cap: each models a scheduler tick that drained the
        // batch but hit a concurrent run, so the reservation must be refunded and the batch stay claimable.
        for (int i = 0; i < DAILY_CAP + 2; i++) {
            clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
            Optional<BatchClaim> claim = service.claimDueBatch(1L);
            assertThat(claim).as("retry tick #%d must still claim the batch", i + 1).isPresent();
            assertThat(claim.get().exerciseIds()).containsExactly(200L);
            service.requeueAfterConcurrentRun(1L, claim.get().exerciseIds());
        }

        // The cap was never consumed by the retries: a full run of DAILY_CAP scheduled orchestrations
        // is still available, and only then is the cap exhausted.
        for (int i = 0; i < DAILY_CAP; i++) {
            clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
            assertThat(service.claimDueBatch(1L)).as("scheduled run #%d after retries", i + 1).isPresent();
            service.record(1L, 300L + i);
        }
        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
        assertThat(service.claimDueBatch(1L)).as("cap exhausted only after DAILY_CAP real runs").isEmpty();
    }

    @Test
    void requeueAfterFailedRun_keepsReservationSoFailedRetriesAreBoundedByDailyCap() {
        service.record(1L, 200L);

        // Each iteration models a scheduler tick that drained the batch but failed before committing
        // anything: the ids are requeued, but the daily reservation is kept so failed retries are
        // bounded by the cap. After DAILY_CAP attempts the batch can no longer be claimed today.
        for (int i = 0; i < DAILY_CAP; i++) {
            clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
            Optional<BatchClaim> claim = service.claimDueBatch(1L);
            assertThat(claim).as("failed retry #%d must still claim the requeued batch", i + 1).isPresent();
            assertThat(claim.get().exerciseIds()).containsExactly(200L);
            service.requeueAfterFailedRun(1L, claim.get().exerciseIds());
        }

        clock.advanceSeconds(DEBOUNCE_WINDOW_SECONDS + 1);
        assertThat(service.claimDueBatch(1L)).as("cap exhausted after DAILY_CAP failed attempts").isEmpty();

        // The changes were not discarded: the requeued id is still buffered and is claimable the next
        // day once the cap resets.
        clock.advanceSeconds(24 * 60 * 60);
        Optional<BatchClaim> nextDayClaim = service.claimDueBatch(1L);
        assertThat(nextDayClaim).as("requeued batch survives to the next day").isPresent();
        assertThat(nextDayClaim.get().exerciseIds()).containsExactly(200L);
    }

    @Test
    void tryClaimLock_secondAcquirerIsRejectedUntilRelease() throws Exception {
        assertThat(service.tryClaimLock(42L)).isTrue();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            // Same JVM, same thread re-entering — Hazelcast IMap.tryLock is reentrant, so this would
            // succeed. Run on a separate thread (via Future#get so the assertion bubbles back to the
            // JUnit thread) to model a second scheduler tick on another node.
            java.util.concurrent.Future<Boolean> future = executor.submit(() -> service.tryClaimLock(42L));
            assertThat(future.get()).as("second acquirer must be rejected while lock is held").isFalse();
        }
        finally {
            executor.shutdownNow();
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

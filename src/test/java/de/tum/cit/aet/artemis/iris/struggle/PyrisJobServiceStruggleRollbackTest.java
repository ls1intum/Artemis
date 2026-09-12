package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DefaultTimeToLiveDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DelegatingDistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

/**
 * Unit tests for the compensation around the struggle single-flight reservation.
 *
 * <p>
 * The reservation writes three things and returns the token only at the end, so a failure in the middle leaves state
 * the caller cannot clean up, and the pair stays blocked for a whole {@code jobTimeout}. These tests inject a failure
 * into the last of the three writes.
 *
 * <p>
 * A unit test rather than an addition to {@link PyrisJobServiceStruggleTest}, because what is under test is the
 * control flow of the compensation. Real {@link LocalMap} instances behind the same
 * {@link DefaultTimeToLiveDistributedMap} wrapper keep the map behaviour honest, while the failing wrapper makes the
 * failure deterministic.
 */
class PyrisJobServiceStruggleRollbackTest {

    private static final long COURSE_ID = 11L;

    private static final long USER_ID = 22L;

    private static final long EXERCISE_ID = 33L;

    private static final String IN_FLIGHT_KEY = USER_ID + ":" + EXERCISE_ID;

    private DistributedMap<String, PyrisJob> jobMap;

    private DistributedMap<String, String> inFlightMap;

    private IllegalStateException refreshFailure;

    // Fails lock() once, which is where refreshStruggleInFlightMarker starts, so it stands in for a provider that
    // breaks after the job is written. The TTL overload of putIfAbsent doubles as the only way to learn the token,
    // which the service mints internally and never returns on the failing path.
    private static final class FailingLockDistributedMap extends DelegatingDistributedMap<String, String> {

        private final RuntimeException failure;

        private boolean lockFailurePending = true;

        private String reservedToken;

        private FailingLockDistributedMap(DistributedMap<String, String> delegate, RuntimeException failure) {
            super(delegate);
            this.failure = failure;
        }

        @Override
        public String putIfAbsent(String key, String value, Duration timeToLive) {
            String existing = super.putIfAbsent(key, value, timeToLive);
            if (existing == null) {
                reservedToken = value;
            }
            return existing;
        }

        @Override
        public void lock(String key) {
            if (lockFailurePending) {
                lockFailurePending = false;
                throw failure;
            }
            super.lock(key);
        }
    }

    @BeforeEach
    void setUp() {
        jobMap = new DefaultTimeToLiveDistributedMap<>(new LocalMap<>(), Duration.ofSeconds(300));
        inFlightMap = new DefaultTimeToLiveDistributedMap<>(new LocalMap<>(), Duration.ofSeconds(300));
        refreshFailure = new IllegalStateException("the distributed store is unavailable");
    }

    // The service over both maps, with the in-flight one wrapped so its lock fails once.
    private ServiceUnderTest serviceOver(DistributedMap<String, PyrisJob> jobs, DistributedMap<String, String> inFlight) {
        var failingInFlight = new FailingLockDistributedMap(inFlight, refreshFailure);
        var distributedDataProvider = mock(DistributedDataProvider.class);
        when(distributedDataProvider.<String, PyrisJob>getExpiringMap(eq("pyris-job-map"), any())).thenReturn(jobs);
        when(distributedDataProvider.<String, String>getExpiringMap(eq("struggle-inflight-map"), any())).thenReturn(failingInFlight);

        var service = new PyrisJobService(distributedDataProvider, new IrisProactiveProperties());
        ReflectionTestUtils.setField(service, "jobTimeout", 300);
        ReflectionTestUtils.setField(service, "serverUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "instanceId", "test-node");
        return new ServiceUnderTest(service, failingInFlight);
    }

    private record ServiceUnderTest(PyrisJobService service, FailingLockDistributedMap inFlight) {
    }

    private static void reserve(PyrisJobService service) {
        service.addStruggleInterventionJobIfNonePending(COURSE_ID, USER_ID, EXERCISE_ID, "decide", null, null, null, null);
    }

    @Test
    void aFailedMarkerRefreshRollsBackBothTheJobAndTheReservation() {
        var underTest = serviceOver(jobMap, inFlightMap);

        // isSameAs, so the compensation must let the original instance through rather than a lookalike.
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> reserve(underTest.service())).isSameAs(refreshFailure);

        assertThat(underTest.inFlight().reservedToken).as("the reservation must have been written before the refresh failed").isNotNull();
        assertThat(underTest.service().getJob(underTest.inFlight().reservedToken)).as("the job written before the failure must be gone").isNull();
        assertThat(inFlightMap.get(IN_FLIGHT_KEY)).as("the reservation must be released rather than left to expire with the pair blocked").isNull();
    }

    @Test
    void aPairIsReservableAgainImmediatelyAfterARolledBackReservation() {
        var underTest = serviceOver(jobMap, inFlightMap);
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> reserve(underTest.service()));

        // The point of the rollback: the next trigger gets a run rather than a marker nobody can release.
        var token = underTest.service().addStruggleInterventionJobIfNonePending(COURSE_ID, USER_ID, EXERCISE_ID, "decide", null, null, null, null);
        try {
            assertThat(token).as("the pair must be reservable again once the failed reservation was undone").isPresent();
            assertThat(underTest.service().getJob(token.orElseThrow())).isNotNull();
        }
        finally {
            token.ifPresent(reserved -> underTest.service().releaseStruggleInFlightJob(reserved, USER_ID, EXERCISE_ID));
        }
    }

    @Test
    void aFailedMarkerCleanupIsSuppressedOntoTheOriginalFailure() {
        var brokenInFlight = new DelegatingDistributedMap<String, String>(inFlightMap) {

            @Override
            public boolean remove(String key, String value) {
                throw new IllegalArgumentException("the marker cleanup failed too");
            }
        };
        var underTest = serviceOver(jobMap, brokenInFlight);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> reserve(underTest.service())).isSameAs(refreshFailure);

        // A cleanup that fails on its own must be reported alongside the cause, not instead of it.
        assertThat(refreshFailure.getSuppressed())
                .anySatisfy(suppressed -> assertThat(suppressed).isInstanceOf(IllegalArgumentException.class).hasMessage("the marker cleanup failed too"));
        assertThat(underTest.service().getJob(underTest.inFlight().reservedToken)).as("a broken marker cleanup must not stop the job from being removed").isNull();
    }

    @Test
    void aFailedJobCleanupStillReleasesTheReservation() {
        // Why the two removals are attempted separately: a provider failing the job removal would otherwise skip
        // the marker removal, and the blocked pair is the more expensive leak. Fold them together and this goes red.
        var brokenJobs = new DelegatingDistributedMap<String, PyrisJob>(jobMap) {

            @Override
            public boolean remove(String key, PyrisJob value) {
                throw new IllegalArgumentException("the job cleanup failed too");
            }
        };
        var underTest = serviceOver(brokenJobs, inFlightMap);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> reserve(underTest.service())).isSameAs(refreshFailure);

        assertThat(inFlightMap.get(IN_FLIGHT_KEY)).as("a broken job cleanup must not leave the pair reserved").isNull();
        assertThat(refreshFailure.getSuppressed())
                .anySatisfy(suppressed -> assertThat(suppressed).isInstanceOf(IllegalArgumentException.class).hasMessage("the job cleanup failed too"));
    }
}

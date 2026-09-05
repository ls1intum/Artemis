package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Tests the in-memory accumulation.
 * <p>
 * The property under test is that nothing is ever lost or double counted: the flush reports the difference to what it
 * reported last time, rather than resetting counters, because a reset would race with concurrent recording.
 */
class FeatureUsageCollectorTest {

    private static final long FEATURE_ID = 42L;

    private FeatureUsageRegistry registry;

    private FeatureUsageCollector collector;

    @BeforeEach
    void init() {
        registry = mock(FeatureUsageRegistry.class);
        collector = newCollector(new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of())));
    }

    @Test
    void shouldAccumulateCallsErrorsAndDurations() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 10);
        collector.recordUsage(FEATURE_ID, Role.STUDENT, true, 30);

        var deltas = collector.drain(today());

        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().callCount()).isEqualTo(2);
        assertThat(deltas.getFirst().errorCount()).isEqualTo(1);
        assertThat(deltas.getFirst().durationSumMs()).isEqualTo(40);
        assertThat(deltas.getFirst().durationMaxMs()).isEqualTo(30);
    }

    @Test
    void shouldKeepBucketsOfDifferentRolesApart() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 1);
        collector.recordUsage(FEATURE_ID, Role.INSTRUCTOR, false, 1);

        assertThat(collector.drain(today())).hasSize(2).extracting(FeatureUsageDelta::callerRole).containsExactlyInAnyOrder(Role.STUDENT, Role.INSTRUCTOR);
    }

    @Test
    void shouldReportOnlyWhatIsNewSinceThePreviousFlush() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);
        assertThat(collector.drain(today()).getFirst().callCount()).isEqualTo(1);

        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);

        var deltas = collector.drain(today());
        assertThat(deltas).hasSize(1);
        // 3 calls happened in total, 1 was already reported
        assertThat(deltas.getFirst().callCount()).isEqualTo(2);
        assertThat(deltas.getFirst().durationSumMs()).isEqualTo(10);
    }

    @Test
    void shouldReportTheRunningMaximumSoTheStoredMaximumIsIdempotent() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 100);
        assertThat(collector.drain(today()).getFirst().durationMaxMs()).isEqualTo(100);

        // a slower call than before does not appear in this interval, but the maximum must still be reported
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);

        assertThat(collector.drain(today()).getFirst().durationMaxMs()).isEqualTo(100);
    }

    @Test
    void shouldReportNothingWhenNoCallHappenedSinceThePreviousFlush() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 1);
        collector.drain(today());

        assertThat(collector.drain(today())).isEmpty();
    }

    @Test
    void shouldDropIdleBucketsOfClosedDaysButKeepTheCurrentOne() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 1);
        collector.drain(today());

        // draining as if the day had rolled over discards the finished bucket, so the map cannot grow over a long uptime
        assertThat(collector.drain(today().plusDays(1))).isEmpty();

        // the bucket is gone, so a new call starts from zero rather than continuing an old watermark
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 1);
        assertThat(collector.drain(today()).getFirst().callCount()).isEqualTo(1);
    }

    /**
     * The counters of one observation are incremented one after another, so a flush can land between them and see the call
     * without its error and its duration. Reporting only what has a new call would then advance the watermarks past that
     * call and skip the bucket next time, losing the failure and the latency for good.
     * <p>
     * That interleaving cannot be produced from the outside, so the state it leaves behind is built here through
     * {@link FeatureUsageCollector#reclaim}: counters that are ahead of their watermarks with no new call behind them.
     */
    @Test
    void shouldReportLateErrorsAndDurationsEvenWhenNoNewCallArrived() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, true, 50);
        collector.drain(today());

        // as if the previous drain had seen the call but not yet the failure and the duration of that same request
        collector.reclaim(new FeatureUsageDelta(FEATURE_ID, today(), Role.STUDENT, 0, 1, 50, 0));

        var deltas = collector.drain(today());
        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().callCount()).isZero();
        assertThat(deltas.getFirst().errorCount()).isEqualTo(1);
        assertThat(deltas.getFirst().durationSumMs()).isEqualTo(50);
    }

    @Test
    void shouldKeepAClosedDayBucketThatStillHasUnreportedErrorsOrDurations() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, true, 50);
        collector.drain(today());
        collector.reclaim(new FeatureUsageDelta(FEATURE_ID, today(), Role.STUDENT, 0, 1, 50, 0));

        // the day is over, but dropping the bucket here would discard the failure and the duration that are still pending
        var deltas = collector.drain(today().plusDays(1));
        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().errorCount()).isEqualTo(1);
    }

    /**
     * The maximum is the fourth counter of an observation and the last one {@code accumulate} updates, so a drain can
     * consume the call, the error and the duration sum of a request and still read the maximum from before that request
     * raised it. It needs a watermark of its own to be noticed: unlike the other three it is a running maximum, so there is
     * no delta to compare.
     */
    @Test
    void shouldReportALateMaximumEvenWhenNoOtherCounterMoved() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 100);
        collector.drain(today());

        // as if the previous drain had taken the additive counters but read the maximum before this request raised it
        collector.reclaim(new FeatureUsageDelta(FEATURE_ID, today(), Role.STUDENT, 0, 0, 0, 0));

        var deltas = collector.drain(today());
        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().callCount()).isZero();
        assertThat(deltas.getFirst().durationMaxMs()).isEqualTo(100);
    }

    @Test
    void shouldKeepAClosedDayBucketWhoseMaximumHasNotBeenReported() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 100);
        collector.drain(today());
        collector.reclaim(new FeatureUsageDelta(FEATURE_ID, today(), Role.STUDENT, 0, 0, 0, 0));

        // the day is over, but the slowest call of that day has not reached the database yet
        var deltas = collector.drain(today().plusDays(1));
        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().durationMaxMs()).isEqualTo(100);
    }

    @Test
    void shouldReportTheMaximumAgainAfterAFailedWrite() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 100);
        var failed = collector.drain(today()).getFirst();

        collector.reclaim(failed);

        var retry = collector.drain(today());
        assertThat(retry).hasSize(1);
        assertThat(retry.getFirst().callCount()).isEqualTo(1);
        assertThat(retry.getFirst().durationMaxMs()).isEqualTo(100);
    }

    /**
     * Everything recorded has to come out of the drains exactly once, whatever the interleaving. Asserted as a conservation
     * law over a concurrent run rather than on one interleaving, because the window between the counter increments of a
     * single observation is a few nanoseconds wide and cannot be hit on purpose.
     */
    @Test
    void shouldConserveEveryCounterWhileFlushingConcurrently() throws InterruptedException {
        int threads = 4;
        int callsPerThread = 20_000;
        var recording = new AtomicBoolean(true);
        var calls = new LongAdder();
        var errors = new LongAdder();
        var durations = new LongAdder();

        var flusher = new Thread(() -> {
            // one last drain after the recorders are done, so nothing is left in the collector
            boolean lastRound = false;
            while (!lastRound) {
                lastRound = !recording.get();
                for (var delta : collector.drain(today())) {
                    calls.add(delta.callCount());
                    errors.add(delta.errorCount());
                    durations.add(delta.durationSumMs());
                }
            }
        });
        flusher.start();

        var recorders = new ArrayList<Thread>();
        for (int thread = 0; thread < threads; thread++) {
            var recorder = new Thread(() -> {
                for (int call = 0; call < callsPerThread; call++) {
                    // every call fails and takes one millisecond, so all three expected totals are exact
                    collector.recordUsage(FEATURE_ID, Role.STUDENT, true, 1);
                }
            });
            recorder.start();
            recorders.add(recorder);
        }
        for (var recorder : recorders) {
            recorder.join();
        }
        recording.set(false);
        flusher.join();

        long expected = (long) threads * callsPerThread;
        assertThat(calls.sum()).isEqualTo(expected);
        assertThat(errors.sum()).isEqualTo(expected);
        assertThat(durations.sum()).isEqualTo(expected);
    }

    @Test
    void shouldRegisterAGitFeatureOnFirstSighting() {
        when(registry.featureId(eq(FeatureKind.GIT), anyString(), anyString())).thenReturn(FEATURE_ID);

        collector.recordUsage(FeatureKind.GIT, "localvc", "push/assignment", Role.ANONYMOUS, false, 12);

        var deltas = collector.drain(today());
        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().featureId()).isEqualTo(FEATURE_ID);
    }

    @Test
    void shouldRecordNothingWhenTheFeatureCouldNotBeRegistered() {
        when(registry.featureId(any(), anyString(), anyString())).thenReturn(null);

        collector.recordUsage(FeatureKind.BACKGROUND, "plagiarism", "continuous-plagiarism-control/text", Role.ANONYMOUS, false, 1);

        assertThat(collector.drain(today())).isEmpty();
    }

    @Test
    void shouldDoNothingWhenTrackingIsDisabled() {
        var disabled = newCollector(new FeatureUsageProperties(false, 400, new FeatureUsageProperties.Digest(false, List.of())));

        disabled.recordUsage(FEATURE_ID, Role.STUDENT, false, 1);
        disabled.recordUsage(FeatureKind.GIT, "localvc", "push/assignment", Role.ANONYMOUS, false, 1);

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.drain(today())).isEmpty();
        verifyNoInteractions(registry);
    }

    @Test
    void shouldNotPropagateAFailureOfTheRegistryIntoTheRequest() {
        when(registry.featureId(any(), anyString(), anyString())).thenThrow(new IllegalStateException("database down"));

        // a usage counter must never be able to break the operation it is measuring
        collector.recordUsage(FeatureKind.GIT, "localvc", "fetch/tests", Role.ANONYMOUS, false, 1);

        assertThat(collector.drain(today())).isEmpty();
    }

    /**
     * The production collector hands every observation to a recording thread, so that nothing about counting a request
     * can lengthen or interfere with it. Recording therefore has to have actually happened by the time a flush reads the
     * counters, and on shutdown the queue has to be applied rather than dropped on top of the interval the design
     * already accepts losing.
     */
    @Test
    void shouldApplyObservationsHandedToTheRecordingThread() {
        var asyncCollector = new FeatureUsageCollector(enabledProperties(), contextReturningRegistry());

        asyncCollector.recordUsage(FEATURE_ID, Role.STUDENT, true, 40);
        asyncCollector.recordUsage(FEATURE_ID, Role.STUDENT, false, 60);

        // the point of the handoff: the caller returned before the work was done, so it has to be waited for here
        assertThat(asyncCollector.applyPendingObservations()).isTrue();

        var deltas = asyncCollector.drain(today());
        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().callCount()).isEqualTo(2);
        assertThat(deltas.getFirst().errorCount()).isEqualTo(1);
        assertThat(deltas.getFirst().durationSumMs()).isEqualTo(100);
        assertThat(deltas.getFirst().durationMaxMs()).isEqualTo(60);
    }

    /**
     * Recording is deferred, so reading the clock where an observation is applied rather than where it happened would
     * attribute a request made just before midnight to the following day.
     */
    @Test
    void shouldAttributeAnObservationToTheDayItHappenedOn() {
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 1);

        // draining as if the day had rolled over still finds the bucket under today, which only holds if the day
        // travelled with the observation instead of being read at accumulation time
        var deltas = collector.drain(today().plusDays(1));

        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().usageDay()).isEqualTo(today());
    }

    @Test
    void shouldNotPropagateAFailureOfTheRecordingThreadIntoTheCaller() {
        var rejecting = new FeatureUsageCollector(enabledProperties(), contextReturningRegistry(), runnable -> {
            throw new RejectedExecutionException("the recorder is saturated");
        });

        // a counter must never surface to the operation it measures, not even when the queue is gone
        assertThatCode(() -> rejecting.recordUsage(FEATURE_ID, Role.STUDENT, false, 1)).doesNotThrowAnyException();
        assertThat(rejecting.consumeDiscardedObservationCount()).isEqualTo(1);
        // consumed, so the next flush does not report the same drop again
        assertThat(rejecting.consumeDiscardedObservationCount()).isZero();
    }

    /**
     * A flush drops a bucket whose day is over and which saw nothing new. If that removal can interleave with an
     * observation being applied to the same bucket, the increments land on an object no longer in the map and are gone
     * for good.
     * <p>
     * Driven as a conservation law under a drainer that is continuously eligible to remove: draining as if the day had
     * already rolled over makes every bucket a closed one, so the removal path runs against live accumulation on every
     * pass. Nothing recorded may go missing whatever the interleaving.
     */
    @Test
    void shouldNotLoseObservationsToTheRemovalOfAClosedDayBucket() throws InterruptedException {
        int observations = 20_000;
        var asyncCollector = new FeatureUsageCollector(enabledProperties(), contextReturningRegistry());
        var applied = new LongAdder();
        var recording = new AtomicBoolean(true);

        var flusher = new Thread(() -> {
            boolean lastRound = false;
            while (!lastRound) {
                lastRound = !recording.get();
                // "tomorrow", so every bucket counts as a closed day and is a candidate for removal
                for (var delta : asyncCollector.drain(today().plusDays(1))) {
                    applied.add(delta.callCount());
                }
            }
        });
        flusher.start();

        for (int i = 0; i < observations; i++) {
            asyncCollector.recordUsage(FEATURE_ID, Role.STUDENT, false, 1);
        }
        assertThat(asyncCollector.applyPendingObservations()).isTrue();
        recording.set(false);
        flusher.join();

        assertThat(applied.sum()).isEqualTo(observations);
    }

    private static FeatureUsageProperties enabledProperties() {
        return new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of()));
    }

    private ApplicationContext contextReturningRegistry() {
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(FeatureUsageRegistry.class)).thenReturn(registry);
        return applicationContext;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /**
     * The collector resolves the registry from the context on first use, so constructing it here has to supply a context that
     * hands back the mocked registry.
     */
    private FeatureUsageCollector newCollector(FeatureUsageProperties properties) {
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(FeatureUsageRegistry.class)).thenReturn(registry);
        // Recording is asynchronous in production. Applied inline here so a test can drain straight after recording:
        // the concurrency between recording and draining is what the conservation test covers on purpose, and every
        // other test here is about the watermark protocol, not the handoff.
        return new FeatureUsageCollector(properties, applicationContext, Runnable::run);
    }

}

package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        collector = new FeatureUsageCollector(registry, new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of())));
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
        var disabled = new FeatureUsageCollector(registry, new FeatureUsageProperties(false, 400, new FeatureUsageProperties.Digest(false, List.of())));

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

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}

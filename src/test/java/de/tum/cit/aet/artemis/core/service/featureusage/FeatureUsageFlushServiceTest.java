package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.domain.FeatureUsageDaily;
import de.tum.cit.aet.artemis.core.repository.FeatureUsageDailyRepository;
import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Tests writing the accumulated counters.
 * <p>
 * Every node flushes, so the interesting cases are the two races: an existing bucket must be added to rather than
 * overwritten, and two nodes creating the same bucket at the same time must end up with both their counts.
 */
class FeatureUsageFlushServiceTest {

    private static final long FEATURE_ID = 7L;

    private FeatureUsageCollector collector;

    private FeatureUsageDailyRepository repository;

    private FeatureUsageFlushService service;

    @BeforeEach
    void init() {
        collector = newCollector(new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of())));
        repository = mock(FeatureUsageDailyRepository.class);
        service = new FeatureUsageFlushService(collector, repository);
    }

    @Test
    void shouldAddToAnExistingBucketWithoutInserting() {
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(1);
        collector.recordUsage(FEATURE_ID, Role.STUDENT, true, 25);

        service.flush();

        verify(repository).addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldInsertTheBucketWhenItDoesNotExistYet() {
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(0);
        collector.recordUsage(FEATURE_ID, Role.INSTRUCTOR, true, 25);

        service.flush();

        ArgumentCaptor<FeatureUsageDaily> captor = ArgumentCaptor.forClass(FeatureUsageDaily.class);
        verify(repository).save(captor.capture());
        FeatureUsageDaily saved = captor.getValue();
        assertThat(saved.getFeatureId()).isEqualTo(FEATURE_ID);
        assertThat(saved.getCallerRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(saved.getCallCount()).isEqualTo(1);
        assertThat(saved.getErrorCount()).isEqualTo(1);
        assertThat(saved.getDurationSumMs()).isEqualTo(25);
        assertThat(saved.getDurationMaxMs()).isEqualTo(25);
    }

    @Test
    void shouldAddToTheOtherNodesBucketWhenItLosesTheInsertRace() {
        // first update finds nothing, the insert then collides with another node's insert, and the retry has to land
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(0, 1);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);

        service.flush();

        verify(repository, times(2)).addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt());
    }

    /**
     * A failed write must be retried rather than dropped. drain() advances each bucket's watermark as it hands the delta
     * out, so without giving a failed delta back its counts sit behind the watermark and are never reported again - the
     * calls in that bucket are lost silently, which is the worst way for a metrics pipeline to fail.
     */
    @Test
    void shouldRetryABucketWhoseWriteFailed() {
        // the update finds no row, the insert then fails for a reason that is not the insert race
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(0);
        when(repository.save(any())).thenThrow(new RuntimeException("connection reset"));
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);

        service.flush();

        // the next flush succeeds and must still carry the counts of the failed one
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(1);
        service.flush();

        // Twice: the failed attempt and the retry. The retry is what matters - it must still carry the lost count.
        ArgumentCaptor<Long> callCount = ArgumentCaptor.forClass(Long.class);
        verify(repository, times(2)).addUsage(anyLong(), any(), any(), callCount.capture(), anyLong(), anyLong(), anyInt());
        assertThat(callCount.getAllValues()).containsExactly(1L, 1L);
    }

    /**
     * The retry must not double-report once the write has succeeded: reclaiming happens only on failure.
     */
    @Test
    void shouldNotRetryABucketWhoseWriteSucceeded() {
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(1);
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);

        service.flush();
        service.flush();

        verify(repository, times(1)).addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void shouldNotReportTheSameCountsTwiceAcrossFlushes() {
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(1);
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);

        service.flush();
        service.flush();

        // the second run has nothing new to write, so it must not touch the database at all
        verify(repository, times(1)).addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void shouldKeepGoingWhenOneBucketFails() {
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenThrow(new DataIntegrityViolationException("constraint")).thenReturn(1);
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);
        collector.recordUsage(FEATURE_ID, Role.INSTRUCTOR, false, 5);

        service.flush();

        verify(repository, times(2)).addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void shouldDoNothingWhenTrackingIsDisabled() {
        var disabledCollector = newCollector(new FeatureUsageProperties(false, 400, new FeatureUsageProperties.Digest(false, List.of())));

        new FeatureUsageFlushService(disabledCollector, repository).flush();

        verifyNoInteractions(repository);
    }

    @Test
    void shouldFlushOnShutdownSoAPlannedRestartDoesNotDiscardTheInterval() {
        when(repository.addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(1);
        collector.recordUsage(FEATURE_ID, Role.STUDENT, false, 5);

        service.flushOnShutdown();

        verify(repository).addUsage(anyLong(), any(), any(), anyLong(), anyLong(), anyLong(), anyInt());
    }

    /**
     * The collector resolves the registry from the context on first use, so constructing it here has to supply a context that
     * hands back the mocked registry.
     */
    private FeatureUsageCollector newCollector(FeatureUsageProperties properties) {
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(FeatureUsageRegistry.class)).thenReturn(mock(FeatureUsageRegistry.class));
        // Applied inline: every test here is about the write and retry protocol, not the handoff to the recording
        // thread, which FeatureUsageCollectorTest covers with a real one.
        return new FeatureUsageCollector(properties, applicationContext, Runnable::run);
    }

}

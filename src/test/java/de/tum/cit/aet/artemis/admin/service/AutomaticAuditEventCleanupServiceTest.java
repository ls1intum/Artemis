package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.admin.repository.ApplicationAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.SecurityAuditEventRepository;

/**
 * Unit tests for {@link AutomaticAuditEventCleanupService}: that each of the three logs is pruned with its own cutoff,
 * and that the batch loop terminates both when a log runs dry and when the per-run cap is reached. Uses mocked
 * repositories so no database is needed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutomaticAuditEventCleanupServiceTest {

    private static final int GENERAL_RETENTION_DAYS = 365;

    private static final int SECURITY_RETENTION_DAYS = 1825;

    private static final int APPLICATION_RETENTION_DAYS = 1825;

    @Mock
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    @Mock
    private SecurityAuditEventRepository securityAuditEventRepository;

    @Mock
    private ApplicationAuditEventRepository applicationAuditEventRepository;

    private AutomaticAuditEventCleanupService service;

    @BeforeEach
    void setUp() {
        service = new AutomaticAuditEventCleanupService(persistenceAuditEventRepository, securityAuditEventRepository, applicationAuditEventRepository);
        ReflectionTestUtils.setField(service, "generalRetentionPeriodInDays", GENERAL_RETENTION_DAYS);
        ReflectionTestUtils.setField(service, "securityRetentionPeriodInDays", SECURITY_RETENTION_DAYS);
        ReflectionTestUtils.setField(service, "applicationRetentionPeriodInDays", APPLICATION_RETENTION_DAYS);
        // Default: every log is empty, so a test only has to stub the log it cares about.
        when(persistenceAuditEventRepository.findExpiredIds(any(), any())).thenReturn(List.of());
        when(securityAuditEventRepository.findExpiredIds(any(), any())).thenReturn(List.of());
        when(applicationAuditEventRepository.findExpiredIds(any(), any())).thenReturn(List.of());
    }

    @Test
    void nothingIsDeletedWhenNoLogHasExpiredEvents() {
        service.cleanup();

        verify(persistenceAuditEventRepository, never()).deleteAllById(any());
        verify(securityAuditEventRepository, never()).deleteAllById(any());
        verify(applicationAuditEventRepository, never()).deleteAllById(any());
    }

    @Test
    void eachLogIsPrunedWithItsOwnCutoffAndOnlyItsOwnRowsAreDeleted() {
        when(persistenceAuditEventRepository.findExpiredIds(any(), any())).thenReturn(List.of(1L, 2L), List.of());
        when(securityAuditEventRepository.findExpiredIds(any(), any())).thenReturn(List.of(3L), List.of());
        when(applicationAuditEventRepository.findExpiredIds(any(), any())).thenReturn(List.of(4L, 5L, 6L), List.of());

        Instant before = Instant.now();
        service.cleanup();
        Instant after = Instant.now();

        Instant generalCutoff = captureCutoff(persistenceAuditEventRepository);
        Instant securityCutoff = captureCutoff(securityAuditEventRepository);
        Instant applicationCutoff = captureCutoff(applicationAuditEventRepository);

        assertCutoff(generalCutoff, GENERAL_RETENTION_DAYS, before, after);
        assertCutoff(securityCutoff, SECURITY_RETENTION_DAYS, before, after);
        assertCutoff(applicationCutoff, APPLICATION_RETENTION_DAYS, before, after);

        // The long-retention logs must reach further back than the general log, otherwise the split buys nothing.
        assertThat(securityCutoff).isBefore(generalCutoff);
        assertThat(applicationCutoff).isBefore(generalCutoff);

        verify(persistenceAuditEventRepository).deleteAllById(List.of(1L, 2L));
        verify(securityAuditEventRepository).deleteAllById(List.of(3L));
        verify(applicationAuditEventRepository).deleteAllById(List.of(4L, 5L, 6L));
    }

    @Test
    void pruningOneLogDoesNotTouchTheOthers() {
        when(securityAuditEventRepository.findExpiredIds(any(), any())).thenReturn(List.of(42L), List.of());

        service.cleanup();

        verify(securityAuditEventRepository).deleteAllById(List.of(42L));
        verify(persistenceAuditEventRepository, never()).deleteAllById(any());
        verify(applicationAuditEventRepository, never()).deleteAllById(any());
    }

    @Test
    void stopsAfterTheConfiguredNumberOfBatchesWhenABacklogNeverDrains() {
        int maxBatches = (int) ReflectionTestUtils.getField(AutomaticAuditEventCleanupService.class, "MAX_BATCHES_PER_RUN");
        // The general log never runs dry, so the loop must stop at the cap rather than spinning forever.
        when(persistenceAuditEventRepository.findExpiredIds(any(), any())).thenReturn(List.of(1L));

        service.cleanup();

        verify(persistenceAuditEventRepository, times(maxBatches)).deleteAllById(List.of(1L));
    }

    private Instant captureCutoff(PersistenceAuditEventRepository repository) {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).findExpiredIds(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    private Instant captureCutoff(SecurityAuditEventRepository repository) {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).findExpiredIds(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    private Instant captureCutoff(ApplicationAuditEventRepository repository) {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).findExpiredIds(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    private void assertCutoff(Instant actual, int retentionDays, Instant before, Instant after) {
        // The service calls Instant.now() internally, so the cutoff must fall within [before - N days, after - N days].
        assertThat(actual).isBetween(before.minus(retentionDays, ChronoUnit.DAYS), after.minus(retentionDays, ChronoUnit.DAYS));
    }
}

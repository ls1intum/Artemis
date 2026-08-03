package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.admin.config.AuditEventRetentionProperties;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;

/**
 * Tests the retention scheduling itself: which cutoff each schedule applies, that the two schedules cannot delete each
 * other's rows, and that a single run is bounded. Getting a cutoff wrong deletes records that were meant to be kept, and
 * an unbounded run would try to delete a backlog of years in one transaction.
 */
class AutomaticAuditEventCleanupServiceTest {

    private static final int GENERAL_RETENTION_DAYS = 365;

    private static final int APPLICATION_RETENTION_DAYS = 1825;

    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    private AutomaticAuditEventCleanupService service;

    @BeforeEach
    void init() {
        persistenceAuditEventRepository = mock(PersistenceAuditEventRepository.class);
        service = new AutomaticAuditEventCleanupService(persistenceAuditEventRepository, new AuditEventRetentionProperties(GENERAL_RETENTION_DAYS, APPLICATION_RETENTION_DAYS));
    }

    @Test
    void eachScheduleAppliesItsOwnCutoffToItsOwnEventTypes() {
        when(persistenceAuditEventRepository.findExpiredIdsOfTypes(any(), any(), any())).thenReturn(List.of());
        when(persistenceAuditEventRepository.findExpiredIdsExcludingTypes(any(), any(), any())).thenReturn(List.of());
        Instant before = Instant.now();

        service.cleanup();

        Instant after = Instant.now();
        ArgumentCaptor<Instant> generalCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(persistenceAuditEventRepository).findExpiredIdsOfTypes(generalCutoff.capture(), eq(AuditEventConstants.GENERAL_EVENT_TYPES), any());
        assertThat(generalCutoff.getValue()).isBetween(before.minus(GENERAL_RETENTION_DAYS, ChronoUnit.DAYS), after.minus(GENERAL_RETENTION_DAYS, ChronoUnit.DAYS));

        ArgumentCaptor<Instant> applicationCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(persistenceAuditEventRepository).findExpiredIdsExcludingTypes(applicationCutoff.capture(), eq(AuditEventConstants.GENERAL_EVENT_TYPES), any());
        assertThat(applicationCutoff.getValue()).isBetween(before.minus(APPLICATION_RETENTION_DAYS, ChronoUnit.DAYS), after.minus(APPLICATION_RETENTION_DAYS, ChronoUnit.DAYS));

        // The longer retention has to produce the earlier cutoff, i.e. reach further into the past. If the two were the
        // other way round, the rare records would be deleted sooner than the bulk login record.
        assertThat(applicationCutoff.getValue()).isBefore(generalCutoff.getValue());
    }

    @Test
    void nothingIsDeletedWhenNothingHasExpired() {
        when(persistenceAuditEventRepository.findExpiredIdsOfTypes(any(), any(), any())).thenReturn(List.of());
        when(persistenceAuditEventRepository.findExpiredIdsExcludingTypes(any(), any(), any())).thenReturn(List.of());

        service.cleanup();

        verify(persistenceAuditEventRepository, never()).deleteAllById(anyList());
    }

    @Test
    void expiredEventsAreDeletedInBatchesUntilTheLogIsDrained() {
        List<Long> fullBatch = idsOfSize(5_000);
        when(persistenceAuditEventRepository.findExpiredIdsOfTypes(any(), any(), any())).thenReturn(fullBatch, fullBatch, List.of());
        when(persistenceAuditEventRepository.findExpiredIdsExcludingTypes(any(), any(), any())).thenReturn(List.of());

        service.cleanup();

        // Two batches of rows, then an empty result ends the loop rather than a further delete.
        verify(persistenceAuditEventRepository, times(2)).deleteAllById(fullBatch);
    }

    @Test
    void aSingleRunIsBoundedSoALargeBacklogIsDrainedOverSeveralRuns() {
        // A finder that never runs dry: without the per-run cap this would loop until the whole backlog was deleted.
        List<Long> fullBatch = idsOfSize(5_000);
        when(persistenceAuditEventRepository.findExpiredIdsOfTypes(any(), any(), any())).thenReturn(fullBatch);
        when(persistenceAuditEventRepository.findExpiredIdsExcludingTypes(any(), any(), any())).thenReturn(List.of());

        service.cleanup();

        verify(persistenceAuditEventRepository, times(200)).deleteAllById(fullBatch);
    }

    @Test
    void batchesAreBoundedInSize() {
        when(persistenceAuditEventRepository.findExpiredIdsOfTypes(any(), any(), any())).thenReturn(List.of());
        when(persistenceAuditEventRepository.findExpiredIdsExcludingTypes(any(), any(), any())).thenReturn(List.of());

        service.cleanup();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(persistenceAuditEventRepository).findExpiredIdsOfTypes(any(), any(), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 5_000));
    }

    @Test
    void aFailingScheduleDoesNotStopTheOtherOne() {
        // Both schedules share one nightly trigger, so an exception escaping the first would mean the second never runs.
        // A persistent fault - a lock timeout on one old row, say - would then let the other log grow unbounded.
        List<Long> fullBatch = idsOfSize(5_000);
        when(persistenceAuditEventRepository.findExpiredIdsOfTypes(any(), any(), any())).thenThrow(new DataAccessResourceFailureException("lock timeout"));
        when(persistenceAuditEventRepository.findExpiredIdsExcludingTypes(any(), any(), any())).thenReturn(fullBatch, List.of());

        service.cleanup();

        verify(persistenceAuditEventRepository).deleteAllById(fullBatch);
    }

    @Test
    void theGeneralTypesUsedByBothSchedulesAreTheSameSet() {
        // The two schedules partition the table by this one set: any divergence would either double-delete or, worse,
        // leave rows that no schedule ever looks at.
        when(persistenceAuditEventRepository.findExpiredIdsOfTypes(any(), any(), any())).thenReturn(List.of());
        when(persistenceAuditEventRepository.findExpiredIdsExcludingTypes(any(), any(), any())).thenReturn(List.of());

        service.cleanup();

        ArgumentCaptor<Set<String>> included = ArgumentCaptor.captor();
        ArgumentCaptor<Set<String>> excluded = ArgumentCaptor.captor();
        verify(persistenceAuditEventRepository).findExpiredIdsOfTypes(any(), included.capture(), any());
        verify(persistenceAuditEventRepository).findExpiredIdsExcludingTypes(any(), excluded.capture(), any());
        assertThat(included.getValue()).isEqualTo(excluded.getValue()).isEqualTo(AuditEventConstants.GENERAL_EVENT_TYPES);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, -365 })
    void aNonPositiveRetentionPeriodIsRejectedRatherThanApplied(int invalidRetention) {
        // Nothing gates this job, and its deletions are irreversible: 0 would make almost every existing event eligible,
        // and a negative value would put the cutoff in the future and delete records written minutes ago. Validation has
        // to reject the configuration at startup rather than let a typo run.
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            assertThat(validator.validate(new AuditEventRetentionProperties(invalidRetention, APPLICATION_RETENTION_DAYS))).isNotEmpty();
            assertThat(validator.validate(new AuditEventRetentionProperties(GENERAL_RETENTION_DAYS, invalidRetention))).isNotEmpty();
            assertThat(validator.validate(new AuditEventRetentionProperties(GENERAL_RETENTION_DAYS, APPLICATION_RETENTION_DAYS))).isEmpty();
        }
    }

    private static List<Long> idsOfSize(int size) {
        return java.util.stream.LongStream.range(0, size).boxed().toList();
    }
}

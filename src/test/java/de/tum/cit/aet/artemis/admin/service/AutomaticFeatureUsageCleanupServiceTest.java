package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.repository.FeatureUsageDailyRepository;

/**
 * Tests the nightly pruning. The cutoff is the whole point: getting it wrong deletes buckets that were meant to be kept,
 * and the deletion is irreversible.
 */
class AutomaticFeatureUsageCleanupServiceTest {

    private static final int RETENTION_DAYS = 400;

    private FeatureUsageDailyRepository repository;

    private AutomaticFeatureUsageCleanupService service;

    @BeforeEach
    void init() {
        repository = mock(FeatureUsageDailyRepository.class);
        service = new AutomaticFeatureUsageCleanupService(repository, new FeatureUsageProperties(true, RETENTION_DAYS, new FeatureUsageProperties.Digest(false, List.of())));
    }

    @Test
    void shouldDeleteEverythingOlderThanTheRetentionPeriod() {
        when(repository.deleteAllOlderThan(any())).thenReturn(0);

        service.cleanup();

        ArgumentCaptor<LocalDate> cutoff = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).deleteAllOlderThan(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(LocalDate.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS));
    }

    @Test
    void shouldNotPropagateADatabaseFailure() {
        // the job runs unattended at night; a failure must be logged and retried tomorrow, not escalate
        when(repository.deleteAllOlderThan(any())).thenThrow(new DataAccessResourceFailureException("database down"));

        service.cleanup();

        verify(repository).deleteAllOlderThan(any());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1 })
    void shouldRejectARetentionPeriodThatWouldDeleteRecentBuckets(int invalidRetentionPeriod) {
        // 0 would expire every bucket and a negative value would move the cutoff into the future, so the binding is
        // validated and a configuration typo has to fail startup rather than be applied
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            assertThat(validator.validate(new FeatureUsageProperties(true, invalidRetentionPeriod, new FeatureUsageProperties.Digest(false, List.of())))).isNotEmpty();
        }
    }

    @Test
    void shouldAcceptAPositiveRetentionPeriod() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(new FeatureUsageProperties(true, RETENTION_DAYS, new FeatureUsageProperties.Digest(false, List.of())))).isEmpty();
        }
    }
}

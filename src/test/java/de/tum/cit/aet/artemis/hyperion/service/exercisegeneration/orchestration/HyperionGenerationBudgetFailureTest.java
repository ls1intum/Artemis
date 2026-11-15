package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.exception.TooManyRequestsAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;

class HyperionGenerationBudgetFailureTest {

    private final LLMTokenUsageTraceTestRepository repository = mock(LLMTokenUsageTraceTestRepository.class);

    @ParameterizedTest
    @CsvSource({ "-1,0,0", "0,-1,0", "0,0,-1" })
    void negativeBudgetsFailConfiguration(long user, long course, long global) {
        assertThatThrownBy(
                () -> new HyperionGenerationBudgetService(repository, new LocalDataProviderService(), Duration.ofDays(1), user, course, global, 100, 1, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not be negative");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "PT0S", "PT-1S" })
    void enabledBudgetsRequirePositiveWindow(String window) {
        assertThatThrownBy(() -> new HyperionGenerationBudgetService(repository, new LocalDataProviderService(), window == null ? null : Duration.parse(window), 100, 0, 0, 100, 1,
                Duration.ofMinutes(30))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("token-budget-window must be positive");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "PT0S", "PT-1S", "PT3H" })
    void invalidJobDurationUsesFallbackWhileDisabledBudgetsNeedNoReservations(String jobDuration) {
        var service = new HyperionGenerationBudgetService(repository, new LocalDataProviderService(), null, 0, 0, 0, 100, 1,
                jobDuration == null ? null : Duration.parse(jobDuration));
        service.init();
        assertThat(service.reserveGenerationBudget(null, null, 100).id()).isNull();
        assertThat(service.refreshReservation(null)).isTrue();
        assertThat(service.retainReservationForBudgetWindow(null)).isTrue();
        assertThatCode(() -> service.releaseReservation(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.recordPersistedUsage(null, 100)).doesNotThrowAnyException();
    }

    @Test
    void missingReservationStorageAllowsLegacyAdmissionWithoutInventingReservation() {
        var service = new HyperionGenerationBudgetService(repository, Duration.ofDays(1), 100, 100, 100);
        assertThat(service.reserveGenerationBudget(null, null, 100).id()).isNull();
        assertThat(service.refreshReservation("unknown")).isTrue();
        assertThat(service.retainReservationForBudgetWindow("unknown")).isTrue();
        assertThatCode(() -> service.recordPersistedUsage("unknown", 10)).doesNotThrowAnyException();
        assertThatCode(() -> service.releaseReservation("unknown")).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void reservationStorageAndLockFailuresFailAdmissionClosedButCleanupRemainsBestEffort() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        DistributedMap<String, Object> reservations = mock(DistributedMap.class);
        DistributedLock lock = mock(DistributedLock.class);
        when(provider.getExpiringMap(anyString(), any())).thenReturn((DistributedMap) reservations);
        when(provider.getLock(anyString())).thenReturn(lock);
        when(reservations.values()).thenReturn(List.of());
        var service = new HyperionGenerationBudgetService(repository, provider, Duration.ofDays(1), 100, 100, 100, 100, 1, Duration.ofMinutes(30));
        service.init();
        assertBusy(() -> service.reserveGenerationBudget(1L, 2L, 100));
        when(lock.tryLock(any())).thenThrow(new IllegalStateException("lock service unavailable"));
        assertBusy(() -> service.reserveGenerationBudget(1L, 2L, 100));
        org.mockito.Mockito.doReturn(true).when(lock).tryLock(any());
        when(reservations.values()).thenThrow(new IllegalStateException("map unavailable"));
        assertBusy(() -> service.reserveGenerationBudget(1L, 2L, 100));
        doThrow(new IllegalStateException("unlock unavailable")).when(lock).unlock();
        assertBusy(() -> service.reserveGenerationBudget(1L, 2L, 100));
        doThrow(new IllegalStateException("remove unavailable")).when(reservations).remove("id");
        assertThatCode(() -> service.releaseReservation("id")).doesNotThrowAnyException();
        doThrow(new IllegalStateException("lock unavailable")).when(reservations).lock("id");
        assertThat(service.refreshReservation("id")).isFalse();
        assertThat(service.retainReservationForBudgetWindow("id")).isFalse();
        assertThatCode(() -> service.recordPersistedUsage("id", 100)).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void missingReservationsAndUnlockFailuresHaveConservativeResults() {
        DistributedDataProvider provider = mock(DistributedDataProvider.class);
        DistributedMap<String, Object> reservations = mock(DistributedMap.class);
        when(provider.getExpiringMap(anyString(), any())).thenReturn((DistributedMap) reservations);
        var service = new HyperionGenerationBudgetService(repository, provider, Duration.ofDays(1), 100, 0, 0, 100, 1, Duration.ofMinutes(30));
        service.init();
        assertThat(service.refreshReservation("missing")).isFalse();
        assertThat(service.retainReservationForBudgetWindow("missing")).isFalse();
        assertThatCode(() -> service.recordPersistedUsage("missing", 1)).doesNotThrowAnyException();
        assertThatCode(() -> service.recordPersistedUsage("missing", 0)).doesNotThrowAnyException();
        doThrow(new IllegalStateException("unlock unavailable")).when(reservations).unlock("missing");
        assertThatCode(() -> service.recordPersistedUsage("missing", 1)).doesNotThrowAnyException();
    }

    private void assertBusy(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(TooManyRequestsAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("generationAdmissionBusy"));
    }
}

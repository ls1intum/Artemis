package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.exception.TooManyRequestsAlertException;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;

class HyperionGenerationBudgetServiceTest {

    private final LLMTokenUsageTraceTestRepository repository = mock(LLMTokenUsageTraceTestRepository.class);

    @Test
    void configurationRejectsABudgetBelowThePerJobMaximum() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HyperionGenerationBudgetService(repository, mock(HazelcastInstance.class), Duration.ofHours(24), 99, 0, 0, 100, Duration.ofMinutes(35)))
                .withMessageContaining("admission-max-tokens-per-user").withMessageContaining("max-tokens-per-job");
    }

    @Test
    void assertWithinBudgets_whenAllLimitsDisabled_doesNotQueryRepository() {
        HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, Duration.ofHours(24), 0, 0, 0);

        service.assertWithinBudgets(1L, 2L);

        verify(repository, never()).sumTokensSinceForUser(any(), any(), any(), any());
        verify(repository, never()).sumTokensSinceForCourse(any(), any(), any(), any());
        verify(repository, never()).sumTokensSince(any(), any(), any());
    }

    @Test
    void assertWithinBudgets_whenUserLimitReached_throwsBudgetExceeded() {
        when(repository.sumTokensSinceForUser(eq(LLMServiceType.HYPERION), eq(GenerationJobService.GENERATION_PIPELINE_ID), eq(1L), any(ZonedDateTime.class))).thenReturn(100L);
        HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, Duration.ofHours(24), 100, 0, 0);

        assertThatExceptionOfType(TooManyRequestsAlertException.class).isThrownBy(() -> service.assertWithinBudgets(1L, 2L))
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorKey()).isEqualTo("generationTokenBudgetExceeded"));
    }

    @Test
    void assertWithinBudgets_whenBelowConfiguredLimits_allowsStart() {
        when(repository.sumTokensSinceForUser(any(), any(), any(), any())).thenReturn(99L);
        when(repository.sumTokensSinceForCourse(any(), any(), any(), any())).thenReturn(199L);
        when(repository.sumTokensSince(any(), any(), any())).thenReturn(299L);
        HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, Duration.ofHours(24), 100, 200, 300);

        service.assertWithinBudgets(1L, 2L);
    }

    @Test
    void reserveGenerationBudget_countsInFlightReservationsAndReleasesThem() {
        Config config = new Config();
        config.setClusterName("hyperion-budget-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        try {
            when(repository.sumTokensSinceForUser(eq(LLMServiceType.HYPERION), eq(GenerationJobService.GENERATION_PIPELINE_ID), eq(1L), any(ZonedDateTime.class))).thenReturn(0L);
            HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 150, 0, 0, 100,
                    Duration.ofMinutes(35));
            service.init();

            HyperionGenerationBudgetService.BudgetReservation reservation = service.reserveGenerationBudget(1L, 2L);

            assertThat(reservation.id()).isNotBlank();
            assertThatExceptionOfType(TooManyRequestsAlertException.class).isThrownBy(() -> service.reserveGenerationBudget(1L, 2L));

            service.releaseReservation(reservation.id());
            assertThat(service.reserveGenerationBudget(1L, 2L).id()).isNotBlank();
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void reserveGenerationBudget_allowsExactBudgetBoundaryButRejectsNextReservation() {
        Config config = new Config();
        config.setClusterName("hyperion-budget-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        try {
            when(repository.sumTokensSinceForUser(eq(LLMServiceType.HYPERION), eq(GenerationJobService.GENERATION_PIPELINE_ID), eq(1L), any(ZonedDateTime.class))).thenReturn(0L);
            HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 100, 0, 0, 100,
                    Duration.ofMinutes(35));
            service.init();

            assertThat(service.reserveGenerationBudget(1L, 2L).id()).isNotBlank();
            assertThatExceptionOfType(TooManyRequestsAlertException.class).isThrownBy(() -> service.reserveGenerationBudget(1L, 2L));
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

}

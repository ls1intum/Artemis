package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

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

        assertThatCode(() -> service.assertWithinBudgets(1L, 2L)).doesNotThrowAnyException();
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

            HyperionGenerationBudgetService.BudgetReservation reservation = service.reserveGenerationBudget(1L, 2L, 100);

            assertThat(reservation.id()).isNotBlank();
            assertThatExceptionOfType(TooManyRequestsAlertException.class).isThrownBy(() -> service.reserveGenerationBudget(1L, 2L, 100));

            service.releaseReservation(reservation.id());
            assertThat(service.reserveGenerationBudget(1L, 2L, 100).id()).isNotBlank();
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void persistedUsageReplacesTheSameTokensInTheInFlightReservation() {
        Config config = new Config();
        config.setClusterName("hyperion-budget-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        try {
            when(repository.sumTokensSinceForUser(eq(LLMServiceType.HYPERION), eq(GenerationJobService.GENERATION_PIPELINE_ID), eq(1L), any(ZonedDateTime.class))).thenReturn(0L,
                    100L);
            HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 350, 0, 0, 300,
                    Duration.ofMinutes(35));
            service.init();
            HyperionGenerationBudgetService.BudgetReservation running = service.reserveGenerationBudget(1L, 2L, 300);

            service.recordPersistedUsage(running.id(), 100);

            assertThat(service.reserveGenerationBudget(1L, 2L, 1).id()).isNotBlank();
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void aFullyConsumedReservationIsClampedToZeroRatherThanRemoved() {
        // The owning worker's heartbeat reads presence as proof it still owns the job, so removing a spent reservation would report lost ownership mid-save. The user budget
        // equals one job's ceiling, so the final admission of the same size succeeds only if the exhausted reservation contributes exactly zero.
        Config config = new Config();
        config.setClusterName("hyperion-budget-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        try {
            when(repository.sumTokensSinceForUser(eq(LLMServiceType.HYPERION), eq(GenerationJobService.GENERATION_PIPELINE_ID), eq(1L), any(ZonedDateTime.class))).thenReturn(0L);
            HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 300, 0, 0, 300,
                    Duration.ofMinutes(35));
            service.init();
            HyperionGenerationBudgetService.BudgetReservation exhausted = service.reserveGenerationBudget(1L, 2L, 300);

            service.recordPersistedUsage(exhausted.id(), 300);

            IMap<String, ?> reservations = hazelcastInstance.getMap("hyperion-generation-token-budget-reservations");
            assertThat(reservations.containsKey(exhausted.id())).as("the spent reservation is still there for the owning worker's heartbeat to find").isTrue();
            assertThat(service.refreshReservation(exhausted.id())).isTrue();
            assertThat(service.reserveGenerationBudget(1L, 2L, 300).id()).isNotBlank();
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    /**
     * Two admissions racing at the last free slot must not both win. The reservation is serialised by a lock on one shared map key, so two services sharing a member exercise
     * exactly that mutual exclusion; a real multi-member cluster would test Hazelcast's distribution rather than this budget arithmetic.
     */
    @Test
    void reserveGenerationBudget_concurrentAdmissionsAdmitExactlyOneJobAtTheBudgetBoundary() throws Exception {
        Config config = new Config();
        config.setClusterName("hyperion-budget-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            when(repository.sumTokensSinceForUser(eq(LLMServiceType.HYPERION), eq(GenerationJobService.GENERATION_PIPELINE_ID), eq(1L), any(ZonedDateTime.class))).thenReturn(0L);
            HyperionGenerationBudgetService firstService = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 100, 0, 0, 100,
                    Duration.ofMinutes(35));
            HyperionGenerationBudgetService secondService = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 100, 0, 0, 100,
                    Duration.ofMinutes(35));
            firstService.init();
            secondService.init();
            CyclicBarrier startTogether = new CyclicBarrier(2);

            var attempts = executor.invokeAll(List.of(() -> reserveAtBarrier(firstService, startTogether), () -> reserveAtBarrier(secondService, startTogether)));

            assertThat(List.of(attempts.get(0).get(), attempts.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        finally {
            executor.shutdownNow();
            hazelcastInstance.shutdown();
        }
    }

    private static boolean reserveAtBarrier(HyperionGenerationBudgetService service, CyclicBarrier startTogether) throws Exception {
        startTogether.await();
        try {
            return service.reserveGenerationBudget(1L, 2L, 100).id() != null;
        }
        catch (TooManyRequestsAlertException ignored) {
            return false;
        }
    }

    @Test
    void reservationRemainsValidForAtLeastTheBudgetWindow() {
        Config config = new Config();
        config.setClusterName("hyperion-budget-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        try {
            HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 100, 0, 0, 100,
                    Duration.ofMinutes(30));
            service.init();

            HyperionGenerationBudgetService.BudgetReservation reservation = service.reserveGenerationBudget(1L, 2L, 100);
            IMap<String, ?> reservations = hazelcastInstance.getMap("hyperion-generation-token-budget-reservations");

            assertThat(reservations.getEntryView(reservation.id()).getTtl()).isGreaterThanOrEqualTo(Duration.ofHours(24).minusSeconds(1).toMillis());
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void refreshAndRetentionReportMissingReservations() {
        Config config = new Config();
        config.setClusterName("hyperion-budget-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        try {
            HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 100, 0, 0, 100,
                    Duration.ofMinutes(30));
            service.init();

            assertThat(service.refreshReservation("missing")).isFalse();
            assertThat(service.retainReservationForBudgetWindow("missing")).isFalse();
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void reservationIsSizedToWhatTheJobMaySpendRatherThanTheFleetWorstCase() {
        // Reserving the fleet-wide worst case for every job throttles a course that only drafts small exercises at the same job count as one running the largest jobs.
        Config config = new Config();
        config.setClusterName("hyperion-budget-service-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        try {
            when(repository.sumTokensSinceForUser(eq(LLMServiceType.HYPERION), eq(GenerationJobService.GENERATION_PIPELINE_ID), eq(1L), any(ZonedDateTime.class))).thenReturn(0L);
            HyperionGenerationBudgetService service = new HyperionGenerationBudgetService(repository, hazelcastInstance, Duration.ofHours(24), 300, 0, 0, 100,
                    Duration.ofMinutes(35));
            service.init();

            // Worst-case sizing admits three jobs against the 300-token user budget; sizing to a 20-token job admits fifteen, and the budget still bounds the sixteenth.
            for (int admitted = 0; admitted < 15; admitted++) {
                assertThat(service.reserveGenerationBudget(1L, 2L, 20).id()).isNotBlank();
            }
            assertThatExceptionOfType(TooManyRequestsAlertException.class).isThrownBy(() -> service.reserveGenerationBudget(1L, 2L, 20));
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    void aRequestedReservationAboveTheCeilingIsCappedAtTheValidatedCeiling() {
        // The startup check proved only that the ceiling fits every enabled budget, so nothing may reserve past it even if a caller asks.
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

            assertThat(service.reserveGenerationBudget(1L, 2L, Long.MAX_VALUE).id()).isNotBlank();
            // Capped at 100, so a second 100-token reservation exceeds the 150-token budget rather than the first having consumed everything at Long.MAX_VALUE.
            assertThatExceptionOfType(TooManyRequestsAlertException.class).isThrownBy(() -> service.reserveGenerationBudget(1L, 2L, 100));
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }
}

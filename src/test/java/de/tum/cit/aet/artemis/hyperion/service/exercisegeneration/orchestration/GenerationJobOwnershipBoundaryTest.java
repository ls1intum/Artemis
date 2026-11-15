package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationTokenUsageService.GenerationUsage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class GenerationJobOwnershipBoundaryTest {

    private final LocalDataProviderService provider = spy(new LocalDataProviderService());

    private final HyperionGenerationBudgetService budget = mock(HyperionGenerationBudgetService.class);

    private final GenerationTokenUsageService usage = mock(GenerationTokenUsageService.class);

    private DistributedMap<String, GenerationJobService.JobInfo> slots;

    private GenerationJobService service;

    private User owner;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        slots = spy(provider.<String, GenerationJobService.JobInfo>getMap("hyperion-exercise-generation-jobs"));
        doReturn(slots).when(provider).getMap("hyperion-exercise-generation-jobs");
        service = new GenerationJobService(provider, event -> {
        }, usage, budget, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run);
        service.init();
        owner = new User();
        owner.setLogin("owner");
        exercise = new ProgrammingExercise();
        exercise.setId(7L);
        exercise.setTitle("Exercise");
    }

    private String start() {
        return service.startJob(owner, exercise, "Generate", GenerationMode.GENERATE, "reservation");
    }

    @Test
    void missingWrongAndForeignNodeJobsCannotHeartbeatOrEnterPersistence() {
        assertThat(service.isActiveJob(7L, "missing")).isFalse();
        assertThat(service.isOwnedActiveJob(7L, "missing")).isFalse();
        assertThat(service.heartbeat(7L, "missing")).isFalse();
        assertThat(service.enterNonCancellablePhase(7L, "missing")).isFalse();
        String jobId = start();
        assertThat(service.isActiveJob(7L, "wrong")).isFalse();
        assertThat(service.isOwnedActiveJob(7L, "wrong")).isFalse();
        assertThat(service.heartbeat(7L, "wrong")).isFalse();
        assertThat(service.enterNonCancellablePhase(7L, "wrong")).isFalse();
        var job = slots.get("7");
        slots.put("7", new GenerationJobService.JobInfo(jobId, "owner", 7L, job.startedAt(), job.deadlineAt(), "another-node", job.lastHeartbeatAt(), true, "reservation",
                GenerationMode.GENERATE, "Exercise", null));
        assertThat(service.isOwnedActiveJob(7L, jobId)).isFalse();
        assertThat(service.heartbeat(7L, jobId)).isFalse();
        assertThat(service.enterNonCancellablePhase(7L, jobId)).isFalse();
        assertThat(service.isActiveJob(7L, jobId)).isTrue();
        assertThat(slots.get("7").cancellable()).isTrue();
    }

    @Test
    void lostBudgetReservationStopsHeartbeatAndLegacyOwnerCanStillBeClaimed() {
        String jobId = start();
        var original = slots.get("7");
        assertThat(service.heartbeat(7L, jobId)).isFalse();
        assertThat(slots.get("7")).isEqualTo(original);
        when(budget.refreshReservation("reservation")).thenReturn(true);
        assertThat(service.heartbeat(7L, jobId)).isTrue();
        var current = slots.get("7");
        slots.put("7", new GenerationJobService.JobInfo(jobId, "owner", 7L, current.startedAt(), current.deadlineAt(), null, current.lastHeartbeatAt(), true, "reservation",
                GenerationMode.GENERATE, "Exercise", null));
        assertThat(service.isOwnedActiveJob(7L, jobId)).isTrue();
        assertThat(service.enterNonCancellablePhase(7L, jobId)).isTrue();
        assertThat(slots.get("7").cancellable()).isFalse();
        assertThat(service.requestCancellation(7L, jobId, owner)).isFalse();
    }

    @Test
    void cancellationRequiresCurrentOwnerAndTerminalSystemRequestIsNotAcknowledgedAgain() {
        assertThat(service.requestCancellation(7L, "missing", owner)).isFalse();
        String jobId = start();
        User other = new User();
        other.setLogin("other");
        assertThat(service.requestCancellation(7L, jobId, other)).isFalse();
        assertThat(service.requestCancellation(7L, "wrong", owner)).isFalse();
        assertThat(service.isCancelled(jobId)).isFalse();
        assertThat(service.requestCancellation(7L, jobId, owner)).isTrue();
        assertThat(service.requestCancellation(7L, jobId, owner)).isTrue();
        assertThat(service.requestSystemCancellation(7L, jobId)).isFalse();
        assertThat(service.enterNonCancellablePhase(7L, jobId)).isFalse();
        assertThat(service.getStatus(owner, exercise)).hasValueSatisfying(status -> {
            assertThat(status.running()).isFalse();
            assertThat(status.events()).singleElement().satisfies(event -> assertThat(event.type()).isEqualTo(ExerciseGenerationEventDTO.Type.CANCELLED));
        });
    }

    @Test
    void failedCompareAndSetCannotAcknowledgeCancellationOrPersistence() {
        String jobId = start();
        doReturn(false).when(slots).replace(eq("7"), any(), any());
        assertThat(service.requestCancellation(7L, jobId, owner)).isFalse();
        assertThat(service.enterNonCancellablePhase(7L, jobId)).isFalse();
        assertThat(slots.get("7").cancellable()).isTrue();
        doThrow(new IllegalStateException("store unavailable")).when(slots).replace(eq("7"), any(), any());
        assertThatThrownBy(() -> service.requestCancellation(7L, jobId, owner)).isInstanceOf(ServiceUnavailableAlertException.class)
                .hasMessage("Cancellation could not be confirmed. Please retry.");
        assertThat(service.isCancelled(jobId)).isFalse();
    }

    @Test
    void onlyCurrentJobsReduceBudgetReservationsAfterDurableUsageAndFailuresStayConservative() {
        String jobId = start();
        GenerationUsage recorded = new GenerationUsage("model", 100, 1, 50, 1, "pipeline", "request", 20L, 0.1f, true);
        when(usage.trackChatResponseTokenUsage(any(), any(), anyString(), any(), any())).thenAnswer(invocation -> {
            Consumer<GenerationUsage> sink = invocation.getArgument(4);
            sink.accept(recorded);
            return true;
        });
        java.util.List<GenerationUsage> live = new java.util.ArrayList<>();
        service.tokenUsageSink(1L, 7L, 2L, jobId, live::add).accept(null);
        assertThat(live).containsExactly(recorded);
        verify(budget).recordPersistedUsage("reservation", 150);
        assertThat(service.getStatus(owner, exercise).orElseThrow().usage().inputTokens()).isEqualTo(100);
        doThrow(new IllegalStateException("budget store unavailable")).when(budget).recordPersistedUsage("reservation", 150);
        assertThatCode(() -> service.tokenUsageSink(1L, 7L, 2L, jobId).accept(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.tokenUsageSink(1L, null, 2L, "missing").accept(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.tokenUsageSink(1L, 99L, 2L, "missing").accept(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.tokenUsageSink(1L, 7L, 2L, "old").accept(null)).doesNotThrowAnyException();
        verify(budget, times(2)).recordPersistedUsage("reservation", 150);
    }
}

package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationInputDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmissionService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@ExtendWith(MockitoExtension.class)
class GenerationVariantServiceTest {

    @Mock
    private GenerationAdmissionService admission;

    @Mock
    private GenerationVariantDraftService drafts;

    @Mock
    private GenerationExternalMutationService mutations;

    @Mock
    private GenerationJobService jobs;

    @Mock
    private GenerationVariantPlacementService placement;

    private GenerationVariantService service;

    private User user;

    private ProgrammingExercise source;

    private ProgrammingExercise target;

    private VariantGenerationRequestDTO request;

    private GenerationAdmissionService.ReservedRun reservation;

    private GenerationStartedEvent event;

    @BeforeEach
    void setUp() {
        service = new GenerationVariantService(admission, drafts, mutations, jobs, placement);
        user = new User();
        user.setLogin("instructor");
        source = new ProgrammingExercise();
        source.setId(1L);
        target = new ProgrammingExercise();
        target.setId(2L);
        request = new VariantGenerationRequestDTO(null, "A library", null, null, new VariantPlacementDTO(VariantPlacementDTO.PlacementType.STANDALONE, null, null));
        var settings = new HyperionGenerationSettings("draft", "Draft", 20, Duration.ofMinutes(12), 600_000L, true, "CONTINUOUS", 128_000, null, false, false);
        reservation = new GenerationAdmissionService.ReservedRun(user, source, new ExerciseGenerationRequestDTO(GenerationMode.ADAPT, "A library", null), settings, "A library",
                null, new ExerciseGenerationInputDTO("A library", List.of(), 1L), "budget");
        event = new GenerationStartedEvent("job", user, target, "A library", GenerationMode.ADAPT, null, null, null, "budget", null, settings,
                new GenerationVariantPreparation(1L, "copy-token", request));
    }

    private void admit() {
        when(admission.reserveVariant(user, 1L, request)).thenReturn(reservation);
        when(mutations.claimParticipationSlot(1L)).thenReturn("copy-token");
    }

    private void reserveJob() {
        when(jobs.prepareVariantJob(eq(user), eq(target), eq("A library"), eq("budget"), eq(reservation.settings()), eq(reservation.input()), any())).thenReturn(event);
    }

    @Test
    void dispatchHappensAfterDraftCommitAndUsesTheCommonAdaptationJob() {
        admit();
        reserveJob();
        AtomicBoolean committed = new AtomicBoolean();
        when(drafts.prepare(eq(1L), eq(request), any())).thenAnswer(invocation -> {
            Function<ProgrammingExercise, GenerationStartedEvent> reserve = invocation.getArgument(2);
            GenerationStartedEvent start = reserve.apply(target);
            verify(jobs, never()).dispatchPreparedJob(any());
            committed.set(true);
            return start;
        });
        when(jobs.dispatchPreparedJob(event)).thenAnswer(invocation -> {
            assertThat(committed).isTrue();
            return true;
        });

        var result = service.start(user, 1L, request);

        assertThat(result.jobId()).isEqualTo("job");
        assertThat(result.sourceExerciseId()).isEqualTo(1L);
        assertThat(result.exerciseId()).isEqualTo(2L);
        verify(admission, never()).release(any());
        verify(mutations, never()).clearParticipationSlot(eq(1L), any());
    }

    @Test
    void failedCommitReleasesTheExactDestinationSourceAndBudgetWithoutDispatch() {
        admit();
        reserveJob();
        when(drafts.prepare(eq(1L), eq(request), any())).thenAnswer(invocation -> {
            Function<ProgrammingExercise, GenerationStartedEvent> reserve = invocation.getArgument(2);
            reserve.apply(target);
            throw new IllegalStateException("commit failed");
        });

        assertThatThrownBy(() -> service.start(user, 1L, request)).hasMessage("commit failed");

        verify(jobs).clearJob(2L, "job");
        verify(jobs, never()).dispatchPreparedJob(any());
        verify(mutations).clearParticipationSlot(1L, "copy-token");
        verify(admission).release(reservation);
    }

    @Test
    void dispatchRejectionReturnsTheFailedJobIdentityAndReleasesUnusedResources() {
        admit();
        when(drafts.prepare(eq(1L), eq(request), any())).thenReturn(event);
        when(jobs.dispatchPreparedJob(event)).thenReturn(false);

        assertThat(service.start(user, 1L, request).exerciseId()).isEqualTo(2L);

        verify(mutations).clearParticipationSlot(1L, "copy-token");
        verify(admission).release(reservation);
    }

    @Test
    void sourceConflictDoesNotCreateADestinationAndReleasesBudget() {
        when(admission.reserveVariant(user, 1L, request)).thenReturn(reservation);
        when(mutations.claimParticipationSlot(1L)).thenThrow(new IllegalStateException("source busy"));

        assertThatThrownBy(() -> service.start(user, 1L, request)).hasMessage("source busy");

        verify(drafts, never()).prepare(anyLong(), any(), any());
        verify(admission).release(reservation);
    }

    @Test
    void copyingUsesTheInitiatorAndRestoresTheExecutorSecurityContextOnFailure() {
        var original = SecurityContextHolder.getContext();
        var previous = SecurityContextHolder.createEmptyContext();
        previous.setAuthentication(SecurityUtils.makeAuthorizationObject("previous"));
        SecurityContextHolder.setContext(previous);
        try {
            when(jobs.isOwnedActiveJob(2L, "job")).thenReturn(true);
            doAnswer(invocation -> {
                assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("instructor");
                throw new IllegalStateException("copy failed");
            }).when(drafts).complete(1L, 2L);

            assertThatThrownBy(() -> service.prepareInfrastructure(event)).hasMessage("copy failed");

            assertThat(SecurityContextHolder.getContext()).isSameAs(previous);
            verify(mutations).clearParticipationSlot(1L, "copy-token");
        }
        finally {
            SecurityContextHolder.setContext(original);
        }
    }

    @Test
    void lostDestinationOwnershipNeverStartsCopying() {
        assertThatThrownBy(() -> service.prepareInfrastructure(event)).hasMessageContaining("reservation was lost");

        verify(drafts, never()).complete(1L, 2L);
        verify(mutations).clearParticipationSlot(1L, "copy-token");
    }

    @Test
    void invalidPlacementReleasesBudgetBeforeAnySourceReservation() {
        when(admission.reserveVariant(user, 1L, request)).thenReturn(reservation);
        doThrow(new IllegalArgumentException("foreign group")).when(placement).validate(source, request);

        assertThatThrownBy(() -> service.start(user, 1L, request)).hasMessage("foreign group");

        verify(mutations, never()).claimParticipationSlot(1L);
        verify(admission).release(reservation);
    }
}

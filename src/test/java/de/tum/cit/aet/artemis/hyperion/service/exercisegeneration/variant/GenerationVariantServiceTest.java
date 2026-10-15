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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationInputDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmissionService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
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
    private GenerationVariantPreparation.SourceSnapshot sourceSnapshot;

    @Mock
    private GenerationJobService jobs;

    @Mock
    private GenerationVariantPlacementService placement;

    @Mock
    private AuthorizationCheckService authCheck;

    private GenerationLiveSaveService liveSaves;

    private GenerationVariantService service;

    private User user;

    private ProgrammingExercise source;

    private ProgrammingExercise target;

    private VariantGenerationRequestDTO request;

    private GenerationAdmissionService.ReservedRun reservation;

    private GenerationStartedEvent event;

    @BeforeEach
    void setUp() {
        liveSaves = new GenerationLiveSaveService(new LocalDataProviderService(), true);
        service = new GenerationVariantService(admission, drafts, jobs, placement, liveSaves, authCheck);
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
                new GenerationVariantPreparation(1L, sourceSnapshot, request));
    }

    private void admit() {
        when(admission.reserveVariant(user, 1L, request)).thenReturn(reservation);
        when(drafts.captureSource(1L)).thenReturn(sourceSnapshot);
    }

    private void reserveJob() {
        when(jobs.prepareVariantJob(eq(user), eq(target), eq("A library"), eq("budget"), eq(reservation.settings()), eq(reservation.input()), any())).thenReturn(event);
    }

    @Test
    void sourceReaderCoversAdmissionMetadataAndDraftCommitButEndsBeforeDispatch() throws Exception {
        reserveJob();
        try (var writer = Executors.newSingleThreadExecutor()) {
            Runnable assertExcluded = () -> {
                try {
                    writer.submit(() -> assertThatThrownBy(() -> liveSaves.claim(1L)).isInstanceOf(ConflictException.class)).get(10, TimeUnit.SECONDS);
                }
                catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            };
            when(admission.reserveVariant(user, 1L, request)).thenAnswer(invocation -> {
                assertExcluded.run();
                return reservation;
            });
            when(drafts.captureSource(1L)).thenAnswer(invocation -> {
                assertExcluded.run();
                return sourceSnapshot;
            });
            when(drafts.prepare(eq(1L), eq(request), any())).thenAnswer(invocation -> {
                assertExcluded.run();
                Function<ProgrammingExercise, GenerationStartedEvent> reserve = invocation.getArgument(2);
                return reserve.apply(target);
            });
            when(jobs.dispatchPreparedJob(event)).thenAnswer(invocation -> {
                assertThat(liveSaves.getSlotInfo(1L)).isEmpty();
                writer.submit(() -> {
                    String token = liveSaves.claim(1L);
                    liveSaves.release(1L, token);
                }).get(10, TimeUnit.SECONDS);
                return true;
            });

            assertThat(service.start(user, 1L, request).jobId()).isEqualTo("job");

            verify(authCheck).checkHasAtLeastRoleForExerciseElseThrow(de.tum.cit.aet.artemis.core.security.Role.EDITOR, source, user);
            assertThat(liveSaves.getSlotInfo(1L)).isEmpty();
        }
    }

    @Test
    void sourceReaderPreventsTemporarySaveAndCompensationFromPassingBothHeadChecks() throws Exception {
        when(jobs.isOwnedActiveJob(2L, "job")).thenReturn(true);
        var sourceHead = new AtomicReference<>("baseline");
        var copiedHead = new AtomicReference<String>();
        var writerDenied = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(sourceHead.get()).isEqualTo("baseline");
            return null;
        }).when(drafts).verifySource(1L, sourceSnapshot);
        try (var writer = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                var attempted = new CountDownLatch(1);
                var copied = new CountDownLatch(1);
                var future = writer.submit(() -> {
                    String token;
                    try {
                        token = liveSaves.claim(1L);
                    }
                    catch (ConflictException blocked) {
                        writerDenied.set(true);
                        attempted.countDown();
                        return null;
                    }
                    try {
                        sourceHead.set("temporary-save");
                        attempted.countDown();
                        assertThat(copied.await(10, TimeUnit.SECONDS)).isTrue();
                        sourceHead.set("baseline");
                    }
                    finally {
                        liveSaves.release(1L, token);
                    }
                    return null;
                });
                try {
                    assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
                    copiedHead.set(sourceHead.get());
                }
                finally {
                    copied.countDown();
                }
                future.get(10, TimeUnit.SECONDS);
                return null;
            }).when(drafts).complete(1L, 2L);

            service.prepareInfrastructure(event);

            assertThat(copiedHead.get()).isEqualTo("baseline");
            assertThat(writerDenied).isTrue();
            verify(drafts, org.mockito.Mockito.times(2)).verifySource(1L, sourceSnapshot);
            assertThat(liveSaves.getSlotInfo(1L)).isEmpty();
        }
    }

    @Test
    void dispatchHappensAfterDraftCommitAndUsesTheCommonAdaptationJob() {
        admit();
        reserveJob();
        AtomicBoolean committed = new AtomicBoolean();
        when(drafts.prepare(eq(1L), eq(request), any())).thenAnswer(invocation -> {
            Function<ProgrammingExercise, GenerationStartedEvent> reserve = invocation.getArgument(2);
            GenerationStartedEvent start = reserve.apply(target);
            verify(drafts).verifySource(1L, sourceSnapshot);
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

        verify(admission).release(reservation);
    }

    @Test
    void dispatchRejectionReturnsTheFailedJobIdentityAndReleasesUnusedResources() {
        admit();
        when(drafts.prepare(eq(1L), eq(request), any())).thenReturn(event);
        when(jobs.dispatchPreparedJob(event)).thenReturn(false);

        assertThat(service.start(user, 1L, request).exerciseId()).isEqualTo(2L);

        verify(admission).release(reservation);
    }

    @Test
    void sourceConflictDoesNotCreateADestinationAndReleasesBudget() {
        when(admission.reserveVariant(user, 1L, request)).thenReturn(reservation);
        when(drafts.captureSource(1L)).thenThrow(new IllegalStateException("source changed"));

        assertThatThrownBy(() -> service.start(user, 1L, request)).hasMessage("source changed");

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

        }
        finally {
            SecurityContextHolder.setContext(original);
        }
    }

    @Test
    void lostDestinationOwnershipNeverStartsCopying() {
        assertThatThrownBy(() -> service.prepareInfrastructure(event)).hasMessageContaining("reservation was lost");

        verify(drafts, never()).complete(1L, 2L);

    }

    @Test
    void invalidPlacementReleasesBudgetBeforeSourceCapture() {
        when(admission.reserveVariant(user, 1L, request)).thenReturn(reservation);
        doThrow(new IllegalArgumentException("foreign group")).when(placement).validate(source, request);

        assertThatThrownBy(() -> service.start(user, 1L, request)).hasMessage("foreign group");

        verify(drafts, never()).captureSource(1L);
        verify(admission).release(reservation);
    }

    @Test
    void changedSourceBeforeDraftCommitClearsReservationAndNeverDispatchesTheWorker() {
        admit();
        reserveJob();
        when(drafts.prepare(eq(1L), eq(request), any())).thenAnswer(invocation -> {
            Function<ProgrammingExercise, GenerationStartedEvent> reserve = invocation.getArgument(2);
            return reserve.apply(target);
        });
        doThrow(new IllegalStateException("source changed")).when(drafts).verifySource(1L, sourceSnapshot);

        assertThatThrownBy(() -> service.start(user, 1L, request)).hasMessage("source changed");

        verify(jobs, never()).dispatchPreparedJob(any());
        verify(jobs).clearJob(2L, "job");
        verify(admission).release(reservation);
    }

    @Test
    void changedSourceBeforeCopyNeverWritesDestinationRepositories() {
        when(jobs.isOwnedActiveJob(2L, "job")).thenReturn(true);
        doThrow(new IllegalStateException("source changed")).when(drafts).verifySource(1L, sourceSnapshot);

        assertThatThrownBy(() -> service.prepareInfrastructure(event)).hasMessage("source changed");

        verify(drafts, never()).complete(1L, 2L);
    }

    @Test
    void sourceIsCheckedAgainAfterCopyBeforeTheWorkerCanProceed() {
        when(jobs.isOwnedActiveJob(2L, "job")).thenReturn(true);
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("source changed during copy")).when(drafts).verifySource(1L, sourceSnapshot);

        assertThatThrownBy(() -> service.prepareInfrastructure(event)).hasMessage("source changed during copy");

        verify(drafts).complete(1L, 2L);
        verify(drafts, org.mockito.Mockito.times(2)).verifySource(1L, sourceSnapshot);
    }

}

package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationArtifactCompleteness;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class GenerationJobCleanupTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedGenerationAdmissionRetriesExactCleanupWithoutSubmittingAWorker(boolean lostWriteResponse) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var events = new ArrayList<Object>();
        var jobs = new GenerationJobService(provider, events::add, mock(GenerationTokenUsageService.class),
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService.class));
        jobs.init();
        var failure = new IllegalStateException("acquisition response lost");
        if (lostWriteResponse) {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                doThrow(new IllegalStateException("cleanup unavailable")).doCallRealMethod().when(slots).lock("42");
                throw failure;
            }).doCallRealMethod().when(slots).putIfAbsent(eq("42"), any(JobInfo.class));
        }
        else {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                doThrow(new IllegalStateException("cleanup unavailable")).doCallRealMethod().when(slots).lock("42");
                throw failure;
            }).doCallRealMethod().when(slots).unlock("42");
        }
        assertThatThrownBy(() -> jobs.startJob(user(), exercise(), "brief", GenerationMode.GENERATE)).isSameAs(failure);
        assertThat(slots.get("42")).isNotNull();
        assertThat(events).noneMatch(GenerationStartedEvent.class::isInstance);
        jobs.retryPendingCleanup();
        assertThat(slots.get("42")).isNull();
        assertThat(jobs.startJob(user(), exercise(), "new brief", GenerationMode.GENERATE)).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rejectedDispatchRestoresPriorReplayAfterAReleaseFailure(boolean partialReplayRestore) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var files = spy(provider.<String, GenerationJobService.JobFileChangeIndex>getExpiringMap(GenerationJobReplayStore.FILE_CHANGE_MAP_NAME, Duration.ofHours(4)));
        doReturn(files).when(provider).getExpiringMap(GenerationJobReplayStore.FILE_CHANGE_MAP_NAME, Duration.ofHours(4));
        var jobs = new GenerationJobService(provider, _ -> {
        }, mock(GenerationTokenUsageService.class),
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService.class));
        jobs.init();
        String previousJob = jobs.startJob(user(), exercise(), "first", GenerationMode.GENERATE);
        var file = ExerciseGenerationFileChangeDTO.of("solution/Stack.java", "write", 1);
        jobs.recordFileChange(42, previousJob, file);
        var artifacts = new ExerciseGenerationRetainedArtifactsDTO(previousJob, ExerciseGenerationArtifactCompleteness.COMPLETE, "previous statement", null, List.of());
        jobs.retainUnsavedArtifacts(42, previousJob, user().getLogin(), artifacts);
        jobs.clearJob(42, previousJob);
        var previous = jobs.getStatus(user(), exercise()).orElseThrow();
        var failure = new IllegalStateException("dispatch rejected");
        var rejecting = new GenerationJobService(provider, event -> {
            if (event instanceof GenerationStartedEvent) {
                if (partialReplayRestore) {
                    doThrow(new IllegalStateException("replay restoration unavailable")).doCallRealMethod().when(files).put(eq("42"),
                            any(GenerationJobService.JobFileChangeIndex.class), any(Duration.class));
                }
                else {
                    doThrow(new IllegalStateException("rollback unavailable")).doCallRealMethod().when(slots).lock("42");
                }
                throw failure;
            }
        }, mock(GenerationTokenUsageService.class),
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService.class));
        rejecting.init();
        assertThatThrownBy(() -> rejecting.startJob(user(), exercise(), "second", GenerationMode.GENERATE)).isSameAs(failure);
        assertThat(slots.get("42")).isNotNull();
        rejecting.retryPendingCleanup();
        assertThat(slots.get("42")).isNull();
        assertThat(rejecting.getStatus(user(), exercise())).contains(previous);
        assertThat(rejecting.getRetainedArtifacts(user(), exercise())).contains(artifacts);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void publicationFailureDoesNotRollBackAnAdvancedWorkerOrRecoveryGuard(boolean recoveryGuard) {
        var provider = new LocalDataProviderService();
        var ownership = new GenerationWriteOwnershipTestFixture().service;
        var reference = new AtomicReference<GenerationJobService>();
        var failure = new IllegalStateException("publication failed after dispatch");
        var file = ExerciseGenerationFileChangeDTO.of("solution/Stack.java", "write", 1);
        var jobs = new GenerationJobService(provider, event -> {
            if (event instanceof GenerationStartedEvent started) {
                var current = reference.get();
                current.enterNonCancellablePhase(42, started.jobId());
                current.recordFileChange(42, started.jobId(), file);
                current.retainUnsavedArtifacts(42, started.jobId(), user().getLogin(),
                        new ExerciseGenerationRetainedArtifactsDTO(started.jobId(), ExerciseGenerationArtifactCompleteness.COMPLETE, "candidate statement", null, List.of()));
                if (recoveryGuard) {
                    current.retainIncompleteGenerationSlot(42, started.jobId());
                }
                throw failure;
            }
        }, mock(GenerationTokenUsageService.class), ownership);
        reference.set(jobs);
        jobs.init();
        assertThatThrownBy(() -> jobs.startJob(user(), exercise(), "brief", GenerationMode.GENERATE)).isSameAs(failure);
        var slots = provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME);
        JobInfo reservation = slots.get("42");
        if (recoveryGuard) {
            assertThat(reservation).isNull();
            assertThat(ownership.isGenerationActive(42)).isTrue();
        }
        else {
            assertThat(reservation).isNotNull();
            assertThat(reservation.cancellable()).isFalse();
        }
        var status = jobs.getStatus(user(), exercise());
        assertThat(status).hasValueSatisfying(candidate -> assertThat(candidate.fileChanges()).containsExactly(file));
        var artifacts = jobs.getRetainedArtifacts(user(), exercise());
        assertThat(artifacts).hasValueSatisfying(candidate -> assertThat(candidate.problemStatement()).isEqualTo("candidate statement"));
        jobs.retryPendingCleanup();
        assertThat(slots.get("42")).isEqualTo(reservation);
        assertThat(jobs.getStatus(user(), exercise())).isEqualTo(status);
        assertThat(jobs.getRetainedArtifacts(user(), exercise())).isEqualTo(artifacts);
    }

    @Test
    void initializationUnlockFailureRetainsTheRollbackSnapshotUntilPriorCandidateIsRestored() {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var events = new ArrayList<Object>();
        var jobs = new GenerationJobService(provider, events::add, mock(GenerationTokenUsageService.class),
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService.class));
        jobs.init();
        String previousJob = jobs.startJob(user(), exercise(), "first", GenerationMode.GENERATE);
        var file = ExerciseGenerationFileChangeDTO.of("solution/Stack.java", "write", 1);
        jobs.recordFileChange(42, previousJob, file);
        var artifacts = new ExerciseGenerationRetainedArtifactsDTO(previousJob, ExerciseGenerationArtifactCompleteness.COMPLETE, "previous statement", null, List.of());
        jobs.retainUnsavedArtifacts(42, previousJob, user().getLogin(), artifacts);
        jobs.clearJob(42, previousJob);
        var previous = jobs.getStatus(user(), exercise());
        events.clear();
        var failure = new IllegalStateException("initialization unlock response lost");
        var unlocks = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (unlocks.incrementAndGet() == 2) {
                doThrow(new IllegalStateException("rollback unavailable")).doCallRealMethod().when(slots).lock("42");
                throw failure;
            }
            return null;
        }).when(slots).unlock("42");
        assertThatThrownBy(() -> jobs.startJob(user(), exercise(), "second", GenerationMode.GENERATE)).isSameAs(failure);
        assertThat(events).noneMatch(GenerationStartedEvent.class::isInstance);
        assertThat(slots.get("42")).isNotNull();
        jobs.retryPendingCleanup();
        assertThat(slots.get("42")).isNull();
        assertThat(jobs.getStatus(user(), exercise())).isEqualTo(previous);
        assertThat(jobs.getRetainedArtifacts(user(), exercise())).contains(artifacts);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void partialSaveRetainsDatabaseGuardWhileFinishedActivityIsCleared(boolean lostConversionResponse) {
        var provider = new LocalDataProviderService();
        var ownership = new GenerationWriteOwnershipTestFixture().service;
        var jobs = new GenerationJobService(provider, _ -> {
        }, mock(GenerationTokenUsageService.class), ownership);
        jobs.init();
        String job = jobs.startJob(user(), exercise(), "brief", GenerationMode.GENERATE);
        assertThat(jobs.enterNonCancellablePhase(42, job)).isTrue();
        if (lostConversionResponse) {
            doAnswer(call -> {
                call.callRealMethod();
                throw new IllegalStateException("retention acknowledgement lost");
            }).when(ownership).retainSaveRecovery(42, job);
            assertThatThrownBy(() -> jobs.retainIncompleteGenerationSlot(42, job)).hasMessage("retention acknowledgement lost");
        }
        else {
            jobs.retainIncompleteGenerationSlot(42, job);
        }
        jobs.retryPendingCleanup();
        assertThat(jobs.hasActiveJob(42)).isFalse();
        assertThat(ownership.isGenerationActive(42)).isTrue();
        assertThatThrownBy(() -> jobs.claimSeedSlot(42)).isInstanceOf(de.tum.cit.aet.artemis.core.exception.ConflictException.class);
        assertThat(jobs.recoverWedgedSlot(42, "wrong-token")).isFalse();
        assertThat(jobs.recoverWedgedSlot(42, job)).isTrue();
    }

    @Test
    void partialSaveRecoveryRetryCannotConvertOrReleaseAReplacementJob() {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var jobs = new GenerationJobService(provider, _ -> {
        }, mock(GenerationTokenUsageService.class),
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService.class));
        jobs.init();
        String job = jobs.startJob(user(), exercise(), "brief", GenerationMode.GENERATE);
        jobs.enterNonCancellablePhase(42, job);
        doThrow(new IllegalStateException("store unavailable")).doCallRealMethod().when(slots).lock("42");
        jobs.retainIncompleteGenerationSlot(42, job);
        slots.remove("42");
        String replacementJob = jobs.startJob(user(), exercise(), "replacement", GenerationMode.GENERATE);
        JobInfo replacement = slots.get("42");
        var status = jobs.getStatus(user(), exercise());
        jobs.retryPendingCleanup();
        assertThat(slots.get("42")).isEqualTo(replacement);
        assertThat(jobs.getStatus(user(), exercise())).isEqualTo(status);
        assertThat(slots.get("42").jobId()).isEqualTo(replacementJob);
    }

    @Test
    void finishedWorkerRetriesReleaseAndRetainsItsBoundedReplay() {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var jobs = new GenerationJobService(provider, _ -> {
        }, mock(GenerationTokenUsageService.class),
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService.class));
        jobs.init();
        String job = jobs.startJob(user(), exercise(), "brief", GenerationMode.GENERATE);
        var file = ExerciseGenerationFileChangeDTO.of("solution/Stack.java", "write", 1);
        jobs.recordFileChange(42, job, file);
        doThrow(new IllegalStateException("store unavailable")).doCallRealMethod().when(slots).lock("42");
        jobs.clearJob(42, job);
        assertThat(slots.get("42").jobId()).isEqualTo(job);
        jobs.retryPendingCleanup();
        assertThat(slots.get("42")).isNull();
        assertThat(jobs.getStatus(user(), exercise())).hasValueSatisfying(status -> {
            assertThat(status.jobId()).isEqualTo(job);
            assertThat(status.running()).isFalse();
            assertThat(status.fileChanges()).containsExactly(file);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void finishedWorkerRetryPreservesReplacementJobsAndPartialSaveGuards(boolean recoveryGuard) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var jobs = new GenerationJobService(provider, _ -> {
        }, mock(GenerationTokenUsageService.class),
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService.class));
        jobs.init();
        String job = jobs.startJob(user(), exercise(), "brief", GenerationMode.GENERATE);
        doThrow(new IllegalStateException("store unavailable")).doCallRealMethod().when(slots).lock("42");
        jobs.clearJob(42, job);
        if (recoveryGuard) {
            jobs.enterNonCancellablePhase(42, job);
            jobs.retainIncompleteGenerationSlot(42, job);
        }
        else {
            slots.remove("42");
            jobs.startJob(user(), exercise(), "replacement", GenerationMode.GENERATE);
        }
        JobInfo replacement = slots.get("42");
        var replay = jobs.getStatus(user(), exercise());
        jobs.retryPendingCleanup();
        assertThat(slots.get("42")).isEqualTo(replacement);
        assertThat(jobs.getStatus(user(), exercise()).map(status -> status.jobId())).isEqualTo(replay.map(status -> status.jobId()));
    }

    private static User user() {
        var user = new User();
        user.setLogin("editor");
        return user;
    }

    private static ProgrammingExercise exercise() {
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        return exercise;
    }
}

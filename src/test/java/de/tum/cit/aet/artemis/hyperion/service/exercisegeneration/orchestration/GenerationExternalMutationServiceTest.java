package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.CoordinationSnapshot;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

class GenerationExternalMutationServiceTest {

    @Test
    void deploymentWithFencingDisabledDoesNotUseTheDistributedProvider() {
        var provider = org.mockito.Mockito.mock(DistributedDataProvider.class);
        var service = new GenerationExternalMutationService(provider, 1, false);
        var api = new HyperionExerciseMutationApi(service);
        String token = api.claimExternalMutationSlot(42);
        api.clearExternalMutationSlot(42, token);
        try (var reservation = api.reserveParticipation(42)) {
            assertThat(reservation).isNotNull();
        }
        assertThat(api.isGenerationActive(42)).isFalse();
        assertThat(service.getWedgedSlotInfo(42)).isEmpty();
        assertThat(service.recoverWedgedSlot(42, "obsolete-token")).isFalse();
        service.retryPendingReleases();
        org.mockito.Mockito.verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void completedPartialUndoRetriesRecoveryConversionWithoutReleasingItsReservation(boolean lostResponse) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var guard = new GenerationExternalMutationService(provider, 1, true);
        var jobs = new GenerationJobService(provider, _ -> {
        }, org.mockito.Mockito.mock(GenerationTokenUsageService.class), null, new HyperionAgentProperties(), org.mockito.Mockito.mock(HyperionEffortProfileService.class),
                Runnable::run, 1, Duration.ofHours(4), 0, null, guard);
        jobs.init();
        var user = new User();
        user.setLogin("editor");
        String token = jobs.claimRevertSlot(user, 42);
        if (lostResponse) {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                throw new IllegalStateException("conversion response lost");
            }).doCallRealMethod().when(slots).replace(org.mockito.ArgumentMatchers.eq("42"), org.mockito.ArgumentMatchers.any(JobInfo.class),
                    org.mockito.ArgumentMatchers.any(JobInfo.class));
        }
        else {
            doThrow(new IllegalStateException("store temporarily unavailable")).doCallRealMethod().when(slots).lock("42");
        }
        jobs.retainRevertRecoverySlot(42, token);
        JobInfo afterFailure = slots.get("42");
        assertThat(afterFailure).isNotNull();
        assertThat(afterFailure.cancellable()).isFalse();
        guard.retryPendingReleases();
        assertThat(jobs.isRevertRecoveryPending(42)).isTrue();
        JobInfo recovery = slots.get("42");
        if (lostResponse) {
            assertThat(recovery).isEqualTo(afterFailure);
        }
        assertThat(jobs.recoverWedgedSlot(42, token)).isFalse();
        assertThat(jobs.recoverWedgedSlot(42, recovery.jobId())).isTrue();
    }

    @Test
    void delayedUndoConversionCannotReplaceANewerRecoveryRetry() {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var guard = new GenerationExternalMutationService(provider, 1, true);
        var jobs = new GenerationJobService(provider, _ -> {
        }, org.mockito.Mockito.mock(GenerationTokenUsageService.class), null, new HyperionAgentProperties(), org.mockito.Mockito.mock(HyperionEffortProfileService.class),
                Runnable::run, 1, Duration.ofHours(4), 0, null, guard);
        jobs.init();
        var user = new User();
        user.setLogin("editor");
        String old = jobs.claimRevertSlot(user, 42);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("conversion response lost");
        }).doCallRealMethod().when(slots).replace(org.mockito.ArgumentMatchers.eq("42"), org.mockito.ArgumentMatchers.any(JobInfo.class),
                org.mockito.ArgumentMatchers.any(JobInfo.class));
        jobs.retainRevertRecoverySlot(42, old);
        assertThat(jobs.isRevertRecoveryPending(42)).isTrue();
        String retry = jobs.claimRevertSlot(user, 42);
        JobInfo replacement = slots.get("42");
        guard.retryPendingReleases();
        assertThat(slots.get("42")).isEqualTo(replacement);
        assertThat(jobs.isOwnedActiveJob(42, retry)).isTrue();
        assertThat(jobs.isRevertRecoveryPending(42)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void revertAcquisitionRetriesCleanupAndRestoresThePreviousRecoveryGuard(boolean recovering) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var guard = new GenerationExternalMutationService(provider, 1, true);
        var jobs = new GenerationJobService(provider, _ -> {
        }, org.mockito.Mockito.mock(GenerationTokenUsageService.class), null, new HyperionAgentProperties(), org.mockito.Mockito.mock(HyperionEffortProfileService.class),
                Runnable::run, 1, Duration.ofHours(4), 0, null, guard);
        jobs.init();
        var user = new User();
        user.setLogin("editor");
        if (recovering) {
            jobs.retainRevertRecoverySlot(42, jobs.claimRevertSlot(user, 42));
        }
        JobInfo previous = slots.get("42");
        var failure = new IllegalStateException("unlock response lost");
        var unlocks = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (unlocks.incrementAndGet() == (recovering ? 1 : 2)) {
                doThrow(new IllegalStateException("cleanup unavailable")).doCallRealMethod().when(slots).lock("42");
                throw failure;
            }
            return null;
        }).when(slots).unlock("42");
        assertThatThrownBy(() -> jobs.claimRevertSlot(user, 42)).isSameAs(failure);
        assertThat(slots.get("42").jobId()).startsWith(recovering ? GenerationRecoverySlots.RETRY_PREFIX : GenerationJobService.REVERT_JOB_PREFIX);
        guard.retryPendingReleases();
        assertThat(slots.get("42")).isEqualTo(previous);
        assertThat(jobs.isRevertRecoveryPending(42)).isEqualTo(recovering);

        unlocks.set(0);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (unlocks.incrementAndGet() == (recovering ? 1 : 2)) {
                doThrow(new IllegalStateException("cleanup unavailable")).doCallRealMethod().when(slots).lock("42");
                throw failure;
            }
            return null;
        }).when(slots).unlock("42");
        assertThatThrownBy(() -> jobs.claimRevertSlot(user, 42)).isSameAs(failure);
        JobInfo replacement = new JobInfo("replacement", "owner", 42, Instant.now(), null, "local-node", Instant.now(), true, null);
        slots.put("42", replacement);
        guard.retryPendingReleases();
        assertThat(slots.get("42")).isEqualTo(replacement);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void lostRevertWriteResponseRestoresTheExactPreviousReservation(boolean recovering) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var guard = new GenerationExternalMutationService(provider, 1, true);
        var jobs = new GenerationJobService(provider, _ -> {
        }, org.mockito.Mockito.mock(GenerationTokenUsageService.class), null, new HyperionAgentProperties(), org.mockito.Mockito.mock(HyperionEffortProfileService.class),
                Runnable::run, 1, Duration.ofHours(4), 0, null, guard);
        jobs.init();
        var user = new User();
        user.setLogin("editor");
        if (recovering) {
            jobs.retainRevertRecoverySlot(42, jobs.claimRevertSlot(user, 42));
        }
        JobInfo previous = slots.get("42");
        var failure = new IllegalStateException("write response lost");
        if (recovering) {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                throw failure;
            }).doCallRealMethod().when(slots).replace(org.mockito.ArgumentMatchers.eq("42"), org.mockito.ArgumentMatchers.eq(previous),
                    org.mockito.ArgumentMatchers.any(JobInfo.class));
        }
        else {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                throw failure;
            }).when(slots).putIfAbsent(org.mockito.ArgumentMatchers.eq("42"), org.mockito.ArgumentMatchers.any(JobInfo.class));
        }
        assertThatThrownBy(() -> jobs.claimRevertSlot(user, 42)).isSameAs(failure);
        assertThat(slots.get("42")).isEqualTo(previous);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedAcquisitionQueuesExactCleanupAfterUnlockAndPreservesReplacement(boolean participation) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var service = new GenerationExternalMutationService(provider, 1, true);
        var failure = new IllegalStateException("unlock response lost");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            doThrow(new IllegalStateException("cleanup unavailable")).doCallRealMethod().when(slots).lock("42");
            throw failure;
        }).doCallRealMethod().when(slots).unlock("42");
        assertThatThrownBy(() -> {
            if (participation) {
                service.claimParticipationSlot(42);
            }
            else {
                service.claimExternalMutationSlot(42);
            }
        }).isSameAs(failure);
        assertThat(slots.get("42")).isNotNull();
        service.retryPendingReleases();
        assertThat(slots.get("42")).isNull();

        doAnswer(invocation -> {
            invocation.callRealMethod();
            doThrow(new IllegalStateException("cleanup unavailable")).doCallRealMethod().when(slots).lock("42");
            throw failure;
        }).doCallRealMethod().when(slots).unlock("42");
        assertThatThrownBy(() -> {
            if (participation) {
                service.claimParticipationSlot(42);
            }
            else {
                service.claimExternalMutationSlot(42);
            }
        }).isSameAs(failure);
        JobInfo replacement = new JobInfo("replacement", "owner", 42, Instant.now(), null, "local-node", Instant.now(), true, null);
        slots.put("42", replacement);
        service.retryPendingReleases();
        assertThat(slots.get("42")).isEqualTo(replacement);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uncertainSlotWriteIsCompensatedBeforeReturningTheAcquisitionFailure(boolean participation) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var failure = new IllegalStateException("write response lost");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw failure;
        }).when(slots).putIfAbsent(org.mockito.ArgumentMatchers.eq("42"), org.mockito.ArgumentMatchers.any(JobInfo.class));
        var service = new GenerationExternalMutationService(provider, 1, true);
        assertThatThrownBy(() -> {
            if (participation) {
                service.claimParticipationSlot(42);
            }
            else {
                service.claimExternalMutationSlot(42);
            }
        }).isSameAs(failure);
        assertThat(slots.get("42")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void completedOperationRetriesReleaseWithoutClearingAReplacement(boolean participation) {
        var provider = spy(new LocalDataProviderService());
        var slots = spy(provider.<String, JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        var service = new GenerationExternalMutationService(provider, 1, true);
        String token = participation ? service.claimParticipationSlot(42) : service.claimExternalMutationSlot(42);
        doThrow(new IllegalStateException("store temporarily unavailable")).doCallRealMethod().when(slots).lock("42");
        if (participation) {
            service.clearParticipationSlot(42, token);
        }
        else {
            service.clearExternalMutationSlot(42, token);
        }
        assertThat(slots.get("42")).isNotNull();
        service.retryPendingReleases();
        assertThat(slots.get("42")).isNull();

        String staleToken = participation ? service.claimParticipationSlot(42) : service.claimExternalMutationSlot(42);
        doThrow(new IllegalStateException("store temporarily unavailable")).doCallRealMethod().when(slots).lock("42");
        if (participation) {
            service.clearParticipationSlot(42, staleToken);
        }
        else {
            service.clearExternalMutationSlot(42, staleToken);
        }
        JobInfo replacement = new JobInfo("new-generation", "owner", 42, Instant.now(), null, "local-node", Instant.now(), true, null);
        slots.put("42", replacement);
        service.retryPendingReleases();
        assertThat(slots.get("42")).isEqualTo(replacement);
    }

    @Test
    void sharedCopyRecoveryRequiresEveryRecordedOwnerToBeAbsent() {
        var provider = new LocalDataProviderService();
        var service = new GenerationExternalMutationService(provider, 1, true);
        String first = service.claimParticipationSlot(42);
        String second = service.claimParticipationSlot(42);
        DistributedMap<String, JobInfo> jobs = provider.getMap(GenerationJobService.JOB_MAP_NAME);
        JobInfo group = jobs.get("42");
        assertThat(group.participationOwners()).hasSize(2);
        assertThat(group.ownersAbsentFrom(Set.of(provider.getLocalNodeId()))).isFalse();
        assertThat(service.recoverWedgedSlot(42, group.jobId())).isFalse();
        jobs.put("42", group.withParticipationOwners(java.util.Map.of(first, "departed-one", second, provider.getLocalNodeId())));
        assertThat(service.recoverWedgedSlot(42, group.jobId())).isFalse();
        jobs.put("42", group.withParticipationOwners(java.util.Map.of(first, "departed-one", second, "departed-two")));
        assertThat(service.recoverWedgedSlot(42, group.jobId())).isTrue();
    }

    @Test
    void disabledWriterNodeStillProtectsGenerationWithoutInstantiatingEngine() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("localvc");
            context.getEnvironment().getPropertySources()
                    .addFirst(new org.springframework.core.env.MapPropertySource("fencing", java.util.Map.of("artemis.hyperion.generation.cluster-fencing-enabled", "true")));
            context.registerBean(DistributedDataProvider.class, LocalDataProviderService::new);
            context.register(GenerationExternalMutationService.class, HyperionExerciseMutationApi.class);
            context.refresh();
            var api = context.getBean(HyperionExerciseMutationApi.class);
            DistributedMap<String, JobInfo> jobs = context.getBean(DistributedDataProvider.class).getMap(GenerationJobService.JOB_MAP_NAME);
            JobInfo running = new JobInfo("generation", "owner", 42, Instant.now(), null, "core", Instant.now(), true, null);
            jobs.put("42", running);

            assertThatThrownBy(() -> api.claimExternalMutationSlot(42)).isInstanceOf(ConflictException.class);
            assertThat(jobs.get("42")).isEqualTo(running);
            assertThat(context.getBeansOfType(GenerationJobService.class)).isEmpty();

            jobs.remove("42", running);
            String token = api.claimExternalMutationSlot(42);
            assertThat(jobs.get("42").cancellable()).isFalse();
            assertThatThrownBy(() -> api.claimExternalMutationSlot(42)).isInstanceOf(ConflictException.class);
            api.clearExternalMutationSlot(42, "generation");
            assertThat(jobs.get("42").jobId()).isEqualTo(token);
            api.clearExternalMutationSlot(42, token);
            assertThat(jobs.get("42")).isNull();
        }
    }

    @Test
    void generationActiveIsAReadOnlyViewOfAnySlotAndWorksWithoutTheGenerationEngine() {
        var provider = new LocalDataProviderService();
        var service = new GenerationExternalMutationService(provider, 1, true);
        var api = new HyperionExerciseMutationApi(service);
        DistributedMap<String, JobInfo> jobs = provider.getMap(GenerationJobService.JOB_MAP_NAME);
        assertThat(api.isGenerationActive(42)).isFalse();

        JobInfo running = new JobInfo("generation", "owner", 42, Instant.now(), null, "core", Instant.now(), true, null);
        jobs.put("42", running);
        assertThat(api.isGenerationActive(42)).isTrue();
        assertThat(api.isGenerationActive(43)).isFalse();
        // Reading must not take, alter or release the slot.
        assertThat(jobs.get("42")).isEqualTo(running);
        jobs.remove("42", running);
        assertThat(api.isGenerationActive(42)).isFalse();

        String token = api.claimExternalMutationSlot(42);
        assertThat(api.isGenerationActive(42)).isTrue();
        api.clearExternalMutationSlot(42, token);
        assertThat(api.isGenerationActive(42)).isFalse();
    }

    @Test
    void delayedReleaseDoesNotClearReplacement() {
        var provider = new LocalDataProviderService();
        var service = new GenerationExternalMutationService(provider, 1, true);
        String old = service.claimExternalMutationSlot(42);
        service.clearExternalMutationSlot(42, old);
        String replacement = service.claimExternalMutationSlot(42);
        service.clearExternalMutationSlot(42, old);
        DistributedMap<String, JobInfo> jobs = provider.getMap(GenerationJobService.JOB_MAP_NAME);
        assertThat(jobs.get("42").jobId()).isEqualTo(replacement);
        service.clearExternalMutationSlot(42, replacement);
        service.clearExternalMutationSlot(42, replacement);
        assertThat(jobs.get("42")).isNull();
    }

    @Test
    void recoveryRequiresExactDepartedOwnerAndMajority() {
        var provider = spy(new LocalDataProviderService());
        var service = new GenerationExternalMutationService(provider, 3, true);
        doReturn(Optional.of(new CoordinationSnapshot(Set.of("local-node", "other", "third"), true))).when(provider).getCoordinationSnapshot();
        DistributedMap<String, JobInfo> jobs = provider.getMap(GenerationJobService.JOB_MAP_NAME);
        assertThat(service.getWedgedSlotInfo(42)).isEmpty();
        String token = service.claimExternalMutationSlot(42);
        assertThat(service.getWedgedSlotInfo(42).orElseThrow().ownerLeftCluster()).isFalse();
        doReturn(Optional.of(new CoordinationSnapshot(Set.of("local-node"), true))).when(provider).getCoordinationSnapshot();
        assertThatThrownBy(() -> service.recoverWedgedSlot(42, token)).isInstanceOf(ServiceUnavailableAlertException.class);
        doReturn(Optional.of(new CoordinationSnapshot(Set.of("local-node", "other"), true))).when(provider).getCoordinationSnapshot();
        assertThat(service.recoverWedgedSlot(42, token)).isFalse();
        doReturn(Optional.of(new CoordinationSnapshot(Set.of("survivor-1", "survivor-2"), true))).when(provider).getCoordinationSnapshot();
        assertThat(service.getWedgedSlotInfo(42).orElseThrow().ownerLeftCluster()).isTrue();
        assertThat(service.recoverWedgedSlot(42, "external-mutation-wrong")).isFalse();
        assertThat(service.recoverWedgedSlot(42, token)).isTrue();
        assertThat(service.recoverWedgedSlot(42, token)).isFalse();
        assertThat(jobs.get("42")).isNull();
    }

    @Test
    void disabledGenerationRecoveryNeverReleasesGenerationOrUnknownOwner() {
        var provider = new LocalDataProviderService();
        var service = new GenerationExternalMutationService(provider, 1, true);
        DistributedMap<String, JobInfo> jobs = provider.getMap(GenerationJobService.JOB_MAP_NAME);
        JobInfo generation = new JobInfo("generation", "owner", 42, Instant.now(), null, "departed", Instant.now(), false, null);
        jobs.put("42", generation);
        assertThat(service.getWedgedSlotInfo(42)).isEmpty();
        assertThat(service.recoverWedgedSlot(42, "generation")).isFalse();
        JobInfo unknownOwner = new JobInfo("external-mutation-unknown", "external", 42, Instant.now(), null, null, Instant.now(), false, null);
        jobs.put("42", unknownOwner);
        assertThat(service.getWedgedSlotInfo(42).orElseThrow().ownerLeftCluster()).isFalse();
        assertThat(service.recoverWedgedSlot(42, unknownOwner.jobId())).isFalse();
        jobs.put("42", new JobInfo(unknownOwner.jobId(), "external", 42, Instant.now(), null, "departed", Instant.now(), true, null));
        assertThat(service.getWedgedSlotInfo(42)).isEmpty();
        assertThat(service.recoverWedgedSlot(42, unknownOwner.jobId())).isFalse();
    }

    @Test
    void unknownCoordinationNeverBypassesWriterProtection() {
        var provider = spy(new LocalDataProviderService());
        doReturn(Optional.empty()).when(provider).getCoordinationSnapshot();
        var service = new GenerationExternalMutationService(provider, 1, true);
        assertThatThrownBy(() -> service.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        assertThat(provider.getMap(GenerationJobService.JOB_MAP_NAME).get("42")).isNull();
        assertThatThrownBy(() -> service.recoverWedgedSlot(42, "unknown")).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void missingTopologyDoesNotBypassTheGuard() {
        var provider = spy(new LocalDataProviderService());
        var service = new GenerationExternalMutationService(provider, 1, true);
        doReturn(Optional.empty()).when(provider).getCoordinationSnapshot();
        assertThatThrownBy(() -> service.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        doReturn(Optional.of(new CoordinationSnapshot(Set.of("other"), true))).when(provider).getCoordinationSnapshot();
        assertThatThrownBy(() -> service.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

}

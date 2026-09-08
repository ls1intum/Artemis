package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

class GenerationExternalMutationServiceTest {

    @Test
    void disabledWriterNodeStillProtectsGenerationWithoutInstantiatingEngine() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("localvc");
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
    void delayedReleaseDoesNotClearReplacement() {
        var provider = new LocalDataProviderService();
        var service = new GenerationExternalMutationService(provider, 1, "Local");
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
        var service = new GenerationExternalMutationService(provider, 3, "Local");
        DistributedMap<String, JobInfo> jobs = provider.getMap(GenerationJobService.JOB_MAP_NAME);
        assertThat(service.getWedgedSlotInfo(42)).isEmpty();
        String token = service.claimExternalMutationSlot(42);
        assertThat(service.getWedgedSlotInfo(42).orElseThrow().ownerLeftCluster()).isFalse();
        assertThatThrownBy(() -> service.recoverWedgedSlot(42, token)).isInstanceOf(ServiceUnavailableAlertException.class);
        doReturn(Optional.of(Set.of("local-node", "other"))).when(provider).getDataNodeIds();
        assertThat(service.recoverWedgedSlot(42, token)).isFalse();
        doReturn(Optional.of(Set.of("survivor-1", "survivor-2"))).when(provider).getDataNodeIds();
        assertThat(service.getWedgedSlotInfo(42).orElseThrow().ownerLeftCluster()).isTrue();
        assertThat(service.recoverWedgedSlot(42, "external-mutation-wrong")).isFalse();
        assertThat(service.recoverWedgedSlot(42, token)).isTrue();
        assertThat(service.recoverWedgedSlot(42, token)).isFalse();
        assertThat(jobs.get("42")).isNull();
    }

    @Test
    void disabledGenerationRecoveryNeverReleasesGenerationOrUnknownOwner() {
        var provider = new LocalDataProviderService();
        var service = new GenerationExternalMutationService(provider, 1, "Local");
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
    void unsupportedProviderDoesNotCreateSlotsItCannotRecover() {
        var provider = spy(new LocalDataProviderService());
        doReturn(Optional.empty()).when(provider).getDataNodeIds();
        var service = new GenerationExternalMutationService(provider, 1, "Redis");
        String token = service.claimExternalMutationSlot(42);
        service.clearExternalMutationSlot(42, token);
        assertThat(provider.getMap(GenerationJobService.JOB_MAP_NAME).get("42")).isNull();
        assertThatThrownBy(() -> service.recoverWedgedSlot(42, token)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void missingTopologyDoesNotBypassTheGuard() {
        var provider = spy(new LocalDataProviderService());
        var service = new GenerationExternalMutationService(provider, 1, "Hazelcast");
        doReturn(Optional.empty()).when(provider).getDataNodeIds();
        assertThatThrownBy(() -> service.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        doReturn(Optional.of(Set.of("other"))).when(provider).getDataNodeIds();
        assertThatThrownBy(() -> service.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
    }

}

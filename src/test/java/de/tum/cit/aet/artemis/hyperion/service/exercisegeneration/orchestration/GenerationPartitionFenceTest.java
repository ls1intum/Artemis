package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.CoordinationSnapshot;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class GenerationPartitionFenceTest {

    @Test
    void partitionDuringPersistenceStopsOwnershipAndBothParticipationPartitions() {
        var provider = spy(new LocalDataProviderService());
        String owner = provider.getLocalNodeId();
        var view = new AtomicReference<>(new CoordinationSnapshot(Set.of(owner, "second", "island"), true, true));
        doAnswer(_ -> Optional.of(view.get())).when(provider).getCoordinationSnapshot();
        var jobs = new GenerationJobService(provider, _ -> {
        }, mock(GenerationTokenUsageService.class), null, Duration.ofHours(1), Duration.ofMinutes(45), Runnable::run, 3);
        jobs.init();
        var user = new User();
        user.setLogin("owner");
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        String job = jobs.startJob(user, exercise, "Write a stack exercise", GenerationMode.GENERATE);
        assertThat(jobs.enterNonCancellablePhase(42, job)).isTrue();
        var slots = provider.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME);
        var reservation = slots.get("42");

        view.set(new CoordinationSnapshot(Set.of(owner, "second"), true, true));
        assertThat(jobs.isOwnedActiveJob(42, job)).isFalse();
        assertThat(jobs.heartbeat(42, job)).isFalse();
        var majorityWriter = new GenerationExternalMutationService(provider, 3, true);
        assertThatThrownBy(() -> majorityWriter.claimParticipationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        assertThat(slots.get("42")).isEqualTo(reservation).extracting(GenerationJobService.JobInfo::cancellable).isEqualTo(false);

        var island = spy(new LocalDataProviderService());
        doReturn("island").when(island).getLocalNodeId();
        doAnswer(_ -> Optional.of(new CoordinationSnapshot(Set.of(island.getLocalNodeId()), true, true))).when(island).getCoordinationSnapshot();
        var islandWriter = new GenerationExternalMutationService(island, 3, true);
        assertThatThrownBy(() -> islandWriter.claimParticipationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        assertThatThrownBy(() -> islandWriter.claimExternalMutationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        assertThat(island.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME).get("42")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void partitionWhileWaitingForTheSlotLockRejectsMutationAndParticipation(boolean participation) {
        var provider = spy(new LocalDataProviderService());
        String owner = provider.getLocalNodeId();
        var view = new AtomicReference<>(new CoordinationSnapshot(Set.of(owner, "second", "third"), true, true));
        doAnswer(_ -> Optional.of(view.get())).when(provider).getCoordinationSnapshot();
        var slots = spy(provider.<String, GenerationJobService.JobInfo>getMap(GenerationJobService.JOB_MAP_NAME));
        doReturn(slots).when(provider).getMap(GenerationJobService.JOB_MAP_NAME);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            view.set(new CoordinationSnapshot(Set.of(owner), true, true));
            return null;
        }).when(slots).lock("42");
        var writer = new GenerationExternalMutationService(provider, 3, true);
        assertThatThrownBy(() -> {
            if (participation) {
                writer.claimParticipationSlot(42);
            }
            else {
                writer.claimExternalMutationSlot(42);
            }
        }).isInstanceOf(ServiceUnavailableAlertException.class);
        assertThat(slots.get("42")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void missingWriterDeclarationRejectsGenerationEvenWithCompleteMembership(boolean memberHosted) {
        var provider = spy(new LocalDataProviderService());
        doAnswer(_ -> Optional.of(new CoordinationSnapshot(Set.of(provider.getLocalNodeId()), memberHosted, false))).when(provider).getCoordinationSnapshot();
        assertThatThrownBy(() -> new GenerationClusterTopology(provider, 1).verifyAllMembers()).isInstanceOf(ServiceUnavailableAlertException.class);
        assertThatThrownBy(() -> new GenerationExternalMutationService(provider, 1, true).claimParticipationSlot(42)).isInstanceOf(ServiceUnavailableAlertException.class);
        var ordinaryWriter = new GenerationExternalMutationService(provider, 1, false);
        String token = ordinaryWriter.claimExternalMutationSlot(42);
        ordinaryWriter.clearExternalMutationSlot(42, token);
    }
}

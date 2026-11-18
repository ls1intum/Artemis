package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ProgrammingExerciseLiveSaveScopeTest {

    @Test
    void examStaffMutationDoesNotDependOnGenerationMapAvailability() {
        var provider = mock(DistributedDataProvider.class);
        @SuppressWarnings("unchecked")
        DistributedMap<String, GenerationLiveSaveService.LiveSaveSlot> map = mock(DistributedMap.class);
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(map);
        when(map.get(anyString())).thenThrow(new IllegalStateException("Authoring authority unavailable"));
        var live = new GenerationLiveSaveService(provider, true);
        var exercises = mock(ProgrammingExerciseTestRepository.class);
        when(exercises.existsByIdAndExerciseGroupIsNotNull(42)).thenReturn(true);
        var guard = new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(live)), exercises);
        try (var lease = guard.claimExternalMutation(42)) {
            assertThat(lease.coordinated()).isFalse();
        }
        verifyNoInteractions(map);
    }

    @Test
    void disabledGenerationDoesNotAddAnEligibilityQuery() {
        var api = mock(HyperionExerciseMutationApi.class);
        var exercises = mock(ProgrammingExerciseTestRepository.class);
        var guard = new ProgrammingExerciseMutationGuardService(Optional.of(api), exercises);
        try (var lease = guard.claimExternalMutation(42)) {
            assertThat(lease.coordinated()).isFalse();
        }
        verifyNoInteractions(exercises);
    }
}

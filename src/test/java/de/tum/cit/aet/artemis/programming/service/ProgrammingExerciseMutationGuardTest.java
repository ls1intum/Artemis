package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;

class ProgrammingExerciseMutationGuardTest {

    @Test
    void claimExternalMutation_holdsTheHyperionSlotUntilLeaseCloses() {
        HyperionExerciseMutationApi hyperionApi = mock(HyperionExerciseMutationApi.class);
        when(hyperionApi.claimExternalMutationSlot(42L)).thenReturn("mutation-42");
        ProgrammingExerciseMutationGuardService guard = new ProgrammingExerciseMutationGuardService(Optional.of(hyperionApi));

        try (ProgrammingExerciseMutationGuardService.MutationLease ignored = guard.claimExternalMutation(42L)) {
            verify(hyperionApi).claimExternalMutationSlot(42L);
        }

        verify(hyperionApi).clearExternalMutationSlot(42L, "mutation-42");
    }

    @Test
    void claimExternalMutation_propagatesConflictWithoutClearing() {
        HyperionExerciseMutationApi hyperionApi = mock(HyperionExerciseMutationApi.class);
        when(hyperionApi.claimExternalMutationSlot(42L))
                .thenThrow(new ConflictException("Exercise 42 is already being mutated.", "programmingExercise", "hyperionMutationSlotConflict"));
        ProgrammingExerciseMutationGuardService guard = new ProgrammingExerciseMutationGuardService(Optional.of(hyperionApi));

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> guard.claimExternalMutation(42L));
        verify(hyperionApi, never()).clearExternalMutationSlot(anyLong(), anyString());
    }

    @Test
    void claimExternalMutation_isNoOpWhenWholeExerciseGenerationIsDisabled() {
        ProgrammingExerciseMutationGuardService guard = new ProgrammingExerciseMutationGuardService(Optional.empty());
        assertThatCode(() -> guard.claimExternalMutation(42L).close()).doesNotThrowAnyException();
    }

    @Test
    void claimExternalMutation_isNoOpForAnUnrelatedRepository() {
        HyperionExerciseMutationApi hyperionApi = mock(HyperionExerciseMutationApi.class);
        ProgrammingExerciseMutationGuardService guard = new ProgrammingExerciseMutationGuardService(Optional.of(hyperionApi));
        assertThatCode(() -> guard.claimExternalMutation(OptionalLong.empty()).close()).doesNotThrowAnyException();
        verify(hyperionApi, never()).claimExternalMutationSlot(anyLong());
    }
}

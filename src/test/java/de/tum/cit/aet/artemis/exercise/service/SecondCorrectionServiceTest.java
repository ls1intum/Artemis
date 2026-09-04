package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;

/**
 * A running generation owns the exercise it is rewriting. Toggling second correction underneath it would race repository and metadata writes it is mid-way through, so the toggle
 * must be refused before anything is mutated — not merely reported afterwards.
 */
class SecondCorrectionServiceTest {

    @Test
    void toggle_whenGenerationOwnsProgrammingExercise_rejectsBeforeMutation() {
        long exerciseId = 42L;
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
        when(exerciseRepository.findByIdElseThrow(exerciseId)).thenReturn(exercise);
        ProgrammingExerciseMutationGuardService mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);
        when(mutationGuard.claimExternalMutation(OptionalLong.of(exerciseId)))
                .thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        SecondCorrectionService secondCorrectionService = new SecondCorrectionService(exerciseRepository, versionService, mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> secondCorrectionService.toggle(exerciseId, new User()));

        verify(exerciseRepository, never()).toggleSecondCorrection(any());
        verify(versionService, never()).createExerciseVersionSynchronously(any(), any());
    }
}

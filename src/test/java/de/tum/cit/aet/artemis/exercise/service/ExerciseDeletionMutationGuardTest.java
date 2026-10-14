package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.assessment.service.ExampleSubmissionService;
import de.tum.cit.aet.artemis.assessment.test_repository.TutorParticipationTestRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

class ExerciseDeletionMutationGuardTest {

    private final ExerciseTestRepository repository = mock(ExerciseTestRepository.class);

    private final ParticipationDeletionService participations = mock(ParticipationDeletionService.class);

    private final ProgrammingExerciseDeletionService programmingDeletion = mock(ProgrammingExerciseDeletionService.class);

    private final ChannelService channels = mock(ChannelService.class);

    private final ProgrammingExerciseMutationGuardService guard = mock(ProgrammingExerciseMutationGuardService.class);

    private ProgrammingExercise exercise;

    private ExerciseDeletionService service;

    @BeforeEach
    void setUp() {
        exercise = new ProgrammingExercise();
        exercise.setId(42L);
        when(repository.findWithCompetenciesByIdElseThrow(42L)).thenReturn(exercise);
        service = new ExerciseDeletionService(repository, participations, programmingDeletion, mock(QuizExerciseService.class), mock(TutorParticipationTestRepository.class),
                mock(ExampleSubmissionService.class), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), channels, Optional.empty(), Optional.empty(),
                Optional.empty(), guard);
    }

    @ParameterizedTest
    @ValueSource(strings = { "SAVE", "COPY" })
    void commonDeletionEntryRejectsReservedExerciseBeforeAnyDestructiveCall(String purpose) {
        when(guard.claimExternalMutation(42L)).thenThrow(new ConflictException(purpose + " owns exercise", "programmingExercise", "generationRunning"));
        assertThatThrownBy(() -> service.delete(42L, true)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(channels, participations, programmingDeletion);
    }

    @Test
    void commonDeletionEntryHoldsOneLeaseThroughProgrammingCleanup() {
        var held = new AtomicBoolean();
        when(guard.claimExternalMutation(42L)).thenAnswer(_ -> {
            assertThat(held.compareAndSet(false, true)).isTrue();
            return new ProgrammingExerciseMutationGuardService.MutationLease(() -> held.set(false));
        });
        when(repository.findByIdWithEagerExampleSubmissionsElseThrow(42L)).thenReturn(exercise);
        doAnswer(_ -> {
            assertThat(held).isTrue();
            return null;
        }).when(channels).deleteChannelForExerciseId(42L);
        doAnswer(_ -> {
            assertThat(held).isTrue();
            return null;
        }).when(programmingDeletion).delete(42L, true);
        service.delete(42L, true);
        verify(guard).claimExternalMutation(42L);
        verify(programmingDeletion).delete(42L, true);
        assertThat(held).isFalse();
    }

    @Test
    void disabledGenerationPreservesOrdinaryProgrammingDeletion() {
        var disabled = new ProgrammingExerciseMutationGuardService(Optional.empty());
        service = new ExerciseDeletionService(repository, participations, programmingDeletion, mock(QuizExerciseService.class), mock(TutorParticipationTestRepository.class),
                mock(ExampleSubmissionService.class), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), channels, Optional.empty(), Optional.empty(),
                Optional.empty(), disabled);
        when(repository.findByIdWithEagerExampleSubmissionsElseThrow(42L)).thenReturn(exercise);
        service.delete(42L, true);
        verify(channels).deleteChannelForExerciseId(42L);
        verify(programmingDeletion).delete(42L, true);
    }

    @Test
    void guardIsReleasedWhenTheProtectedRefetchFailsBeforeDeletion() {
        var held = new AtomicBoolean();
        when(guard.claimExternalMutation(42L)).thenAnswer(_ -> {
            held.set(true);
            return new ProgrammingExerciseMutationGuardService.MutationLease(() -> held.set(false));
        });
        when(repository.findWithCompetenciesByIdElseThrow(42L)).thenReturn(exercise).thenThrow(new IllegalStateException("deleted before claim"));
        assertThatThrownBy(() -> service.delete(42L, true)).isInstanceOf(IllegalStateException.class);
        assertThat(held).isFalse();
        verifyNoInteractions(channels, participations, programmingDeletion);
    }
}

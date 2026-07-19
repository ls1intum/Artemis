package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuard;

class ProgrammingExerciseDeletionResourceMutationGuardTest {

    private static final long EXERCISE_ID = 42L;

    private final ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);

    private final ExerciseDeletionService exerciseDeletionService = mock(ExerciseDeletionService.class);

    private final ProgrammingExerciseDeletionService programmingExerciseDeletionService = mock(ProgrammingExerciseDeletionService.class);

    private final ContinuousIntegrationService continuousIntegrationService = mock(ContinuousIntegrationService.class);

    private final ExerciseVersionService exerciseVersionService = mock(ExerciseVersionService.class);

    private ProgrammingExerciseDeletionResource resource;

    @BeforeEach
    void setUp() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        when(repository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesElseThrow(EXERCISE_ID)).thenReturn(exercise);
        when(repository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesAndBuildConfigElseThrow(EXERCISE_ID)).thenReturn(exercise);
        when(repository.findByIdElseThrow(EXERCISE_ID)).thenReturn(exercise);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(new User());
        ProgrammingExerciseMutationGuard guard = mock(ProgrammingExerciseMutationGuard.class);
        when(guard.claimExternalMutation(EXERCISE_ID)).thenThrow(new ConflictException("Generation is running", "programmingExercise", "generationRunning"));
        resource = new ProgrammingExerciseDeletionResource(repository, userRepository, mock(AuthorizationCheckService.class), Optional.of(continuousIntegrationService),
                mock(ExerciseService.class), exerciseDeletionService, programmingExerciseDeletionService, exerciseVersionService, guard);
    }

    @Test
    void deleteRejectsBeforeMutationWhenGenerationOwnsTheExercise() {
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.deleteProgrammingExercise(EXERCISE_ID, true));

        verifyNoInteractions(exerciseDeletionService);
    }

    @Test
    void resetRejectsBeforeMutationWhenGenerationOwnsTheExercise() {
        var options = new ProgrammingExerciseResetOptionsDTO(true, true);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.reset(EXERCISE_ID, options));

        verifyNoInteractions(exerciseDeletionService);
        verifyNoInteractions(continuousIntegrationService);
        verifyNoInteractions(exerciseVersionService);
    }

    @Test
    void taskDeletionRejectsBeforeMutationWhenGenerationOwnsTheExercise() {
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.deleteTasks(EXERCISE_ID));

        verifyNoInteractions(programmingExerciseDeletionService);
        verifyNoInteractions(exerciseVersionService);
    }

    @Test
    void deleteReleasesTheLeaseAfterDeletionServiceFailsSoASubsequentClaimSucceeds() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        ProgrammingExerciseRepository deletionRepository = mock(ProgrammingExerciseRepository.class);
        when(deletionRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndCompetenciesElseThrow(EXERCISE_ID)).thenReturn(exercise);
        UserRepository deletionUserRepository = mock(UserRepository.class);
        when(deletionUserRepository.getUserWithGroupsAndAuthorities()).thenReturn(new User());
        ExerciseDeletionService deletionService = mock(ExerciseDeletionService.class);
        IllegalStateException failure = new IllegalStateException("delete failed");
        doThrow(failure).when(deletionService).delete(EXERCISE_ID, true);
        AtomicBoolean leaseHeld = new AtomicBoolean();
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(EXERCISE_ID)).thenAnswer(invocation -> {
            assertThat(leaseHeld.compareAndSet(false, true)).isTrue();
            return new ProgrammingExerciseMutationGuard.MutationLease(() -> leaseHeld.set(false));
        });
        ProgrammingExerciseDeletionResource deletionResource = new ProgrammingExerciseDeletionResource(deletionRepository, deletionUserRepository,
                mock(AuthorizationCheckService.class), Optional.empty(), mock(ExerciseService.class), deletionService, mock(ProgrammingExerciseDeletionService.class),
                mock(ExerciseVersionService.class), mutationGuard);

        assertThatThrownBy(() -> deletionResource.deleteProgrammingExercise(EXERCISE_ID, true)).isSameAs(failure);

        assertThat(leaseHeld).isFalse();
        assertThatCode(() -> mutationGuard.claimExternalMutation(EXERCISE_ID).close()).doesNotThrowAnyException();
    }
}

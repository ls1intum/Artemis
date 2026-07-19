package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationScheduleService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuard;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseService;

class ProgrammingExerciseTestCaseResourceTest {

    @Test
    void updateTestCases_whenGenerationOwnsMutationSlot_rejectsBeforeSideEffects() {
        long exerciseId = 61L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise exercise = exercise(exerciseId);
        when(repository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(exercise);
        User user = user("editor");
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        AuthorizationCheckService authorizationCheckService = mock(AuthorizationCheckService.class);
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(exerciseId)).thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));
        ProgrammingExerciseTestCaseService testCaseService = mock(ProgrammingExerciseTestCaseService.class);
        ProgrammingExerciseCreationScheduleService scheduleService = mock(ProgrammingExerciseCreationScheduleService.class);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        ProgrammingExerciseTestCaseResource resource = resource(repository, userRepository, authorizationCheckService, testCaseService, scheduleService, versionService,
                mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.updateTestCases(exerciseId, Set.of()));

        verify(userRepository).getUserWithGroupsAndAuthorities();
        var order = inOrder(authorizationCheckService, mutationGuard);
        order.verify(authorizationCheckService).checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);
        order.verify(mutationGuard).claimExternalMutation(exerciseId);
        verifyNoInteractions(testCaseService, scheduleService, versionService);
    }

    @Test
    void resetTestCases_whenGenerationOwnsMutationSlot_rejectsBeforeSideEffects() {
        long exerciseId = 62L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise exercise = exercise(exerciseId);
        when(repository.findByIdElseThrow(exerciseId)).thenReturn(exercise);
        User user = user("editor");
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        AuthorizationCheckService authorizationCheckService = mock(AuthorizationCheckService.class);
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(exerciseId)).thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));
        ProgrammingExerciseTestCaseService testCaseService = mock(ProgrammingExerciseTestCaseService.class);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        ProgrammingExerciseTestCaseResource resource = resource(repository, userRepository, authorizationCheckService, testCaseService,
                mock(ProgrammingExerciseCreationScheduleService.class), versionService, mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.resetTestCases(exerciseId));

        verify(userRepository).getUserWithGroupsAndAuthorities();
        var order = inOrder(authorizationCheckService, mutationGuard);
        order.verify(authorizationCheckService).checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);
        order.verify(mutationGuard).claimExternalMutation(exerciseId);
        verifyNoInteractions(testCaseService, versionService);
    }

    @Test
    void updateTestCases_refetchesInsideLeaseAndHoldsLeaseThroughAllTails() {
        long exerciseId = 63L;
        AtomicBoolean leaseHeld = new AtomicBoolean();
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exercise(exerciseId);
        ProgrammingExercise fresh = exercise(exerciseId);
        when(repository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(stale, fresh);
        User user = user("editor");
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        ProgrammingExerciseTestCaseService testCaseService = mock(ProgrammingExerciseTestCaseService.class);
        ProgrammingExerciseTestCaseDTO update = mock(ProgrammingExerciseTestCaseDTO.class);
        ProgrammingExerciseTestCase updatedTestCase = mock(ProgrammingExerciseTestCase.class);
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            return Set.of(updatedTestCase);
        }).when(testCaseService).update(exerciseId, Set.of(update));
        when(updatedTestCase.isAfterDueDate()).thenReturn(true);
        ProgrammingExerciseCreationScheduleService scheduleService = mock(ProgrammingExerciseCreationScheduleService.class);
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            return null;
        }).when(scheduleService).scheduleOperations(exerciseId);
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            return null;
        }).when(updatedTestCase).setExercise(null);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            return null;
        }).when(versionService).createExerciseVersionSynchronously(fresh, user);
        ProgrammingExerciseMutationGuard mutationGuard = trackingGuard(exerciseId, leaseHeld);
        ProgrammingExerciseTestCaseResource resource = resource(repository, userRepository, mock(AuthorizationCheckService.class), testCaseService, scheduleService, versionService,
                mutationGuard);

        resource.updateTestCases(exerciseId, Set.of(update));

        verify(mutationGuard).claimExternalMutation(exerciseId);
        verify(versionService).createExerciseVersionSynchronously(fresh, user);
        verify(versionService, never()).createExerciseVersion(any(ProgrammingExercise.class), any(User.class));
        assertThat(leaseHeld).isFalse();
    }

    @Test
    void resetTestCases_refetchesInsideLeaseAndHoldsLeaseThroughAllTails() {
        long exerciseId = 64L;
        AtomicBoolean leaseHeld = new AtomicBoolean();
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exercise(exerciseId);
        ProgrammingExercise fresh = exercise(exerciseId);
        when(repository.findByIdElseThrow(exerciseId)).thenReturn(stale, fresh, fresh);
        User user = user("editor");
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user);
        ProgrammingExerciseTestCaseService testCaseService = mock(ProgrammingExerciseTestCaseService.class);
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            return null;
        }).when(testCaseService).logTestCaseReset(user, fresh, null);
        List<ProgrammingExerciseTestCase> resetTestCases = List.of(mock(ProgrammingExerciseTestCase.class));
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            assertThat(invocation.<ProgrammingExercise>getArgument(0)).isSameAs(fresh);
            return resetTestCases;
        }).when(testCaseService).reset(fresh);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            return null;
        }).when(versionService).createExerciseVersionSynchronously(fresh, user);
        ProgrammingExerciseMutationGuard mutationGuard = trackingGuard(exerciseId, leaseHeld);
        ProgrammingExerciseTestCaseResource resource = resource(repository, userRepository, mock(AuthorizationCheckService.class), testCaseService,
                mock(ProgrammingExerciseCreationScheduleService.class), versionService, mutationGuard);

        resource.resetTestCases(exerciseId);

        verify(mutationGuard).claimExternalMutation(exerciseId);
        verify(versionService).createExerciseVersionSynchronously(fresh, user);
        verify(versionService, never()).createExerciseVersion(any(ProgrammingExercise.class), any(User.class));
        assertThat(leaseHeld).isFalse();
    }

    @Test
    void updateTestCases_whenMutationFails_releasesMutationSlot() {
        long exerciseId = 65L;
        AtomicBoolean leaseHeld = new AtomicBoolean();
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        when(repository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(exercise(exerciseId));
        ProgrammingExerciseTestCaseService testCaseService = mock(ProgrammingExerciseTestCaseService.class);
        when(testCaseService.update(exerciseId, Set.of())).thenThrow(new IllegalStateException("update failed"));
        ProgrammingExerciseMutationGuard mutationGuard = trackingGuard(exerciseId, leaseHeld);
        ProgrammingExerciseTestCaseResource resource = resource(repository, testCaseService, mutationGuard);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> resource.updateTestCases(exerciseId, Set.of()));

        verify(mutationGuard).claimExternalMutation(exerciseId);
        assertThat(leaseHeld).isFalse();
    }

    @Test
    void resetTestCases_whenMutationFails_releasesMutationSlot() {
        long exerciseId = 66L;
        AtomicBoolean leaseHeld = new AtomicBoolean();
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise exercise = exercise(exerciseId);
        when(repository.findByIdElseThrow(exerciseId)).thenReturn(exercise, exercise);
        ProgrammingExerciseTestCaseService testCaseService = mock(ProgrammingExerciseTestCaseService.class);
        when(testCaseService.reset(exercise)).thenThrow(new IllegalStateException("reset failed"));
        ProgrammingExerciseMutationGuard mutationGuard = trackingGuard(exerciseId, leaseHeld);
        ProgrammingExerciseTestCaseResource resource = resource(repository, testCaseService, mutationGuard);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> resource.resetTestCases(exerciseId));

        verify(mutationGuard).claimExternalMutation(exerciseId);
        assertThat(leaseHeld).isFalse();
    }

    private ProgrammingExerciseTestCaseResource resource(ProgrammingExerciseRepository repository, ProgrammingExerciseTestCaseService testCaseService,
            ProgrammingExerciseMutationGuard mutationGuard) {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user("editor"));
        return resource(repository, userRepository, mock(AuthorizationCheckService.class), testCaseService, mock(ProgrammingExerciseCreationScheduleService.class),
                mock(ExerciseVersionService.class), mutationGuard);
    }

    private ProgrammingExerciseTestCaseResource resource(ProgrammingExerciseRepository repository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, ProgrammingExerciseTestCaseService testCaseService, ProgrammingExerciseCreationScheduleService scheduleService,
            ExerciseVersionService versionService, ProgrammingExerciseMutationGuard mutationGuard) {
        return new ProgrammingExerciseTestCaseResource(mock(ProgrammingExerciseTestCaseRepository.class), testCaseService, scheduleService, repository, authorizationCheckService,
                userRepository, versionService, mutationGuard);
    }

    private ProgrammingExerciseMutationGuard trackingGuard(long exerciseId, AtomicBoolean leaseHeld) {
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(exerciseId)).thenAnswer(invocation -> {
            assertThat(leaseHeld.compareAndSet(false, true)).isTrue();
            return new ProgrammingExerciseMutationGuard.MutationLease(() -> leaseHeld.set(false));
        });
        return mutationGuard;
    }

    private static User user(String login) {
        User user = new User();
        user.setLogin(login);
        return user;
    }

    private static ProgrammingExercise exercise(long exerciseId) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        exercise.setTitle("Exercise");
        return exercise;
    }
}

package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuard;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;

class StaticCodeAnalysisResourceMutationGuardTest {

    private static final long TARGET_EXERCISE_ID = 71L;

    private static final long SOURCE_EXERCISE_ID = 72L;

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void mutation_whenGenerationOwnsSlot_authorizesThenRejectsBeforeSideEffects(Mutation mutation) {
        ProgrammingExercise targetExercise = exercise(TARGET_EXERCISE_ID);
        ProgrammingExercise sourceExercise = exercise(SOURCE_EXERCISE_ID);
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        when(repository.findByIdElseThrow(Long.valueOf(TARGET_EXERCISE_ID))).thenReturn(targetExercise);
        when(repository.findByIdElseThrow(Long.valueOf(SOURCE_EXERCISE_ID))).thenReturn(sourceExercise);
        AuthorizationCheckService authorizationCheckService = mock(AuthorizationCheckService.class);
        StaticCodeAnalysisService staticCodeAnalysisService = mock(StaticCodeAnalysisService.class);
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(TARGET_EXERCISE_ID))
                .thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));
        StaticCodeAnalysisResource resource = resource(repository, authorizationCheckService, staticCodeAnalysisService, mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> mutation.invoke(resource));

        var order = inOrder(authorizationCheckService, mutationGuard);
        order.verify(authorizationCheckService).checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, targetExercise, null);
        if (mutation == Mutation.IMPORT) {
            order.verify(authorizationCheckService).checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, sourceExercise, null);
        }
        order.verify(mutationGuard).claimExternalMutation(TARGET_EXERCISE_ID);
        verifyNoInteractions(staticCodeAnalysisService);
    }

    @Test
    void reset_whenMutationFails_propagatesFailureAndReleasesSlot() {
        ProgrammingExercise authorizationExercise = exercise(TARGET_EXERCISE_ID);
        ProgrammingExercise authoritativeExercise = exercise(TARGET_EXERCISE_ID);
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        when(repository.findByIdElseThrow(Long.valueOf(TARGET_EXERCISE_ID))).thenReturn(authorizationExercise, authoritativeExercise);
        StaticCodeAnalysisService staticCodeAnalysisService = mock(StaticCodeAnalysisService.class);
        IllegalStateException failure = new IllegalStateException("reset failed");
        AtomicBoolean leaseHeld = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            assertThat(invocation.<ProgrammingExercise>getArgument(0)).isSameAs(authoritativeExercise);
            throw failure;
        }).when(staticCodeAnalysisService).resetCategories(authoritativeExercise);
        ProgrammingExerciseMutationGuard mutationGuard = trackingGuard(leaseHeld);
        StaticCodeAnalysisResource resource = resource(repository, mock(AuthorizationCheckService.class), staticCodeAnalysisService, mutationGuard);

        assertThatThrownBy(() -> resource.resetStaticCodeAnalysisCategories(TARGET_EXERCISE_ID)).isSameAs(failure);

        assertThat(leaseHeld).isFalse();
    }

    @Test
    void update_versionsTheAuthoritativeExerciseBeforeReleasingTheSlot() {
        ProgrammingExercise authorizationExercise = exercise(TARGET_EXERCISE_ID);
        ProgrammingExercise authoritativeExercise = exercise(TARGET_EXERCISE_ID);
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        when(repository.findByIdElseThrow(Long.valueOf(TARGET_EXERCISE_ID))).thenReturn(authorizationExercise, authoritativeExercise);
        StaticCodeAnalysisService staticCodeAnalysisService = mock(StaticCodeAnalysisService.class);
        when(staticCodeAnalysisService.updateCategories(TARGET_EXERCISE_ID, Set.of())).thenReturn(Set.of());
        User user = new User();
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUser()).thenReturn(user);
        ExerciseVersionService exerciseVersionService = mock(ExerciseVersionService.class);
        AtomicBoolean leaseHeld = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(leaseHeld).isTrue();
            return null;
        }).when(exerciseVersionService).createExerciseVersionSynchronously(authoritativeExercise, user);
        ProgrammingExerciseMutationGuard mutationGuard = trackingGuard(leaseHeld);
        StaticCodeAnalysisResource resource = new StaticCodeAnalysisResource(mock(AuthorizationCheckService.class), repository, staticCodeAnalysisService,
                mock(StaticCodeAnalysisCategoryRepository.class), mutationGuard, userRepository, exerciseVersionService);

        resource.updateStaticCodeAnalysisCategories(TARGET_EXERCISE_ID, Set.of());

        assertThat(leaseHeld).isFalse();
    }

    private static StaticCodeAnalysisResource resource(ProgrammingExerciseRepository repository, AuthorizationCheckService authorizationCheckService,
            StaticCodeAnalysisService staticCodeAnalysisService, ProgrammingExerciseMutationGuard mutationGuard) {
        return new StaticCodeAnalysisResource(authorizationCheckService, repository, staticCodeAnalysisService, mock(StaticCodeAnalysisCategoryRepository.class), mutationGuard,
                mock(UserRepository.class), mock(ExerciseVersionService.class));
    }

    private static ProgrammingExerciseMutationGuard trackingGuard(AtomicBoolean leaseHeld) {
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(TARGET_EXERCISE_ID)).thenAnswer(invocation -> {
            assertThat(leaseHeld.compareAndSet(false, true)).isTrue();
            return new ProgrammingExerciseMutationGuard.MutationLease(() -> leaseHeld.set(false));
        });
        return mutationGuard;
    }

    private static ProgrammingExercise exercise(long id) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setStaticCodeAnalysisEnabled(true);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        return exercise;
    }

    private enum Mutation {

        UPDATE {

            @Override
            void invoke(StaticCodeAnalysisResource resource) {
                resource.updateStaticCodeAnalysisCategories(TARGET_EXERCISE_ID, Set.of());
            }
        },
        RESET {

            @Override
            void invoke(StaticCodeAnalysisResource resource) {
                resource.resetStaticCodeAnalysisCategories(TARGET_EXERCISE_ID);
            }
        },
        IMPORT {

            @Override
            void invoke(StaticCodeAnalysisResource resource) {
                resource.importStaticCodeAnalysisCategoriesFromExercise(TARGET_EXERCISE_ID, SOURCE_EXERCISE_ID);
            }
        };

        abstract void invoke(StaticCodeAnalysisResource resource);
    }
}

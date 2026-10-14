package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgrammingExercisePartialUpdateResourceTest {

    private GenerationExternalMutationService mutations;

    @BeforeEach
    void setUp() {
        mutations = new GenerationExternalMutationService(de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipTestFixture.create());
    }

    @Test
    void updateProblemStatement_whenGenerationOwnsMutationSlot_rejectsBeforePersistence() {
        long exerciseId = 42L;
        ProgrammingExerciseRepository programmingExerciseRepository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise exercise = exercise(exerciseId);
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationCategoriesById(exerciseId)).thenReturn(Optional.of(exercise));
        ProgrammingExerciseCreationUpdateService updateService = mock(ProgrammingExerciseCreationUpdateService.class);
        ProgrammingExerciseMutationGuardService mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);
        when(mutationGuard.claimExternalMutation(exerciseId)).thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));
        ProgrammingExercisePartialUpdateResource resource = resource(programmingExerciseRepository, updateService, mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.updateProblemStatement(exerciseId, "new statement", null));

        verifyNoInteractions(updateService);
    }

    @Test
    void updateProblemStatement_refetchesInsideLeaseAndBlocksSaveThroughSideEffects() {
        long exerciseId = 43L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exercise(exerciseId);
        ProgrammingExercise fresh = exercise(exerciseId);
        when(repository.findWithTemplateAndSolutionParticipationCategoriesById(exerciseId)).thenReturn(Optional.of(stale), Optional.of(fresh));
        ProgrammingExerciseCreationUpdateService updateService = mock(ProgrammingExerciseCreationUpdateService.class);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        ProgrammingExerciseMutationGuardService guard = realGuard();
        doAnswer(invocation -> {
            assertThat(invocation.getArgument(0, ProgrammingExercise.class)).isSameAs(fresh);
            assertSaveCannotClaim(exerciseId);
            return fresh;
        }).when(updateService).updateProblemStatement(same(fresh), same("new statement"), same(null));
        doAnswer(invocation -> {
            assertSaveCannotClaim(exerciseId);
            return null;
        }).when(versionService).createExerciseVersionSynchronously(same(fresh), org.mockito.ArgumentMatchers.any());
        ProgrammingExercisePartialUpdateResource resource = resource(repository, updateService, guard, versionService);

        resource.updateProblemStatement(exerciseId, "new statement", null);

        verify(updateService, never()).updateProblemStatement(same(stale), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertSaveCanClaim(exerciseId);
    }

    @Test
    void updateTimeline_refetchesInsideLeaseAndBlocksSaveThroughSideEffects() {
        long exerciseId = 44L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exercise(exerciseId);
        ProgrammingExercise fresh = exercise(exerciseId);
        ProgrammingExerciseTimelineUpdateDTO dto = new ProgrammingExerciseTimelineUpdateDTO(exerciseId, null, null, null, null, null, null, null);
        when(repository.findByIdElseThrow(Long.valueOf(exerciseId))).thenReturn(stale, fresh);
        ProgrammingExerciseCreationUpdateService updateService = mock(ProgrammingExerciseCreationUpdateService.class);
        doAnswer(invocation -> {
            assertThat(invocation.getArgument(0, ProgrammingExercise.class)).isSameAs(fresh);
            assertSaveCannotClaim(exerciseId);
            return fresh;
        }).when(updateService).updateTimeline(same(fresh), same(dto), same(null));
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        doAnswer(invocation -> {
            assertSaveCannotClaim(exerciseId);
            return null;
        }).when(versionService).createExerciseVersionSynchronously(same(fresh), org.mockito.ArgumentMatchers.any());
        ProgrammingExercisePartialUpdateResource resource = resource(repository, updateService, realGuard(), versionService);

        resource.updateProgrammingExerciseTimeline(dto, null);

        assertSaveCanClaim(exerciseId);
    }

    @Test
    void updateProblemStatement_holdsMutationSlotUntilSynchronousVersionTailCompletes() throws Exception {
        long exerciseId = 45L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise exercise = exercise(exerciseId);
        when(repository.findWithTemplateAndSolutionParticipationCategoriesById(exerciseId)).thenReturn(Optional.of(exercise));
        ProgrammingExerciseCreationUpdateService updateService = mock(ProgrammingExerciseCreationUpdateService.class);
        when(updateService.updateProblemStatement(same(exercise), same("new statement"), same(null))).thenReturn(exercise);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        CountDownLatch versionTailEntered = new CountDownLatch(1);
        CountDownLatch finishVersionTail = new CountDownLatch(1);
        doAnswer(invocation -> {
            versionTailEntered.countDown();
            assertThat(finishVersionTail.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(versionService).createExerciseVersionSynchronously(same(exercise), org.mockito.ArgumentMatchers.any());
        ProgrammingExercisePartialUpdateResource resource = resource(repository, updateService, realGuard(), versionService);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> update = executor.submit(() -> resource.updateProblemStatement(exerciseId, "new statement", null));
            assertThat(versionTailEntered.await(5, TimeUnit.SECONDS)).isTrue();

            assertSaveCannotClaim(exerciseId);

            finishVersionTail.countDown();
            update.get(5, TimeUnit.SECONDS);
            assertSaveCanClaim(exerciseId);
        }
        finally {
            finishVersionTail.countDown();
            executor.shutdownNow();
        }
    }

    private ProgrammingExercisePartialUpdateResource resource(ProgrammingExerciseRepository repository, ProgrammingExerciseCreationUpdateService updateService,
            ProgrammingExerciseMutationGuardService mutationGuard) {
        return resource(repository, updateService, mutationGuard, mock(ExerciseVersionService.class));
    }

    private ProgrammingExercisePartialUpdateResource resource(ProgrammingExerciseRepository repository, ProgrammingExerciseCreationUpdateService updateService,
            ProgrammingExerciseMutationGuardService mutationGuard, ExerciseVersionService versionService) {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithAuthorities()).thenReturn(user("editor"));
        return new ProgrammingExercisePartialUpdateResource(repository, userRepository, mock(AuthorizationCheckService.class), mock(ExerciseService.class), updateService,
                mock(ProgrammingExerciseTaskService.class), versionService, mutationGuard, mock(ExerciseVariantGroupService.class),
                mock(de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository.class),
                mock(de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository.class));
    }

    private ProgrammingExerciseMutationGuardService realGuard() {
        return new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(mutations)));
    }

    private void assertSaveCannotClaim(long exerciseId) {
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> mutations.claimSaveSlot(exerciseId, java.util.UUID.randomUUID().toString()));
    }

    private void assertSaveCanClaim(long exerciseId) {
        String token = java.util.UUID.randomUUID().toString();
        mutations.claimSaveSlot(exerciseId, token);
        mutations.releaseSaveSlot(exerciseId, token);
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

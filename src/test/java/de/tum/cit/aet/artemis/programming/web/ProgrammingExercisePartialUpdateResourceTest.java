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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.HyperionGenerationBudgetService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuard;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgrammingExercisePartialUpdateResourceTest {

    private HazelcastInstance hazelcastInstance;

    private GenerationJobService generationJobService;

    @BeforeAll
    void startHazelcast() {
        Config config = new Config();
        config.setClusterName("partial-update-mutation-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @BeforeEach
    void setUp() {
        hazelcastInstance.getDistributedObjects().forEach(distributedObject -> distributedObject.destroy());
        generationJobService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), mock(HyperionGenerationBudgetService.class), Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run);
        generationJobService.init();
    }

    @AfterAll
    void stopHazelcast() {
        hazelcastInstance.shutdown();
    }

    @Test
    void updateProblemStatement_whenGenerationOwnsMutationSlot_rejectsBeforePersistence() {
        long exerciseId = 42L;
        ProgrammingExerciseRepository programmingExerciseRepository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise exercise = exercise(exerciseId);
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId)).thenReturn(Optional.of(exercise));
        ProgrammingExerciseCreationUpdateService updateService = mock(ProgrammingExerciseCreationUpdateService.class);
        ProgrammingExerciseMutationGuard mutationGuard = mock(ProgrammingExerciseMutationGuard.class);
        when(mutationGuard.claimExternalMutation(exerciseId)).thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "generationRunning"));
        ProgrammingExercisePartialUpdateResource resource = resource(programmingExerciseRepository, updateService, mutationGuard);

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.updateProblemStatement(exerciseId, "new statement", null));

        verifyNoInteractions(updateService);
    }

    @Test
    void updateProblemStatement_refetchesInsideLeaseAndBlocksGenerationThroughSideEffects() {
        long exerciseId = 43L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exercise(exerciseId);
        ProgrammingExercise fresh = exercise(exerciseId);
        when(repository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId)).thenReturn(Optional.of(stale), Optional.of(fresh));
        ProgrammingExerciseCreationUpdateService updateService = mock(ProgrammingExerciseCreationUpdateService.class);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        ProgrammingExerciseMutationGuard guard = realGuard();
        doAnswer(invocation -> {
            assertThat(invocation.getArgument(0, ProgrammingExercise.class)).isSameAs(fresh);
            assertGenerationCannotClaim(exerciseId);
            return fresh;
        }).when(updateService).updateProblemStatement(same(fresh), same("new statement"), same(null));
        doAnswer(invocation -> {
            assertGenerationCannotClaim(exerciseId);
            return null;
        }).when(versionService).createExerciseVersionSynchronously(same(fresh), org.mockito.ArgumentMatchers.any());
        ProgrammingExercisePartialUpdateResource resource = resource(repository, updateService, guard, versionService);

        resource.updateProblemStatement(exerciseId, "new statement", null);

        verify(updateService, never()).updateProblemStatement(same(stale), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertGenerationCanClaim(exerciseId);
    }

    @Test
    void updateTimeline_refetchesInsideLeaseAndBlocksGenerationThroughSideEffects() {
        long exerciseId = 44L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exercise(exerciseId);
        ProgrammingExercise fresh = exercise(exerciseId);
        ProgrammingExerciseTimelineUpdateDTO dto = new ProgrammingExerciseTimelineUpdateDTO(exerciseId, null, null, null, null, null, null, null);
        when(repository.findByIdElseThrow(exerciseId)).thenReturn(stale);
        when(repository.findByIdWithBuildConfigElseThrow(exerciseId)).thenReturn(fresh);
        ProgrammingExerciseCreationUpdateService updateService = mock(ProgrammingExerciseCreationUpdateService.class);
        doAnswer(invocation -> {
            assertThat(invocation.getArgument(0, ProgrammingExercise.class)).isSameAs(fresh);
            assertGenerationCannotClaim(exerciseId);
            return fresh;
        }).when(updateService).updateTimeline(same(fresh), same(dto), same(null));
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        doAnswer(invocation -> {
            assertGenerationCannotClaim(exerciseId);
            return null;
        }).when(versionService).createExerciseVersionSynchronously(same(fresh), org.mockito.ArgumentMatchers.any());
        ProgrammingExercisePartialUpdateResource resource = resource(repository, updateService, realGuard(), versionService);

        resource.updateProgrammingExerciseTimeline(dto, null);

        assertGenerationCanClaim(exerciseId);
    }

    @Test
    void updateProblemStatement_holdsMutationSlotUntilSynchronousVersionTailCompletes() throws Exception {
        long exerciseId = 45L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise exercise = exercise(exerciseId);
        when(repository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId)).thenReturn(Optional.of(exercise));
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

            assertGenerationCannotClaim(exerciseId);

            finishVersionTail.countDown();
            update.get(5, TimeUnit.SECONDS);
            assertGenerationCanClaim(exerciseId);
        }
        finally {
            finishVersionTail.countDown();
            executor.shutdownNow();
        }
    }

    private ProgrammingExercisePartialUpdateResource resource(ProgrammingExerciseRepository repository, ProgrammingExerciseCreationUpdateService updateService,
            ProgrammingExerciseMutationGuard mutationGuard) {
        return resource(repository, updateService, mutationGuard, mock(ExerciseVersionService.class));
    }

    private ProgrammingExercisePartialUpdateResource resource(ProgrammingExerciseRepository repository, ProgrammingExerciseCreationUpdateService updateService,
            ProgrammingExerciseMutationGuard mutationGuard, ExerciseVersionService versionService) {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(user("editor"));
        return new ProgrammingExercisePartialUpdateResource(repository, userRepository, mock(AuthorizationCheckService.class), mock(ExerciseService.class), updateService,
                mock(ProgrammingExerciseTaskService.class), versionService, mutationGuard);
    }

    private ProgrammingExerciseMutationGuard realGuard() {
        return new ProgrammingExerciseMutationGuard(Optional.of(new HyperionExerciseMutationApi(generationJobService)), hazelcastInstance);
    }

    private void assertGenerationCannotClaim(long exerciseId) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> generationJobService.startJob(user("generator"), exercise(exerciseId), "generate", GenerationMode.GENERATE));
    }

    private void assertGenerationCanClaim(long exerciseId) {
        String jobId = generationJobService.startJob(user("generator"), exercise(exerciseId), "generate", GenerationMode.GENERATE);
        generationJobService.clearJob(exerciseId, jobId);
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

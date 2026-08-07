package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.HyperionGenerationBudgetService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.localci.service.AutomaticAfterDueDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgrammingExerciseUpdateResourceTest {

    private HazelcastInstance hazelcastInstance;

    private GenerationJobService generationJobService;

    @BeforeAll
    void startHazelcast() {
        Config config = new Config();
        config.setClusterName("full-update-mutation-test-" + System.nanoTime());
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
    void updateProgrammingExercise_refetchesInsideLeaseAndBlocksGenerationBeforeFirstMutation() {
        long exerciseId = 51L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exerciseForAuthorization(exerciseId);
        ProgrammingExercise fresh = exerciseForAuthorization(exerciseId);
        when(repository.findForUpdateByIdElseThrow(exerciseId)).thenReturn(stale, fresh);
        UpdateProgrammingExerciseDTO dto = updateDto(exerciseId);
        doAnswer(invocation -> {
            assertGenerationCannotClaim(exerciseId);
            throw new MutationReached();
        }).when(fresh).setTitle("updated");
        ProgrammingExerciseUpdateResource resource = resource(repository);

        assertThatExceptionOfType(MutationReached.class).isThrownBy(() -> resource.updateProgrammingExercise(dto, null));

        verify(stale, never()).setTitle(anyString());
        assertGenerationCanClaim(exerciseId);
    }

    @Test
    void reEvaluateProgrammingExercise_refetchesInsideLeaseAndBlocksGenerationBeforeFirstMutation() {
        long exerciseId = 52L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exerciseForAuthorization(exerciseId);
        ProgrammingExercise fresh = exerciseForAuthorization(exerciseId);
        when(repository.findForUpdateByIdElseThrow(exerciseId)).thenReturn(stale, fresh);
        UpdateProgrammingExerciseDTO dto = updateDto(exerciseId);
        doAnswer(invocation -> {
            assertGenerationCannotClaim(exerciseId);
            throw new MutationReached();
        }).when(fresh).setTitle("updated");
        ProgrammingExerciseUpdateResource resource = resource(repository);

        assertThatExceptionOfType(MutationReached.class).isThrownBy(() -> resource.reEvaluateAndUpdateProgrammingExercise(exerciseId, dto, false));

        verify(stale, never()).setTitle(anyString());
        assertGenerationCanClaim(exerciseId);
    }

    @Test
    void reEvaluateProgrammingExercise_holdsMutationSlotUntilSynchronousPostSaveTailsComplete() throws Exception {
        long exerciseId = 53L;
        ProgrammingExerciseRepository repository = mock(ProgrammingExerciseRepository.class);
        ProgrammingExercise stale = exerciseForAuthorization(exerciseId);
        ProgrammingExercise fresh = exerciseForAuthorization(exerciseId);
        when(fresh.ensureGradingCriteriaSet()).thenReturn(new HashSet<>());
        when(repository.findForUpdateByIdElseThrow(exerciseId)).thenReturn(stale, fresh);
        UpdateProgrammingExerciseDTO dto = updateDto(exerciseId);
        when(dto.allowOnlineEditor()).thenReturn(true);
        ExerciseService exerciseService = mock(ExerciseService.class);
        ExerciseVersionService versionService = mock(ExerciseVersionService.class);
        ProgrammingExerciseCreationUpdateService updateService = mock(ProgrammingExerciseCreationUpdateService.class);
        when(updateService.updateProgrammingExercise(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(fresh);
        CountDownLatch scoreTailEntered = new CountDownLatch(1);
        CountDownLatch finishScoreTail = new CountDownLatch(1);
        CountDownLatch versionTailEntered = new CountDownLatch(1);
        CountDownLatch finishVersionTail = new CountDownLatch(1);
        doAnswer(invocation -> {
            scoreTailEntered.countDown();
            assertThat(finishScoreTail.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(exerciseService).updatePointsInRelatedParticipantScoresSynchronously(any(), any(), same(fresh));
        doAnswer(invocation -> {
            versionTailEntered.countDown();
            assertThat(finishVersionTail.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(versionService).createExerciseVersionSynchronously(same(fresh), any());
        ProgrammingExerciseUpdateResource resource = resource(repository, exerciseService, versionService, updateService);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> update = executor.submit(() -> resource.reEvaluateAndUpdateProgrammingExercise(exerciseId, dto, false));
            assertThat(scoreTailEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertGenerationCannotClaim(exerciseId);

            finishScoreTail.countDown();
            assertThat(versionTailEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertGenerationCannotClaim(exerciseId);

            finishVersionTail.countDown();
            update.get(5, TimeUnit.SECONDS);
            assertGenerationCanClaim(exerciseId);
        }
        finally {
            finishScoreTail.countDown();
            finishVersionTail.countDown();
            executor.shutdownNow();
        }
    }

    private ProgrammingExerciseUpdateResource resource(ProgrammingExerciseRepository repository) {
        return resource(repository, mock(ExerciseService.class), mock(ExerciseVersionService.class), mock(ProgrammingExerciseCreationUpdateService.class));
    }

    private ProgrammingExerciseUpdateResource resource(ProgrammingExerciseRepository repository, ExerciseService exerciseService, ExerciseVersionService versionService,
            ProgrammingExerciseCreationUpdateService updateService) {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithAuthorities()).thenReturn(user("editor"));
        CourseService courseService = mock(CourseService.class);
        when(courseService.retrieveCourseOverExerciseGroupOrCourseId(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0, ProgrammingExercise.class).getCourseViaExerciseGroupOrCourseMember());
        ProgrammingExerciseMutationGuardService guard = new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(generationJobService)),
                hazelcastInstance);
        return new ProgrammingExerciseUpdateResource(repository, userRepository, mock(AuthorizationCheckService.class), courseService, exerciseService,
                mock(ProgrammingExerciseValidationService.class), updateService, mock(ProgrammingExerciseRepositoryService.class), mock(AuxiliaryRepositoryService.class),
                Optional.<AthenaApi>empty(), mock(ModuleFeatureService.class), Optional.<SlideApi>empty(), Optional.<AutomaticAfterDueDateService>empty(), versionService,
                mock(ParticipationRepository.class), mock(CompetencyExerciseLinkService.class), guard, mock(ExerciseVariantGroupService.class));
    }

    private UpdateProgrammingExerciseDTO updateDto(long exerciseId) {
        UpdateProgrammingExerciseDTO dto = mock(UpdateProgrammingExerciseDTO.class);
        when(dto.id()).thenReturn(exerciseId);
        when(dto.courseId()).thenReturn(1L);
        when(dto.exerciseGroupId()).thenReturn(null);
        when(dto.title()).thenReturn("updated");
        return dto;
    }

    private ProgrammingExercise exerciseForAuthorization(long exerciseId) {
        Course course = new Course();
        course.setId(1L);
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(exerciseId);
        when(exercise.isCourseExercise()).thenReturn(true);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);
        when(exercise.getCompetencyLinks()).thenReturn(Set.of());
        when(exercise.getAuxiliaryRepositories()).thenReturn(List.of());
        return exercise;
    }

    private void assertGenerationCannotClaim(long exerciseId) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> generationJobService.startJob(user("generator"), generatedExercise(exerciseId), "generate", GenerationMode.GENERATE));
    }

    private void assertGenerationCanClaim(long exerciseId) {
        String jobId = generationJobService.startJob(user("generator"), generatedExercise(exerciseId), "generate", GenerationMode.GENERATE);
        generationJobService.clearJob(exerciseId, jobId);
    }

    private static ProgrammingExercise generatedExercise(long exerciseId) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        return exercise;
    }

    private static User user(String login) {
        User user = new User();
        user.setLogin(login);
        return user;
    }

    private static final class MutationReached extends RuntimeException {
    }
}

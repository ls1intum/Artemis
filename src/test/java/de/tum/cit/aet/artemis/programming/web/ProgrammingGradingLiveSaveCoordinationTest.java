package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationScheduleService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDraftGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ProgrammingGradingLiveSaveCoordinationTest {

    @Test
    void gradingEditCannotEnterWhileGeneratedTestsAreSynchronizing() throws Exception {
        long exerciseId = 42L;
        var provider = mock(DistributedDataProvider.class);
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(new LocalMap<>());
        var liveSave = new GenerationLiveSaveService(provider, true);
        var guard = new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(liveSave)), mock(ProgrammingExerciseTestRepository.class));
        var exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        var exercises = mock(ProgrammingExerciseTestRepository.class);
        when(exercises.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId)).thenReturn(exercise);
        var users = mock(UserTestRepository.class);
        var editor = new User();
        editor.setLogin("editor");
        when(users.getUserWithAuthorities()).thenReturn(editor);
        var testCases = mock(ProgrammingExerciseTestCaseService.class);
        when(testCases.update(exerciseId, Set.of())).thenReturn(Set.of());
        var versions = mock(ExerciseVersionService.class);
        var resource = new ProgrammingExerciseTestCaseResource(mock(ProgrammingExerciseTestCaseTestRepository.class), testCases,
                mock(ProgrammingExerciseCreationScheduleService.class), exercises, users, versions, guard, mock(ProgrammingExerciseDraftGuardService.class));
        var synchronizing = new CountDownLatch(1);
        var finishSynchronization = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var save = executor.submit(() -> {
                String token = liveSave.claim(exerciseId);
                try {
                    synchronizing.countDown();
                    assertThat(finishSynchronization.await(10, TimeUnit.SECONDS)).isTrue();
                }
                finally {
                    liveSave.release(exerciseId, token);
                }
                return null;
            });
            try {
                assertThat(synchronizing.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.updateTestCases(exerciseId, Set.of()));
                verifyNoInteractions(testCases, versions);
            }
            finally {
                finishSynchronization.countDown();
            }
            save.get(10, TimeUnit.SECONDS);
        }

        resource.updateTestCases(exerciseId, Set.of());
        verify(testCases).update(exerciseId, Set.of());
        verify(versions).createExerciseVersionSynchronously(exercise, editor);
        assertThat(liveSave.getSlotInfo(exerciseId)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "true,true", "true,false" })
    void resetReloadsOnlyWhenCoordinationRequiresAnAuthoritativeExercise(boolean enabled, boolean exam) {
        long exerciseId = 42L;
        var original = new ProgrammingExercise();
        original.setId(exerciseId);
        original.setCourse(new Course());
        var fresh = new ProgrammingExercise();
        fresh.setId(exerciseId);
        fresh.setCourse(new Course());
        var exercises = mock(ProgrammingExerciseTestRepository.class);
        when(exercises.findByIdElseThrow(exerciseId)).thenReturn(original, fresh);
        when(exercises.existsByIdAndExerciseGroupIsNotNull(exerciseId)).thenReturn(exam);
        var provider = mock(DistributedDataProvider.class);
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(new LocalMap<>());
        var liveSave = new GenerationLiveSaveService(provider, enabled);
        var guard = new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(liveSave)), exercises);
        var users = mock(UserTestRepository.class);
        var editor = new User();
        when(users.getUserWithAuthorities()).thenReturn(editor);
        var testCases = mock(ProgrammingExerciseTestCaseService.class);
        var expected = enabled && !exam ? fresh : original;
        when(testCases.reset(expected)).thenReturn(java.util.List.of());
        var versions = mock(ExerciseVersionService.class);
        var resource = new ProgrammingExerciseTestCaseResource(mock(ProgrammingExerciseTestCaseTestRepository.class), testCases,
                mock(ProgrammingExerciseCreationScheduleService.class), exercises, users, versions, guard, mock(ProgrammingExerciseDraftGuardService.class));

        resource.resetTestCases(exerciseId);

        verify(exercises, times(enabled && !exam ? 2 : 1)).findByIdElseThrow(exerciseId);
        verify(testCases).reset(expected);
        if (enabled && !exam) {
            verify(versions).createExerciseVersionSynchronously(fresh, editor);
        }
        else {
            verify(versions).createExerciseVersion(original, editor);
        }
        assertThat(liveSave.getSlotInfo(exerciseId)).isEmpty();
    }
}

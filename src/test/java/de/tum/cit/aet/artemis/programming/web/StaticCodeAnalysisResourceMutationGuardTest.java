package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class StaticCodeAnalysisResourceMutationGuardTest {

    static Stream<Arguments> scopes() {
        return Stream.of("update", "reset", "import")
                .flatMap(operation -> Stream.of(Arguments.of(operation, false, false), Arguments.of(operation, true, true), Arguments.of(operation, true, false)));
    }

    @ParameterizedTest
    @MethodSource("scopes")
    void versionsAreCapturedOnlyForCoordinatedCourseOperations(String operation, boolean enabled, boolean exam) {
        Long exerciseId = 42L;
        var exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        exercise.setStaticCodeAnalysisEnabled(true);
        var exercises = mock(ProgrammingExerciseTestRepository.class);
        when(exercises.findByIdElseThrow(exerciseId)).thenReturn(exercise);
        when(exercises.existsByIdAndExerciseGroupIsNotNull(exerciseId)).thenReturn(exam);
        var provider = mock(DistributedDataProvider.class);
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(new LocalMap<>());
        var liveSave = new GenerationLiveSaveService(provider, enabled);
        var guard = new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(liveSave)), exercises);
        var users = mock(UserTestRepository.class);
        var editor = new User();
        if (enabled && !exam) {
            when(users.getUser()).thenReturn(editor);
        }
        var versions = mock(ExerciseVersionService.class);
        var categories = mock(StaticCodeAnalysisService.class);
        var resource = new StaticCodeAnalysisResource(mock(AuthorizationCheckService.class), exercises, categories, mock(StaticCodeAnalysisCategoryRepository.class), guard, users,
                versions);
        switch (operation) {
            case "update" -> {
                when(categories.updateCategories(exerciseId, List.of())).thenReturn(Set.of());
                resource.updateStaticCodeAnalysisCategories(exerciseId, List.of());
                verify(categories).updateCategories(exerciseId, List.of());
            }
            case "reset" -> {
                when(categories.resetCategories(exercise)).thenReturn(Set.of());
                resource.resetStaticCodeAnalysisCategories(exerciseId);
                verify(categories).resetCategories(exercise);
            }
            case "import" -> {
                when(categories.importCategoriesFromExercise(exercise, exercise)).thenReturn(Set.of());
                resource.importStaticCodeAnalysisCategoriesFromExercise(exerciseId, exerciseId);
                verify(categories).importCategoriesFromExercise(exercise, exercise);
            }
            default -> throw new AssertionError(operation);
        }
        if (enabled && !exam) {
            verify(versions).createExerciseVersionSynchronously(exercise, editor);
        }
        else {
            verifyNoInteractions(versions, users);
        }
        assertThat(liveSave.getSlotInfo(exerciseId)).isEmpty();
    }
}

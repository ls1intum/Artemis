package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

class ExerciseVariantGroupLiveSaveTest {

    @Test
    void rejectedGroupUpdateReleasesEarlierMembersAndWritesNothing() {
        var provider = mock(DistributedDataProvider.class);
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(new LocalMap<>());
        var saves = new GenerationLiveSaveService(provider, true);
        var groups = mock(ExerciseVariantGroupRepository.class);
        var service = service(groups, saves);
        var first = new ProgrammingExercise();
        first.setId(1L);
        var second = new ProgrammingExercise();
        second.setId(2L);
        var group = new ExerciseVariantGroup();
        group.setExercises(Set.of(second, first));
        // A distinct execution context owns the generation reservation; the group operation must not borrow it.
        String token;
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            token = executor.submit(() -> saves.claim(2)).get();
            assertThatThrownBy(() -> service.saveWithTimelineAppliedToMembers(group)).isInstanceOf(ConflictException.class);
            assertThat(saves.getSlotInfo(1)).isEmpty();
            assertThat(saves.getSlotInfo(2)).isPresent();
            verify(groups, never()).save(any());
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
        assertThat(saves.recoverSlot(2, token)).isTrue();
    }

    @Test
    void deletionCannotChangeMembershipDuringLiveSave() throws Exception {
        var provider = mock(DistributedDataProvider.class);
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(new LocalMap<>());
        var saves = new GenerationLiveSaveService(provider, true);
        var groups = mock(ExerciseVariantGroupRepository.class);
        var service = service(groups, saves);
        var member = new ProgrammingExercise();
        member.setId(42L);
        var group = new ExerciseVariantGroup();
        group.setExercises(Set.of(member));
        when(groups.findByIdAndCourseIdElseThrow(7L, 3L)).thenReturn(group);
        when(groups.findByIdAndCourseIdWithoutExercisesElseThrow(7L, 3L)).thenReturn(group);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            String token = executor.submit(() -> saves.claim(42)).get();
            assertThatThrownBy(() -> service.deleteGroup(7L, 3L)).isInstanceOf(ConflictException.class);
            verify(groups, never()).delete(any(ExerciseVariantGroup.class));
            executor.submit(() -> saves.release(42, token)).get();
        }
        service.deleteGroup(7L, 3L);
        verify(groups).delete(group);
    }

    private ExerciseVariantGroupService service(ExerciseVariantGroupRepository groups, GenerationLiveSaveService saves) {
        return new ExerciseVariantGroupService(groups, mock(ExerciseTestRepository.class), mock(CourseTestRepository.class), mock(ProgrammingExerciseCreationUpdateService.class),
                mock(ParticipationTestRepository.class), mock(ExerciseService.class), mock(ExerciseVersionService.class), mock(InstanceMessageSendService.class),
                mock(QuizExerciseService.class), Optional.empty(),
                new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(saves)), mock(ProgrammingExerciseTestRepository.class)),
                mock(ProgrammingExerciseTestRepository.class));
    }
}

package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService.MutationLease;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class ExerciseVariantGroupServiceTest {

    @Mock
    private ExerciseVariantGroupRepository groupRepository;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private ProgrammingExerciseCreationUpdateService programmingUpdateService;

    @Mock
    private ParticipationTestRepository participationRepository;

    @Mock
    private ExerciseService exerciseService;

    @Mock
    private ExerciseVersionService versionService;

    @Mock
    private InstanceMessageSendService messages;

    @Mock
    private QuizExerciseService quizService;

    @Mock
    private ProgrammingExerciseMutationGuardService mutationGuard;

    @Mock
    private ProgrammingExerciseTestRepository programmingRepository;

    @Mock
    private Runnable release;

    private ExerciseVariantGroupService service;

    @BeforeEach
    void setUp() {
        service = new ExerciseVariantGroupService(groupRepository, exerciseRepository, courseRepository, programmingUpdateService, participationRepository, exerciseService,
                versionService, messages, quizService, Optional.empty(), mutationGuard, programmingRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void busyProgrammingMemberRejectsWholeGroupUpdateAndReleasesEarlierClaims(boolean releaseFails) {
        var group = new ExerciseVariantGroup();
        var first = programmingExercise(1);
        var busy = programmingExercise(2);
        var text = new TextExercise();
        text.setId(3L);
        group.addExercise(first);
        group.addExercise(busy);
        group.addExercise(text);
        group.setDueDate(ZonedDateTime.now().plusDays(2));
        when(mutationGuard.claimExternalMutation(1L)).thenReturn(new MutationLease(release));
        when(mutationGuard.claimExternalMutation(2L)).thenThrow(new ConflictException("Generation is active", "exercise", "generationActive"));

        var releaseFailure = new IllegalStateException("Release unavailable");
        if (releaseFails) {
            doThrow(releaseFailure).when(release).run();
        }
        assertThatThrownBy(() -> service.saveWithTimelineAppliedToMembers(group)).isInstanceOf(ConflictException.class)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(releaseFails ? new Throwable[] { releaseFailure } : new Throwable[0]));

        verifyNoInteractions(groupRepository, exerciseRepository, courseRepository, programmingUpdateService, programmingRepository, versionService);
        assertThat(first.getDueDate()).isNull();
        assertThat(busy.getDueDate()).isNull();
        assertThat(text.getDueDate()).isNull();
        verify(release).run();
    }

    @Test
    void busyAssignmentDoesNotAdoptDatesOrSaveMembership() {
        var exercise = programmingExercise(1);
        exercise.setDueDate(ZonedDateTime.now().plusDays(2));
        var group = new ExerciseVariantGroup();
        when(mutationGuard.claimExternalMutation(1L)).thenThrow(new ConflictException("Generation is active", "exercise", "generationActive"));

        assertThatThrownBy(() -> service.assignToGroup(exercise, group)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(groupRepository, exerciseRepository, courseRepository, programmingUpdateService, programmingRepository);
        assertThat(group.getDueDate()).isNull();
        assertThat(exercise.getExerciseVariantGroup()).isNull();
    }

    @Test
    void unassignmentReloadsExerciseAfterClaimInsteadOfOverwritingGeneratedContent() {
        var stale = programmingExercise(1);
        stale.setProblemStatement("Old statement");
        var current = programmingExercise(1);
        current.setProblemStatement("Generated statement");
        current.setExerciseVariantGroup(new ExerciseVariantGroup());
        when(mutationGuard.claimExternalMutation(1L)).thenReturn(new MutationLease(release));
        when(programmingRepository.findByIdWithBuildConfigElseThrow(1L)).thenAnswer(invocation -> {
            verify(mutationGuard).claimExternalMutation(1L);
            return current;
        });
        when(exerciseRepository.save(current)).thenReturn(current);

        service.assignToGroup(stale, null);

        verify(exerciseRepository).save(same(current));
        assertThat(current.getProblemStatement()).isEqualTo("Generated statement");
        assertThat(current.getExerciseVariantGroup()).isNull();
        verify(programmingUpdateService, never()).updateTimeline(any(), any());
        verify(release).run();
    }

    private static ProgrammingExercise programmingExercise(long id) {
        var exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }
}

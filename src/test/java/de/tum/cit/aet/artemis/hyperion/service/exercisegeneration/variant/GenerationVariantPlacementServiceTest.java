package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

@ExtendWith(MockitoExtension.class)
class GenerationVariantPlacementServiceTest {

    @Mock
    private ExerciseVariantGroupRepository groups;

    @Mock
    private ExerciseVariantGroupService groupService;

    @Mock
    private ProgrammingExerciseTestRepository exercises;

    @Mock
    private Runnable ownership;

    private GenerationVariantPlacementService service;

    private ProgrammingExercise source;

    private ProgrammingExercise destination;

    @BeforeEach
    void setUp() {
        service = new GenerationVariantPlacementService(groups, groupService, exercises);
        Course course = new Course();
        course.setId(10L);
        source = new ProgrammingExercise();
        source.setId(1L);
        source.setCourse(course);
        destination = new ProgrammingExercise();
        destination.setId(2L);
        destination.setCourse(course);
    }

    private VariantGenerationRequestDTO request(VariantPlacementDTO.PlacementType type) {
        return new VariantGenerationRequestDTO(null, "Library", null, null,
                new VariantPlacementDTO(type, 30L, new CreateExerciseVariantGroupDTO("Variants", null, null, null, null, null, null)));
    }

    private GenerationStartedEvent event(VariantPlacementDTO.PlacementType type) {
        return new GenerationStartedEvent("job", null, destination, "Library", GenerationMode.ADAPT, null, null, null, null, null, null,
                new GenerationVariantPreparation(1L, "copy", request(type)));
    }

    @Test
    void standaloneHasNoPlacementSideEffects() {
        assertThat(service.place(event(VariantPlacementDTO.PlacementType.STANDALONE), ownership)).isEmpty();
        verifyNoInteractions(groups, groupService, exercises, ownership);
    }

    @Test
    void lostOwnershipDoesNotCreateAGroupOrChangeExercises() {
        doThrow(new IllegalStateException("superseded")).when(ownership).run();
        assertThatThrownBy(() -> service.place(event(VariantPlacementDTO.PlacementType.NEW_GROUP), ownership)).hasMessage("superseded");
        verifyNoInteractions(groups, groupService, exercises);
    }

    @Test
    void existingGroupIsResolvedInTheDestinationCourseAndDoesNotMoveSource() {
        var group = new ExerciseVariantGroup();
        when(exercises.findByIdElseThrow(Long.valueOf(2L))).thenReturn(destination);
        when(groups.findByIdAndCourseIdElseThrow(30L, 10L)).thenReturn(group);

        assertThat(service.place(event(VariantPlacementDTO.PlacementType.EXISTING_GROUP), ownership)).isEmpty();

        verify(groupService).assignToGroupWhileAuthoring(2L, group, ownership);
        var order = inOrder(ownership, exercises, groups, groupService);
        order.verify(ownership).run();
        order.verify(exercises).findByIdElseThrow(Long.valueOf(2L));
        order.verify(groups).findByIdAndCourseIdElseThrow(30L, 10L);
        order.verify(groupService).assignToGroupWhileAuthoring(2L, group, ownership);
        order.verifyNoMoreInteractions();
    }

    @Test
    void newGroupSeedsSourceDatesRatherThanDraftHoldDateAndWarnsIfSourceCannotJoin() {
        var group = new ExerciseVariantGroup();
        when(exercises.findByIdElseThrow(Long.valueOf(2L))).thenReturn(destination);
        when(exercises.findByIdElseThrow(1L)).thenReturn(source);
        when(groupService.createGroup(eq(10L), any())).thenReturn(group);
        when(groups.findByExerciseId(1L)).thenReturn(Optional.empty());

        assertThat(service.place(event(VariantPlacementDTO.PlacementType.NEW_GROUP), ownership)).singleElement().asString().contains("source is no longer eligible");

        var order = inOrder(groupService);
        order.verify(groupService).createGroup(eq(10L), any());
        order.verify(groupService).seedGroupDatesFromExercise(group, source);
        order.verify(groupService).assignToGroupWhileAuthoring(2L, group, ownership);
        order.verify(groupService).assignProgrammingSourceIfUngrouped(1L, group);
    }

    @Test
    void alreadyGroupedSourceRejectsNewGroupBeforeProvisioning() {
        when(groups.findByExerciseId(1L)).thenReturn(Optional.of(new ExerciseVariantGroup()));
        assertThatThrownBy(() -> service.validate(source, request(VariantPlacementDTO.PlacementType.NEW_GROUP))).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("already belongs");
        verifyNoInteractions(groupService, exercises);
    }

    @Test
    void examSourceOnlyAcceptsItsOwnExamGroup() {
        var exam = new Exam();
        exam.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        var group = new ExerciseGroup();
        group.setExam(exam);
        source.setCourse(null);
        source.setExerciseGroup(group);

        assertThatThrownBy(() -> service.validate(source, request(VariantPlacementDTO.PlacementType.STANDALONE))).isInstanceOf(BadRequestAlertException.class);
        service.validate(source, request(VariantPlacementDTO.PlacementType.SAME_EXAM_GROUP));
        verifyNoInteractions(groups, groupService, exercises);
    }
}

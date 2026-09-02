package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

/**
 * NEW_GROUP placement persists a group before its members can join it, and a quiz that has started or ended is
 * rejected by {@code ExerciseVariantGroupService.assignToGroup} — joining stamps the group's timeline onto the
 * member. Because a variant quiz inherits the source's dates, the source starting during a long generation makes
 * both of them ineligible, and an unguarded placement would leave an empty group behind with the variant
 * standalone. These tests pin that no group outlives a placement it cannot complete.
 */
class VariantPlacementServiceNewGroupTest {

    private static final long COURSE_ID = 4L;

    private static final long SOURCE_ID = 11L;

    private static final long VARIANT_ID = 12L;

    private ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private ExerciseVariantGroupService exerciseVariantGroupService;

    private ExerciseTestRepository exerciseRepository;

    private VariantPlacementService placementService;

    private Course course;

    private QuizExercise variant;

    private QuizExercise source;

    private ExerciseVariantGroup createdGroup;

    @BeforeEach
    void setUp() {
        exerciseVariantGroupRepository = mock(ExerciseVariantGroupRepository.class);
        exerciseVariantGroupService = mock(ExerciseVariantGroupService.class);
        exerciseRepository = mock(ExerciseTestRepository.class);
        placementService = new VariantPlacementService(exerciseVariantGroupRepository, exerciseVariantGroupService, exerciseRepository);

        course = new Course();
        course.setId(COURSE_ID);

        variant = individualQuiz(VARIANT_ID);
        source = individualQuiz(SOURCE_ID);
        when(exerciseRepository.findByIdElseThrow(SOURCE_ID)).thenReturn(source);

        createdGroup = new ExerciseVariantGroup();
        createdGroup.setId(77L);
        when(exerciseVariantGroupService.createGroup(anyLong(), any())).thenReturn(createdGroup);
        // Editable unless a test says otherwise — the production predicate answers this for both members.
        when(exerciseVariantGroupService.canJoinGroup(any())).thenReturn(true);
    }

    @Test
    void doesNotCreateTheGroupWhenTheVariantQuizCanNoLongerJoinOne() {
        when(exerciseVariantGroupService.canJoinGroup(variant)).thenReturn(false);

        List<String> warnings = placementService.place(variant, SOURCE_ID, newGroupRequest());

        verify(exerciseVariantGroupService, never()).createGroup(anyLong(), any());
        verify(exerciseVariantGroupService, never()).assignToGroup(any(), any());
        assertThat(warnings).singleElement().asString().contains("left ungrouped").contains("started or ended");
    }

    @Test
    void placesTheVariantAndReportsASourceQuizThatCanNoLongerJoin() {
        when(exerciseVariantGroupService.canJoinGroup(source)).thenReturn(false);

        List<String> warnings = placementService.place(variant, SOURCE_ID, newGroupRequest());

        verify(exerciseVariantGroupService).assignToGroup(variant, createdGroup);
        verify(exerciseVariantGroupService, never()).assignToGroup(source, createdGroup);
        verify(exerciseVariantGroupRepository, never()).delete(any());
        assertThat(warnings).singleElement().asString().contains("its source exercise was not added").contains("started or ended");
    }

    @Test
    void dropsTheNewGroupWhenTheVariantIsRejectedAfterItWasCreated() {
        // The batch starts between the eligibility check and the assignment: both members stay out, so the group
        // that was persisted for them must not survive.
        when(exerciseVariantGroupService.canJoinGroup(source)).thenReturn(false);
        doThrow(new BadRequestAlertException("quiz started", "exerciseVariantGroup", "quizMemberNotEditable")).when(exerciseVariantGroupService).assignToGroup(variant,
                createdGroup);

        List<String> warnings = placementService.place(variant, SOURCE_ID, newGroupRequest());

        verify(exerciseVariantGroupRepository).delete(createdGroup);
        assertThat(warnings).singleElement().asString().contains("left ungrouped");
    }

    @Test
    void keepsTheGroupHoldingTheSourceWhenOnlyTheVariantIsRejected() {
        doThrow(new BadRequestAlertException("quiz started", "exerciseVariantGroup", "quizMemberNotEditable")).when(exerciseVariantGroupService).assignToGroup(variant,
                createdGroup);

        List<String> warnings = placementService.place(variant, SOURCE_ID, newGroupRequest());

        verify(exerciseVariantGroupService).assignToGroup(source, createdGroup);
        verify(exerciseVariantGroupRepository, never()).delete(any());
        assertThat(warnings).singleElement().asString().contains("left ungrouped");
    }

    private QuizExercise individualQuiz(long id) {
        QuizExercise quiz = new QuizExercise();
        quiz.setId(id);
        quiz.setCourse(course);
        quiz.setQuizMode(QuizMode.INDIVIDUAL);
        return quiz;
    }

    private static VariantGenerationRequestDTO newGroupRequest() {
        CreateExerciseVariantGroupDTO group = new CreateExerciseVariantGroupDTO("Variants of the quiz", null, null, null, null, null, null);
        return new VariantGenerationRequestDTO(null, null, null, null, new VariantPlacementDTO(VariantPlacementDTO.PlacementType.NEW_GROUP, null, group));
    }
}

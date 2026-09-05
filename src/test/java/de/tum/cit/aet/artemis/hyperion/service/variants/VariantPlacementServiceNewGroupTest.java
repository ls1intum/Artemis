package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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
 * both of them ineligible. These tests pin that a placement which cannot be completed leaves nothing behind: no
 * empty group, and above all no membership or rewritten timeline on the instructor's own source exercise.
 */
class VariantPlacementServiceNewGroupTest {

    private static final long COURSE_ID = 4L;

    private static final long SOURCE_ID = 11L;

    private static final long VARIANT_ID = 12L;

    private static final long GROUP_ID = 77L;

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
        createdGroup.setId(GROUP_ID);
        when(exerciseVariantGroupService.createGroup(anyLong(), any())).thenReturn(createdGroup);
        // The source is claimed atomically before it joins; it is ungrouped unless a test says another job won it.
        when(exerciseVariantGroupRepository.claimExerciseIfUngrouped(SOURCE_ID, GROUP_ID)).thenReturn(1);
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
        verify(exerciseVariantGroupService, never()).adoptMissingDatesFromExercise(createdGroup, source);
        verify(exerciseVariantGroupRepository, never()).delete(any());
        assertThat(warnings).singleElement().asString().contains("its source exercise was not added").contains("started or ended");
    }

    @Test
    void leavesTheSourceUntouchedWhenTheVariantIsRejectedAfterTheGroupWasCreated() {
        // The batch starts between the eligibility check and the assignment. The source must not have been
        // persisted into the group by then: undoing that would mean rewriting the instructor's own exercise dates.
        doThrow(new BadRequestAlertException("quiz started", "exerciseVariantGroup", "quizMemberNotEditable")).when(exerciseVariantGroupService).assignToGroup(variant,
                createdGroup);

        List<String> warnings = placementService.place(variant, SOURCE_ID, newGroupRequest());

        verify(exerciseVariantGroupService, never()).assignToGroup(source, createdGroup);
        assertThat(source.getExerciseVariantGroup()).isNull();
        verify(exerciseVariantGroupRepository).delete(createdGroup);
        assertThat(warnings).singleElement().asString().contains("left ungrouped");
    }

    @Test
    void takesTheGroupsUnsetDatesFromTheSourceBeforeAnyoneJoins() {
        placementService.place(variant, SOURCE_ID, newGroupRequest());

        InOrder inOrder = inOrder(exerciseVariantGroupService);
        inOrder.verify(exerciseVariantGroupService).adoptMissingDatesFromExercise(createdGroup, source);
        inOrder.verify(exerciseVariantGroupService).assignToGroup(variant, createdGroup);
        inOrder.verify(exerciseVariantGroupService).assignToGroup(source, createdGroup);
    }

    @Test
    void reportsASourceThatIsRejectedWhenItIsAddedAfterTheVariant() {
        doThrow(new BadRequestAlertException("quiz started", "exerciseVariantGroup", "quizMemberNotEditable")).when(exerciseVariantGroupService).assignToGroup(source,
                createdGroup);

        List<String> warnings = placementService.place(variant, SOURCE_ID, newGroupRequest());

        verify(exerciseVariantGroupService).assignToGroup(variant, createdGroup);
        verify(exerciseVariantGroupRepository, never()).delete(any());
        assertThat(warnings).singleElement().asString().contains("its source exercise was not added");
    }

    @Test
    void propagatesAnAssignmentFailureThatIsNotTheQuizRaceInsteadOfDroppingTheGroup() {
        // A programming member's timeline update rejects AFTER membership was persisted, so treating this like the
        // quiz race would delete a group that already has a member.
        BadRequestAlertException invalidBuildPlan = invalidBuildPlanConfiguration();
        doThrow(invalidBuildPlan).when(exerciseVariantGroupService).assignToGroup(variant, createdGroup);

        assertThat(catchThrowable(() -> placementService.place(variant, SOURCE_ID, newGroupRequest()))).isSameAs(invalidBuildPlan);
        verify(exerciseVariantGroupRepository, never()).delete(any());
    }

    @Test
    void propagatesASourceAssignmentFailureThatIsNotTheQuizRace() {
        BadRequestAlertException invalidBuildPlan = invalidBuildPlanConfiguration();
        doThrow(invalidBuildPlan).when(exerciseVariantGroupService).assignToGroup(source, createdGroup);

        assertThat(catchThrowable(() -> placementService.place(variant, SOURCE_ID, newGroupRequest()))).isSameAs(invalidBuildPlan);
        // The variant is placed before the source, so its membership — and the group holding it — survive the failure.
        verify(exerciseVariantGroupService).assignToGroup(variant, createdGroup);
        verify(exerciseVariantGroupRepository, never()).delete(any());
    }

    @Test
    void leavesTheSourceInTheGroupAParallelJobClaimedItFor() {
        // Both jobs were generated from the same source and both read it as ungrouped. The other one claimed it
        // first; assigning on this job's stale reading would take the source out of the group that job created.
        when(exerciseVariantGroupRepository.claimExerciseIfUngrouped(SOURCE_ID, GROUP_ID)).thenReturn(0);

        List<String> warnings = placementService.place(variant, SOURCE_ID, newGroupRequest());

        verify(exerciseVariantGroupService).assignToGroup(variant, createdGroup);
        verify(exerciseVariantGroupService, never()).assignToGroup(source, createdGroup);
        verify(exerciseVariantGroupRepository, never()).delete(any());
        assertThat(warnings).singleElement().asString().contains("its source exercise was not added").contains("already belongs to another variant group");
    }

    @Test
    void givesTheClaimBackWhenTheSourceQuizStartedBeforeItCouldJoin() {
        // The claim is the only thing written before the assignment's own editability check rejects, so it must not
        // outlive it: the source would be a member of a group whose timeline it never adopted.
        doThrow(new BadRequestAlertException("quiz started", "exerciseVariantGroup", "quizMemberNotEditable")).when(exerciseVariantGroupService).assignToGroup(source,
                createdGroup);

        placementService.place(variant, SOURCE_ID, newGroupRequest());

        verify(exerciseVariantGroupRepository).releaseExerciseFromGroup(SOURCE_ID, GROUP_ID);
    }

    @Test
    void keepsTheClaimWhenTheSourceAssignmentFailsForAReasonThatMayHavePersistedMembership() {
        doThrow(invalidBuildPlanConfiguration()).when(exerciseVariantGroupService).assignToGroup(source, createdGroup);

        catchThrowable(() -> placementService.place(variant, SOURCE_ID, newGroupRequest()));

        verify(exerciseVariantGroupRepository, never()).releaseExerciseFromGroup(anyLong(), anyLong());
    }

    /** The rejection a programming member's timeline update raises after its membership was already persisted. */
    private static BadRequestAlertException invalidBuildPlanConfiguration() {
        return new BadRequestAlertException("invalid build plan", "programmingExercise", "invalidBuildPlanConfiguration");
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

package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies the guard that decides whether an exam exercise may be moved between exercise groups.
 * <p>
 * The guard is a {@code NOT EXISTS} clause inside the update statement rather than a check preceding it, so it cannot
 * be interleaved: there is no state in which a student exam commits between the check and the write. That property is
 * invisible from the service, which only sees a boolean, so it is covered here.
 */
class ExerciseGroupMoveTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exercisegroupmove";

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExerciseGroupService exerciseGroupService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    private Exam exam;

    private List<Long> testRunExerciseIds;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        Course course = courseUtilService.addEmptyCourse();
        exam = examUtilService.addExam(course);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
        // The client builds this list by picking one exercise per group, which is the selection the guard protects.
        testRunExerciseIds = exam.getExerciseGroups().stream().map(group -> group.getExercises().iterator().next().getId()).toList();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMoveSucceedsWhileTheExamHasNoStudentExam() {
        ExerciseGroup targetGroup = exam.getExerciseGroups().get(1);
        Exercise movedExercise = exam.getExerciseGroups().getFirst().getExercises().iterator().next();

        exerciseGroupService.moveExerciseToGroup(exam.getId(), movedExercise.getId(), targetGroup.getId());

        assertThat(groupIdOf(movedExercise)).as("the exercise must have landed in the target group").isEqualTo(targetGroup.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testMoveIsRejectedOnceAStudentExamExists() {
        // A test run counts: it has already picked one exercise per group, so a move would desync that selection.
        studentExamService.createTestRun(exam, testRunExerciseIds, 6000);

        ExerciseGroup sourceGroup = exam.getExerciseGroups().getFirst();
        ExerciseGroup targetGroup = exam.getExerciseGroups().get(1);
        Exercise movedExercise = sourceGroup.getExercises().iterator().next();

        assertThatThrownBy(() -> exerciseGroupService.moveExerciseToGroup(exam.getId(), movedExercise.getId(), targetGroup.getId())).isInstanceOf(ConflictException.class);

        assertThat(groupIdOf(movedExercise)).as("a rejected move must not have written anything").isEqualTo(sourceGroup.getId());
    }

    private Long groupIdOf(Exercise exercise) {
        return exerciseRepository.findExerciseAndGroupIdsByExerciseIds(List.of(exercise.getId())).getFirst().exerciseGroupId();
    }
}

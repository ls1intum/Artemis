package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A {@code UserStoryExercise} is a {@link ProgrammingExercise} that must always belong to a
 * {@code MilestoneExerciseGroup}. Its Language/Version-Control settings and repositories are kept in sync with the
 * group's {@link MilestoneExercise} (see {@code UserStoryExerciseService.applyMilestoneConfig}, invoked on create and
 * on every group (re-)assignment) rather than being independently configurable.
 * <p>
 * Its test cases are its own rows (standard {@link #getTestCases()}, not overridden) - duplicated from the milestone's
 * shared test suite by {@code UserStoryExerciseService.syncTestCasesFromMilestone} rather than referenced live, since
 * the grading/task pipeline resolves test cases via a direct database query keyed by this exercise's own id, not
 * through the object graph. Only its own {@link ProgrammingExerciseTask}s (parsed from its own problem statement)
 * determine which of its test cases are marked {@code active} and therefore count toward its (independently
 * configurable) grade - see {@code UserStoryExerciseService.updateRelevantTestCases}.
 * <p>
 * Its {@code includedInOverallScore} stays {@code INCLUDED_COMPLETELY} and is not instructor-editable (see
 * {@code USER_STORY_HIDDEN_FIELDS} client-side): a user story's points genuinely do count, they are simply counted
 * through its group. Marking it {@code NOT_INCLUDED} would be read by every UI as "these points do not count", the
 * opposite of the truth. Double counting is prevented where it belongs - in the score calculation, which skips the
 * members of a milestone group because their {@link MilestoneExercise} already accounts for the whole group; see
 * {@code CourseScoreCalculator.includeIntoScoreCalculation}.
 */
@Entity
@DiscriminatorValue("US")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserStoryExercise extends ProgrammingExercise {

    @Override
    public String getType() {
        return "user-story";
    }
}

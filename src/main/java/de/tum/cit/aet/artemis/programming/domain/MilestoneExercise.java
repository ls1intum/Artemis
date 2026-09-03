package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A {@code MilestoneExercise} is the anchor of a {@code MilestoneExerciseGroup}: a full {@link ProgrammingExercise} that
 * genuinely owns the shared template/solution/test repositories, build plan, and test cases the group's
 * {@link UserStoryExercise}s work against and are graded from.
 * <p>
 * It is created and deleted together with its {@code MilestoneExerciseGroup} (see the group's {@code milestoneExercise}
 * field) and is never rendered to students.
 * <p>
 * It is, however, the <b>only</b> scored exercise of its group: its {@code maxPoints} is kept equal to the sum of its
 * user stories' points, and the points a student achieves on it are the sum of the points they achieved on those user
 * stories minus the group's static code analysis penalty. Its {@code UserStoryExercise} members are
 * {@link de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore#NOT_INCLUDED} so nothing double-counts.
 * Static code analysis describes the shared codebase and is therefore configured and priced here, once, rather than
 * on each user story - see {@code MilestoneScoreService}.
 */
@Entity
@DiscriminatorValue("MS")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MilestoneExercise extends ProgrammingExercise {

    @Override
    public String getType() {
        return "milestone";
    }

    /**
     * A milestone exercise is never rendered to students (see the class-level javadoc) - only its {@link UserStoryExercise}
     * members are. Always {@code false} regardless of release date, which is the single authoritative gate every
     * student-facing exercise access goes through ({@code AuthorizationCheckService.isAllowedToSeeCourseExercise} and
     * {@code ExerciseService.filterExercisesForCourse}).
     * <p>
     * Note that this is independent of scoring: the milestone still carries the group's points (see the class-level
     * javadoc), it is just not listed as an exercise of its own. The per-story breakdown is what students see, in the
     * group detail view.
     */
    @Override
    public boolean isVisibleToStudents() {
        return false;
    }
}

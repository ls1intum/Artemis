package de.tum.cit.aet.artemis.localci.service.ci;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;

/**
 * The inputs a build trigger needs that are identical for every participation of one exercise.
 * <p>
 * Triggering a build for one participation resolves several values that do not depend on the participation at all: the
 * head commit of the exercise's test repository, and the exercise's build statistics. Triggering a build for every
 * participation of an exercise, which is what an instructor's "build all" and the build-and-test-after-due-date
 * schedule do, therefore resolved the same values once per student. On a course of two thousand that is two thousand
 * reads of one git repository and two thousand reads of one row, all on the node handling the request.
 * <p>
 * A caller that is about to trigger many participations of the same exercise resolves them once through
 * {@link ContinuousIntegrationTriggerService#prepareSharedTriggerData} and passes the result to every trigger. Deliberately a
 * value passed down the call stack rather than a cache: nothing has to be invalidated when an instructor edits the
 * exercise, because the next batch resolves it again.
 *
 * @param resolved        whether the caller resolved these values; when false the trigger resolves what it needs itself
 * @param testCommitHash  the head commit of the exercise's test repository, null if the repository has none
 * @param buildStatistics the exercise's build statistics, null if the exercise has none yet
 */
public record SharedBuildTriggerData(boolean resolved, @Nullable String testCommitHash, @Nullable ProgrammingExerciseBuildStatistics buildStatistics) {

    /** Nothing was resolved in advance, so each trigger resolves what it needs itself. */
    public static final SharedBuildTriggerData NONE = new SharedBuildTriggerData(false, null, null);

    /**
     * Values the caller resolved for a whole exercise. A null in either of them is an answer, not a missing value: the
     * exercise has no build statistics yet, or its test repository has no head commit, and both hold for every
     * participation of that exercise. Marking them resolved is what keeps the trigger from asking again per student.
     *
     * @param testCommitHash  the head commit of the exercise's test repository, null if the repository has none
     * @param buildStatistics the exercise's build statistics, null if the exercise has none yet
     * @return the resolved values
     */
    public static SharedBuildTriggerData of(@Nullable String testCommitHash, @Nullable ProgrammingExerciseBuildStatistics buildStatistics) {
        return new SharedBuildTriggerData(true, testCommitHash, buildStatistics);
    }
}

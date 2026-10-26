package de.tum.cit.aet.artemis.localci.service.ci;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;

/**
 * Abstract service for managing entities related to continuous integration.
 */
public interface ContinuousIntegrationTriggerService {

    /**
     * triggers a build for the build plan in the given participation
     *
     * @param participation the participation with the id of the build plan that should be triggered
     * @param triggerAll    true if this build was triggered as part of a trigger all request. Currently only used for Local CI.
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll) throws ContinuousIntegrationException;

    /**
     * Triggers a build for the build plan in the given participation with an optional commit hash.
     *
     * @param participation     the participation with the id of the build plan that should be triggered
     * @param commitHash        the commit hash to be used for the build trigger
     * @param triggeredByPushTo type of the repository that was pushed to and triggered the build job
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    default void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo) throws ContinuousIntegrationException {
        triggerBuild(participation, false);
    }

    /**
     * Triggers a build using the operator-controlled generation network policy and without exercise-provided environment variables. CI implementations that cannot enforce this
     * boundary must reject the request.
     *
     * @param participation     the participation whose build should be triggered
     * @param commitHash        the commit hash to build
     * @param triggeredByPushTo the repository type that triggered the build
     * @throws ContinuousIntegrationException if the CI cannot enforce a restricted build or the build request fails
     */
    default void triggerRestrictedBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo) throws ContinuousIntegrationException {
        throw new ContinuousIntegrationException("This continuous integration service does not support restricted builds");
    }

    /**
     * triggers a build for the build plan in the given participation
     *
     * @param participation the participation with the id of the build plan that should be triggered
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    default void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException {
        triggerBuild(participation, false);
    }

    /**
     * Resolves the trigger inputs that are the same for every participation of the given exercise.
     * <p>
     * A caller about to trigger many participations of one exercise calls this once and hands the result to every
     * trigger, so that per-exercise work is not repeated per student. An implementation that has nothing to share
     * returns {@link SharedBuildTriggerData#NONE} and the triggers resolve what they need themselves, exactly as before.
     *
     * @param exercise the exercise whose participations are about to be triggered
     * @return the inputs shared by every participation of that exercise
     */
    default SharedBuildTriggerData prepareSharedTriggerData(ProgrammingExercise exercise) {
        return SharedBuildTriggerData.NONE;
    }

    /**
     * Creates the throttle for one run that triggers many builds in a row.
     * <p>
     * The caller awaits it before every trigger of the run, so that the run adds builds only while the CI system has room
     * for them. An implementation without a queue to look at returns {@link BulkTriggerThrottle#NONE}.
     *
     * @return a throttle for one run, not shared with any other run
     */
    default BulkTriggerThrottle createBulkTriggerThrottle() {
        return BulkTriggerThrottle.NONE;
    }

    /**
     * triggers a build for the build plan in the given participation, reusing inputs the caller already resolved
     *
     * @param participation the participation with the id of the build plan that should be triggered
     * @param triggerAll    true if this build was triggered as part of a trigger all request. Currently only used for Local CI.
     * @param sharedData    inputs shared by every participation of the exercise, resolved once by the caller
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    default void triggerBuild(ProgrammingExerciseParticipation participation, boolean triggerAll, SharedBuildTriggerData sharedData) throws ContinuousIntegrationException {
        triggerBuild(participation, triggerAll);
    }

    /**
     * Triggers a build of the given commit, reusing inputs the caller already resolved for the whole exercise.
     * <p>
     * Unlike {@link #triggerBuild(ProgrammingExerciseParticipation, boolean, SharedBuildTriggerData)}, this does not read the head of the repository at the time of the
     * trigger, so a caller that already created a submission for a commit gets exactly that commit built even if the repository received another push in the meantime.
     *
     * @param participation     the participation with the id of the build plan that should be triggered
     * @param commitHash        the commit to build
     * @param triggeredByPushTo type of the repository the commit belongs to
     * @param triggerAll        true if this build was triggered as part of a bulk run. Currently only used for Local CI.
     * @param sharedData        inputs shared by every participation of the exercise, resolved once by the caller
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    default void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, RepositoryType triggeredByPushTo, boolean triggerAll,
            SharedBuildTriggerData sharedData) throws ContinuousIntegrationException {
        triggerBuild(participation, commitHash, triggeredByPushTo);
    }
}

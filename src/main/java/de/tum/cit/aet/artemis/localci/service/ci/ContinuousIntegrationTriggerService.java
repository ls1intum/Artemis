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
}

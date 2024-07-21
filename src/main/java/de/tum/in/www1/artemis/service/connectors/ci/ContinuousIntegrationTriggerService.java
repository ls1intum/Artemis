package de.tum.in.www1.artemis.service.connectors.ci;

import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;

/**
 * Abstract service for managing entities related to continuous integration.
 */
public interface ContinuousIntegrationTriggerService {

    /**
     * triggers a build for the build plan in the given participation
     *
     * @param participation the participation with the id of the build plan that should be triggered
     * @param triggerAll    true if this build was triggerd as part of a trigger all request. Currently only used for Local CI.
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
}

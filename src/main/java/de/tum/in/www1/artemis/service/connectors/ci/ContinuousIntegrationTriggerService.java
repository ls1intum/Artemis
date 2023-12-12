package de.tum.in.www1.artemis.service.connectors.ci;

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
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException;

    void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash) throws ContinuousIntegrationException;

    /**
     * Triggers a build for the build plan in the given participation with an optional commit hash.
     *
     * @param participation the participation with the id of the build plan that should be triggered
     * @param commitHash    the commit hash to be used for the build trigger
     * @param isTestPush    defines if the build is triggered by a test push
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    default void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash, boolean isTestPush) throws ContinuousIntegrationException {
        triggerBuild(participation);
    }
}

package de.tum.in.www1.artemis.service.connectors.ci;

import javax.annotation.Nullable;

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
     * @param commitHash    the commit hash of the commit that triggers the build. Use "null" to retrieve the latest commit of the default branch.
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    void triggerBuild(ProgrammingExerciseParticipation participation, @Nullable String commitHash) throws ContinuousIntegrationException;
}

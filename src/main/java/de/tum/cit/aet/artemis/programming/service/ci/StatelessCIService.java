package de.tum.cit.aet.artemis.programming.service.ci;

import java.util.UUID;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.BuildTriggerRequestDTO;

/**
 * Abstract service for managing entities related to continuous integration.
 */
public interface StatelessCIService {

    enum BuildStatus {
        INACTIVE, QUEUED, BUILDING
    }

    /**
     * Get the current status of the build for the given participation, i.e.
     * INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation);

    /**
     * Triggers a build for the build plan in the given participation.
     *
     * @param buildTriggerRequestDTO the request containing the participation and
     *                                   other necessary information to trigger the
     *                                   build
     * @return the UUID of the triggered build
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    UUID build(BuildTriggerRequestDTO buildTriggerRequestDTO) throws ContinuousIntegrationException;

    /**
     * Checks if the underlying CI server is up and running and gives some
     * additional information about the running
     * services if available
     *
     * @return The health of the CI service containing if it is up and running and
     *         any additional data, or the throwing exception otherwise
     */
    ConnectorHealth health();

}

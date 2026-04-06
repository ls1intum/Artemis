package de.tum.cit.aet.artemis.programming.service.ci;

import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_REPO_NAME;

import java.util.UUID;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.jenkinsstateless.dto.BuildTriggerRequestDTO;

/**
 * Abstract service for managing entities related to continuous integration.
 */
public interface StatelessCIService {

    // Match Unix and Windows paths because the notification plugin uses '/' and reports Windows paths like '/C:/'
    String matchPathEndingWithAssignmentDirectory = "(/?[^\0]+)*" + ASSIGNMENT_DIRECTORY;

    String orMatchStartingWithRepoName = "|^" + ASSIGNMENT_REPO_NAME + "/"; // Needed for C build logs

    Pattern ASSIGNMENT_PATH = Pattern.compile(matchPathEndingWithAssignmentDirectory + orMatchStartingWithRepoName);

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

    // This method is temporary, for an adaptation to the new-result endpoint
    // TODO: remove after endpoint handling is refactored.
    String getPlanKey(Object requestBody) throws ContinuousIntegrationException;
}

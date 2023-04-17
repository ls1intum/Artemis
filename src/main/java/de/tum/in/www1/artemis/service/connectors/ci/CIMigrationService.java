package de.tum.in.www1.artemis.service.connectors.ci;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;

/**
 * Service for migrations affecting a continuous integration system.
 * Every migration might now be implemented for all CI systems.
 */
public interface CIMigrationService {

    /**
     * Overrides the existing notification URL for build results with the current one in use by Artemis.
     *
     * @param projectKey    The key of the project, e.g. 'EIST16W1', which is normally the programming exercise project key.
     * @param buildPlanKey  The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param repositoryUrl the URL of the assignment repository
     */
    void overrideBuildPlanNotification(String projectKey, String buildPlanKey, VcsRepositoryUrl repositoryUrl);
}

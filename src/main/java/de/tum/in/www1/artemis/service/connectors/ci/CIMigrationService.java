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

    /**
     * Deletes all build triggers for the given build plan.
     *
     * @param buildPlanKey The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     */
    void deleteBuildTriggers(String buildPlanKey, VcsRepositoryUrl repositoryUrl);

    /**
     * Overrides the existing repository URL for the given build plan.
     *
     * @param buildPlanKey  The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-BASE'.
     * @param name          The name of the repository
     * @param repositoryUrl the URL of the repository
     */
    void overrideBuildPlanRepository(String buildPlanKey, String name, String repositoryUrl);

    /**
     * Overrides the existing repository URL for the given project that are checked out by the build plans.
     *
     * @param buildPlanKey The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-BASE'.
     */
    void overrideRepositoriesToCheckout(String buildPlanKey);
}

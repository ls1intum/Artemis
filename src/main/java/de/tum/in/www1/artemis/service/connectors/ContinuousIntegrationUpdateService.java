package de.tum.in.www1.artemis.service.connectors;

/**
 * This service manages the update of the Build plan on the Continuous Integration Service depending on the current VCS and CI profiles.
 */
public interface ContinuousIntegrationUpdateService {

    /**
     * Updates the configured repository for a given plan to the given CI Server repository (and do other related stuff).
     *
     * @param projectKey              The key of the project, e.g. 'EIST16W1'.
     * @param planKey                 The key of the plan, which is usually the name, e.g. 'ga56hur'.
     * @param ciRepositoryName        The name of the configured repository in the CI plan.
     * @param repoProjectKey          The key of the project that contains the repository.
     * @param repoName                The lower level identifier of the repository.
     */
    public String updatePlanRepository(String projectKey, String planKey, String ciRepositoryName, String repoProjectKey, String repoName);

    /**
     * Triggers an build (if needed)
     *
     * @param buildPlanId             The build plan id.
     * @param initialBuild            Whether the build should be the initial build (might change if the build is actually triggered).
     */
    public void triggerUpdate(String buildPlanId, boolean initialBuild);
}

package de.tum.cit.aet.artemis.core.service.connectors.ci;

/**
 * This service manages the update of the Build plan on the Continuous Integration Service depending on the current VCS and CI profiles.
 */
public interface ContinuousIntegrationUpdateService {

    /**
     * Updates the configured repository for a given plan to the given CI Server repository (and do other related stuff).
     *
     * @param buildPlanKey The key of the buildPlan, which is usually the username, e.g. 'ga56hur'.
     * @param ciRepoName   The name of the configured repository in the continuous integration plan, normally 'assignment' or 'test'
     * @param newRepoUri   The new repository URI
     * @param branchName   The name of the default name of the repository
     */
    void updatePlanRepository(String buildPlanKey, String ciRepoName, String newRepoUri, String branchName);
}

package de.tum.in.www1.artemis.service.connectors;

import java.util.List;
import java.util.Optional;

/**
 * This service manages the update of the Build plan on the Continuous Integration Service depending on the current VCS and CI profiles.
 */
public interface ContinuousIntegrationUpdateService {

    /**
     * Updates the configured repository for a given plan to the given CI Server repository (and do other related stuff).
     *
     * @param projectKey        The key of the project, e.g. 'EIST16W1'.
     * @param buildPlanKey      The key of the buildPlan, which is usually the username, e.g. 'ga56hur'.
     * @param ciRepoName        The name of the configured repository in the continuous integration plan, normally 'assignment' or 'test'
     * @param repoProjectKey    The key of the project that contains the repository.
     * @param vcsRepoName       The lower level identifier of the repository in the version control system
     * @param branchName The name of the default name of the repository
     * @param triggeredBy       Optional list of repositories that should trigger the new build plan. If empty, no triggers get overwritten
     */
    void updatePlanRepository(String projectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String vcsRepoName, String branchName,
            Optional<List<String>> triggeredBy);
}

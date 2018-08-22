package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;
import org.springframework.http.ResponseEntity;

import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * This service manages the update of the Build plan on the Continuous Integration Service depending on the current VCS and CI profiles.
 */
public interface ContinuousIntegrationUpdateService {

    /**
     * Updates the configured repository for a given plan to the given CI Server repository (and do other related stuff).
     *
     * @param topLevelIdentifier      The top level identifier of the plan (project/view), e.g. 'EIST16W1'.
     * @param lowerLevelIdentifier    The lower level identifier of the plan, which is usually the name, e.g. 'ga56hur'.
     * @param bambooRepositoryName    The name of the configured repository in the CI plan.
     * @param vcsTopLevelIdentifier   The top level identifier of the repository.
     * @param vcsLowerLevelIdentifier The lower level identifier of the repository.
     */
    public String updatePlanRepository(String topLevelIdentifier, String lowerLevelIdentifier, String bambooRepositoryName, String vcsTopLevelIdentifier, String vcsLowerLevelIdentifier);
}

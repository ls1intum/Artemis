package de.tum.in.www1.artemis.service.connectors.localci;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;

/**
 * This service is responsible for connecting the local CI build job execution to the build plan saved in the database.
 * Note: For now this service does not do anything as no representation of the build plan in the database exists, but provides the basis for future development.
 */
@Service
@Profile("localci")
public class LocalCIBuildPlanService {

    /**
     * Updates the build plan status of the given participation to the given status.
     *
     * @param participation  the participation for which the build plan status should be updated.
     * @param newBuildStatus the new build plan status.
     * @throws LocalCIException if the build plan id is null.
     */
    public void updateBuildPlanStatus(ProgrammingExerciseParticipation participation, ContinuousIntegrationService.BuildStatus newBuildStatus) {
        // TODO LOCALVC_CI: Update the build status in the database.
    }
}

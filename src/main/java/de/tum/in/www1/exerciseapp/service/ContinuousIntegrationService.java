package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.BuildLogEntry;
import de.tum.in.www1.exerciseapp.domain.Participation;
import org.springframework.http.ResponseEntity;

import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Created by muenchdo on 07/09/16.
 */
public interface ContinuousIntegrationService {

    enum BuildStatus {
        INACTIVE, QUEUED, BUILDING
    }

    /**
     * Copy the base build plan for the given user on the CI system.
     *
     * @param baseBuildPlanId unique identifier for build plan on CI system
     * @param username        username of user for whom to copy build plan
     * @return unique identifier of the copied build plan
     */
    String copyBuildPlan(String baseBuildPlanId, String username);

    /**
     * Configure the build plan with the given identifier on the CI system. Common configurations:
     * - update the repository in the build plan
     * - set appropriate user permissions
     *
     * @param buildPlanId   unique identifier for build plan on CI system
     * @param repositoryUrl url of user's personal repository copy
     * @param username      username of  user for whom to configure build plan
     */
    void configureBuildPlan(String buildPlanId, URL repositoryUrl, String username);

    /**
     * Delete build plan with given identifier from CI system.
     *
     * @param buildPlanId unique identifier for build plan on CI system
     */
    void deleteBuildPlan(String buildPlanId);

    /**
     * Will be called when a POST request is sent to the '/results/{buildPlanId}'.
     * Configure this as a build step in the build plan.
     * <p>
     * Important: The implementation is responsible for retrieving and saving the result from the CI system.
     *
     * @param participation participation for which build has completed
     */
    void onBuildCompleted(Participation participation);

    /**
     * Get the current status of the build for the given participation, i.e. INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    BuildStatus getBuildStatus(Participation participation);

    /**
     * Check if the given build plan is valid and accessible.
     *
     * @param buildPlanId   unique identifier for build plan on CI system
     * @return
     */
    Boolean buildPlanIdIsValid(String buildPlanId);

    // TODO: This return type is temporary
    Map<String, Object> getLatestBuildResultDetails(Participation participation);

    List<BuildLogEntry> getLatestBuildLogs(Participation participation);

    URL getBuildPlanWebUrl(Participation participation);

    ResponseEntity retrieveLatestArtifact(Participation participation);

}

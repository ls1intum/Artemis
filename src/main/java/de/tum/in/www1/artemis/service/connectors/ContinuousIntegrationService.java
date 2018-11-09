package de.tum.in.www1.artemis.service.connectors;

import de.tum.in.www1.artemis.domain.*;
import org.springframework.http.ResponseEntity;

import java.net.URL;
import java.util.List;

public interface ContinuousIntegrationService {

    public enum BuildStatus {
        INACTIVE, QUEUED, BUILDING
    }

    /**
     * Creates the base build plan for the given programming exercise
     *  @param exercise a programming exercise with the required information to create the base build plan
     * @param planKey the key of the plan
     * @param assignmentVcsRepositorySlug the slug of the assignment repository (used to separate between exercise and solution), i.e. the unique identifier
     * @param testVcsRepositorySlug the slug of the test repository, i.e. the unique identifier
     */
    public void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, String assignmentVcsRepositorySlug, String testVcsRepositorySlug);

    /**
     * Copy the base build plan for the given user on the CI system.
     *
     * @param baseBuildPlanId unique identifier for build plan on CI system
     * @param username        username of user for whom to copy build plan
     * @return unique identifier of the copied build plan
     */
    public String copyBuildPlan(String baseBuildPlanId, String username);

    /**
     * Configure the build plan with the given participation on the CI system. Common configurations:
     * - update the repository in the build plan
     * - set appropriate user permissions
     * - initialize/enable the build plan so that it works
     *
     * @param participation contains the unique identifier for build plan on CI system and the url of user's personal repository copy
     */
    public void configureBuildPlan(Participation participation);

    /**
     * triggers a build for the build plan in the given participation
     * @param participation the participation with the id of the build plan that should be triggered
     */
    public void triggerBuild(Participation participation);


    /**
     * Delete project with given identifier from CI system.
     *
     * @param projectKey unique identifier for the project on CI system
     */
    public void deleteProject(String projectKey);

    /**
     * Delete build plan with given identifier from CI system.
     *
     * @param buildPlanId unique identifier for build plan on CI system
     */
    public void deleteBuildPlan(String buildPlanId);

    /**
     * Will be called when a POST request is sent to the '/results/{buildPlanId}'.
     * Configure this as a build step in the build plan.
     * <p>
     * Important: The implementation is responsible for retrieving and saving the result from the CI system.
     *
     * @param participation participation for which build has completed
     */
    public Result onBuildCompleted(Participation participation);

    /**
     * Get the current status of the build for the given participation, i.e. INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    public BuildStatus getBuildStatus(Participation participation);

    /**
     * Check if the given build plan ID is valid and accessible.
     *
     * @param buildPlanId   unique identifier for build plan on CI system
     * @return
     */
    public Boolean buildPlanIdIsValid(String buildPlanId);

    /**
     * Get details about the latest build result.
     *
     * Used to display the results of the test cases to the student: webapp/app/courses/results/result-deatil.html
     * Used to generate the interactive exercise instructions: webapp/app/editor/instructions/editor-instructions.components.js
     *
     * @param result the result for which to get details
     * @return List of automatic feedback by the continuous integration server. contains the test methods and their results:
     */
    public List<Feedback> getLatestBuildResultDetails(Result result);

    /**
     * Get the build logs of the latest CI build.
     *
     * @param participation  participation for which to get the build logs
     * @return  list of build log entries
     */
    public List<BuildLogEntry> getLatestBuildLogs(Participation participation);

    /**
     * Get the public URL to the build plan. Used for the "Go to Build Plan" button, if this feature is enabled for the exercise.
     *
     * @param participation  participation for which to get the build plan URL
     * @return
     */
    public URL getBuildPlanWebUrl(Participation participation);

    /**
     * Get the build artifact (JAR/WAR), if any, of the latest build
     *
     * @param participation participation for which to get the build artifact
     * @return the binary build artifact. Typically a JAR/WAR ResponseEntity.
     */
    public ResponseEntity retrieveLatestArtifact(Participation participation);

    /**
     * Checks if the project with the given projectKey already exists
     *
     * @param projectKey
     * @param projectName
     * @return true if the project exists, false otherwise
     */
    public String checkIfProjectExists(String projectKey, String projectName);
}

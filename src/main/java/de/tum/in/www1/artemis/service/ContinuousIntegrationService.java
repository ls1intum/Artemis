package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import org.springframework.http.ResponseEntity;

import java.net.URL;
import java.util.List;

/**
 * Created by muenchdo on 07/09/16.
 */
public interface ContinuousIntegrationService {

    public enum BuildStatus {
        INACTIVE, QUEUED, BUILDING
    }


    /**
     * Creates a project that contains the build plans.
     *
     * @param projectName The name of the new project
     */
    public void createProject(String projectName);

    /**
     * Creates a new build plan that
     *
     * @param buildplanName The name of the newly created build plan
     * @param projectName   The name of the project where the new build plan should be located
     * @param templateBuildPlanName The name of the build plan that contains the template
     * @param templateProjectName   The name of the project that contains the templateBuildPlan
     */
    public void copyBuildPlanFromTemplate(String buildplanName, String projectName, String templateBuildPlanName, String templateProjectName);

    /**
     * Grants permissions for the instructors/teachingAssistants on a project.
     *
     * @param projectKey The key of the project
     * @param instructorGroupName The name of the group that contains the instructors
     * @param teachingAssistantGroupName The name of the group that contains the tutors
     */
    public void grantProjectPermissions(String projectKey, String instructorGroupName, String teachingAssistantGroupName);

    /**
     * Creates the base build plan for the given programming exercise
     *
     * @param exercise a programming exercise with the required information to create the base build plan
     * @param planKey the key of the plan
     * @param vcsRepositorySlug the slug of the repository (used to seperate between exercise and solution)
     */
    public void createBaseBuildPlanForExercise(ProgrammingExercise exercise, String planKey, String vcsRepositorySlug);

    /**
     * Copy the base build plan for the given user on the CI system.
     *
     * @param baseBuildPlanId unique identifier for build plan on CI system
     * @param username        username of user for whom to copy build plan
     * @return unique identifier of the copied build plan
     */
    public String copyBuildPlan(String baseBuildPlanId, String username);

    /**
     * Configure the build plan with the given identifier on the CI system. Common configurations:
     * - update the repository in the build plan
     * - set appropriate user permissions
     *
     * @param buildPlanId   unique identifier for build plan on CI system
     * @param repositoryUrl url of user's personal repository copy
     * @param username      username of  user for whom to configure build plan
     */
    public void configureBuildPlan(String buildPlanId, URL repositoryUrl, String username);

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
}

package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.BuildLogEntry;
import de.tum.in.www1.exerciseapp.domain.Feedback;
import de.tum.in.www1.exerciseapp.domain.Participation;
import org.springframework.http.ResponseEntity;

import java.net.URL;
import java.util.HashSet;
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
     * Check if the given build plan ID is valid and accessible.
     *
     * @param buildPlanId   unique identifier for build plan on CI system
     * @return
     */
    Boolean buildPlanIdIsValid(String buildPlanId);

    /**
     * Get details about the latest build result.
     *
     * Used to display the results of the test cases to the student: webapp/app/courses/results/result-deatil.html
     * Used to generate the interactive exercise instructions: webapp/app/editor/instructions/editor-instructions.components.js
     *
     * @param participation participation for which to get details
     * @return The details map. contains the test methods and their results:
     *  {
        "details": {
            "className": "edu.tum.cs.i1.ease.DiscussionTestTest",
            "methodName": "testTestStartCourseDiscussionWithBrokenClass",
            "error": [
                    {
                        "message": "java.lang.AssertionError: ..."
                    }
                ]
            }
        }
     *
     */
    // TODO: Change the return type to a CI system independent return type.
    Map<String, Object> getLatestBuildResultDetails(Participation participation);

    /**
     * Get the build logs of the latest CI build.
     *
     * @param participation  participation for which to get the build logs
     * @return  list of build log entries
     */
    List<BuildLogEntry> getLatestBuildLogs(Participation participation);

    /**
     * Get the public URL to the build plan. Used for the "Go to Build Plan" button, if this feature is enabled for the exercise.
     *
     * @param participation  participation for which to get the build plan URL
     * @return
     */
    URL getBuildPlanWebUrl(Participation participation);

    /**
     * Get the build artifact (JAR/WAR), if any, of the latest build
     *
     * @param participation participation for which to get the build artifact
     * @return the binary build artifact. Typically a JAR/WAR ResponseEntity.
     */
    ResponseEntity retrieveLatestArtifact(Participation participation);

    /**
      * Uses the retreiveLatestBuildResultDetails in order to create feebacks from the error to give the user preciser error messages
      *
      *@param buildResultDetails returned build result details from the rest api of bamboo
       *
       * @return a Set of feedbacks which can directly be stored int a result
       */
    public HashSet<Feedback> createFeedbacksForResult(Map<String, Object> buildResultDetails);

}

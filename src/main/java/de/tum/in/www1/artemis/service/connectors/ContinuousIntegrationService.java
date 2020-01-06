package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpException;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;

/**
 * Abstract service for managing entities related to continuous integration.
 */
public interface ContinuousIntegrationService {

    enum BuildStatus {
        INACTIVE, QUEUED, BUILDING
    }

    /**
     * Creates the base build plan for the given programming exercise
     * 
     * @param exercise          a programming exercise with the required information to create the base build plan
     * @param planKey           the key of the plan
     * @param repositoryURL     the URL of the assignment repository (used to separate between exercise and solution)
     * @param testRepositoryURL the URL of the test repository
     */
    void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, URL repositoryURL, URL testRepositoryURL);

    /**
     * Clones an existing build plan. Illegal characters in the plan key, or name will be replaced.
     *
     * @param sourceProjectKey The key of the source project, normally the key of the exercise -> courseShortName + exerciseShortName.
     * @param sourcePlanName The name of the source plan
     * @param targetProjectKey The key of the project the plan should get copied to
     * @param targetProjectName The wanted name of the new project
     * @param targetPlanName The wanted name of the new plan after copying it
     * @return The key of the new build plan
     */
    String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName);

    /**
     * Configure the build plan with the given participation on the CI system. Common configurations: - update the repository in the build plan - set appropriate user permissions -
     * initialize/enable the build plan so that it works
     *
     * @param participation contains the unique identifier for build plan on CI system and the url of user's personal repository copy
     */
    void configureBuildPlan(ProgrammingExerciseParticipation participation);

    /**
     * An empty commit might be necessary depending on the chosen CI system (e.g. on Bamboo) so that subsequent commits trigger a new build on the build plan
     *
     * @param participation contains the unique identifier for build plan on CI system and the url of user's personal repository copy
     */
    void performEmptySetupCommit(ProgrammingExerciseParticipation participation);

    /**
     * triggers a build for the build plan in the given participation
     * 
     * @param participation the participation with the id of the build plan that should be triggered
     * @throws HttpException if the request to the CI failed.
     */
    void triggerBuild(ProgrammingExerciseParticipation participation) throws HttpException;

    /**
     * Delete project with given identifier from CI system.
     *
     * @param projectKey unique identifier for the project on CI system
     */
    void deleteProject(String projectKey);

    /**
     * Delete build plan with given identifier from CI system.
     *
     * @param projectKey The key of the related programming exercise
     * @param buildPlanId unique identifier for build plan on CI system
     */
    void deleteBuildPlan(String projectKey, String buildPlanId);

    /**
     * Get the plan key of the finished build, the information of the build gets passed via the requestBody. The requestBody must match the information passed from the
     * bamboo-server-notification-plugin, the body is described here: https://github.com/ls1intum/bamboo-server-notification-plugin
     *
     * @param requestBody The request Body received from the CI-Server.
     * @return the plan key of the build
     * @throws Exception if the Body could not be parsed
     */
    String getPlanKey(Object requestBody) throws Exception;

    /**
     * Get the result of the finished build, the information of the build gets passed via the requestBody. The requestBody must match the information passed from the
     * bamboo-server-notification-plugin, the body is described here: https://github.com/ls1intum/bamboo-server-notification-plugin
     *
     * @param participation The participation for which the build finished
     * @param requestBody   The request Body received from the CI-Server.
     * @return the result of the build
     * @throws Exception if the Body could not be parsed
     */
    Result onBuildCompletedNew(ProgrammingExerciseParticipation participation, Object requestBody) throws Exception;

    /**
     * Get the current status of the build for the given participation, i.e. INACTIVE, QUEUED, or BUILDING.
     *
     * @param participation participation for which to get status
     * @return build status
     */
    BuildStatus getBuildStatus(ProgrammingExerciseParticipation participation);

    /**
     * Check if the given build plan ID is valid and accessible.
     *
     * @param projectKey The key of the related programming exercise
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if build plan is valid otherwise false
     */
    boolean buildPlanIdIsValid(String projectKey, String buildPlanId);

    /**
     * Get details about the latest build result. Used to display the results of the test cases to the student: webapp/app/courses/results/result-deatil.html Used to generate the
     * interactive exercise instructions: webapp/app/editor/instructions/editor-instructions.components.js
     *
     * @param result the result for which to get details
     * @return List of automatic feedback by the continuous integration server. contains the test methods and their results:
     */
    List<Feedback> getLatestBuildResultDetails(Result result);

    /**
     * Get the build logs of the latest CI build.
     *
     * @param projectKey The key of the project under which the plan is stored
     * @param buildPlanId to get the latest build logs
     * @return list of build log entries
     */
    List<BuildLogEntry> getLatestBuildLogs(String projectKey, String buildPlanId);

    /**
     * Get the build artifact (JAR/WAR), if any, of the latest build
     *
     * @param participation participation for which to get the build artifact
     * @return the binary build artifact. Typically a JAR/WAR ResponseEntity.
     */
    ResponseEntity retrieveLatestArtifact(ProgrammingExerciseParticipation participation);

    /**
     * Retrieve the latest build result from the CIS for the given participation if it matches the commitHash of the submission and save it into the database.
     * @param participation to identify the build artifact with.
     * @param submission    for commitHash comparison.
     * @return the saved Result instance if a build result could be retrieved from the CIS.
     */
    Optional<Result> retrieveLatestBuildResult(ProgrammingExerciseParticipation participation, ProgrammingSubmission submission);

    /**
     * Checks if the project with the given projectKey already exists
     *
     * @param projectKey to check if a project with this unique key already exists
     * @param projectName to check if a project with the same name already exists
     * @return true if the project exists, false otherwise
     */
    String checkIfProjectExists(String projectKey, String projectName);

    /**
     * Checks if a given build plan is deactivated, or enabled
     *
     * @param planId The ID of the build plan
     * @param projectKey The key of the project for which to check the build plan
     * @return True, if the plan is enabled, false otherwise
     */
    boolean isBuildPlanEnabled(final String projectKey, final String planId);

    /**
     * Enables the given build plan.
     *
     * @param projectKey The key of the project for which to enable the plan
     * @param planKey to identify the plan in the CI service.
     */
    void enablePlan(String projectKey, String planKey);

    /**
     * Updates the configured repository for a given plan to the given Bamboo Server repository.
     *
     * @param bambooProject         The key of the project, e.g. 'EIST16W1'.
     * @param bambooPlan            The key of the plan, which is usually the name combined with the project, e.g. 'PROJECT-GA56HUR'.
     * @param bambooRepositoryName  The name of the configured repository in the CI plan.
     * @param repoProjectName       The key of the project that contains the repository.
     * @param repoUrl               The url of the newly to be referenced repository.
     * @param triggeredBy           Optional list of repositories that should trigger the new build plan. If empty, no triggers get overwritten
     */
    void updatePlanRepository(String bambooProject, String bambooPlan, String bambooRepositoryName, String repoProjectName, String repoUrl, Optional<List<String>> triggeredBy);

    /**
     * Gives overall roles permissions for the defined project. A role can e.g. be all logged in users
     *
     * @param projectKey The key of the project to grant permissions to
     * @param groups The role of the users that should have the permissions
     * @param permissions The permissions to grant the users
     */
    void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions);

    /**
     * Some CI systems give projects default permissions (e.g. read in Bamboo for logged in and anonymous users)
     * This method removes all of these unnecessary and potentially insecure permissions
     *
     * @param projectKey The key of the build project which should get "cleaned"
     */
    void removeAllDefaultProjectPermissions(String projectKey);

    /**
     * Checks if the underlying CI server is up and running and gives some additional information about the running
     * services if available
     *
     * @return The health of the CI service containing if it is up and running and any additional data, or the throwing exception otherwise
     */
    ConnectorHealth health();

    /**
     * Creates a project on the CI server.
     *
     * @param programmingExercise for which a project should be created
     */
    void createProjectForExercise(ProgrammingExercise programmingExercise);

    /**
     * Get the webhook URL to call if one wants to trigger the build plan or notify the plan about an event that should
     * trigger. E.g. a new push to the repository
     *
     * @param projectKey The key of the project related to the build plan
     * @param buildPlanId The ID of the build plan, that should get triggered/notified
     * @return The URL as a String pointing to the to be triggered build plan in the CI system. If this is not needed/supported, an empty optional is returned.
     */
    Optional<String> getWebHookUrl(String projectKey, String buildPlanId);

    /**
     * Path a repository should get checked out in a build plan. E.g. the assignment repository should get checked out
     * to a subdirectory called "assignment" for the Python programming language.
     */
    enum RepositoryCheckoutPath implements CustomizableCheckoutPath {
        ASSIGNMENT {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                switch (language) {
                case JAVA:
                case PYTHON:
                case C:
                    return Constants.ASSIGNMENT_CHECKOUT_PATH;
                default:
                    throw new IllegalArgumentException("Repository checkout path for assignment repo has not yet been defined for " + language);
                }
            }
        },
        TEST {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                switch (language) {
                case JAVA:
                case PYTHON:
                    return "";
                case C:
                    return Constants.TESTS_CHECKOUT_PATH;
                default:
                    throw new IllegalArgumentException("Repository checkout path for test repo has not yet been defined for " + language);
                }
            }
        }
    }

    interface CustomizableCheckoutPath {

        /**
         * Path of the subdirectory to which a repository should get checked out to depending on the programming language.
         * E.g. for the language {@link ProgrammingLanguage#C} always check the repo out to "tests"
         *
         * @param language The programming language for which there should be a custom checkout path
         * @return The path to the subdirectory as a String to which some repository should get checked out to.
         */
        String forProgrammingLanguage(ProgrammingLanguage language);
    }
}

package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;

/**
 * Abstract service for managing entities related to continuous integration.
 */
public interface ContinuousIntegrationService {

    // Match Unix and Windows paths because the notification plugin uses '/' and reports Windows paths like '/C:/
    String matchPathEndingWithAssignmentDirectory = "(/?[^\0]+)*" + ASSIGNMENT_DIRECTORY;

    String orMatchStartingWithRepoName = "|^" + ASSIGNMENT_REPO_NAME + "/"; // Needed for C build logs

    Pattern ASSIGNMENT_PATH = Pattern.compile(matchPathEndingWithAssignmentDirectory + orMatchStartingWithRepoName);

    enum BuildStatus {
        INACTIVE, QUEUED, BUILDING
    }

    /**
     * Creates the base build plan for the given programming exercise
     *
     * @param exercise              a programming exercise with the required information to create the base build plan
     * @param planKey               the key of the plan
     * @param repositoryURL         the URL of the assignment repository (used to separate between exercise and solution)
     * @param testRepositoryURL     the URL of the test repository
     * @param solutionRepositoryURL the URL of the solution repository. Only used for HASKELL exercises with checkoutSolutionRepository=true. Otherwise ignored.
     */
    void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUrl repositoryURL, VcsRepositoryUrl testRepositoryURL,
            VcsRepositoryUrl solutionRepositoryURL);

    /**
     * Recreates BASE and SOLUTION Build Plan for the given programming exercise
     *
     * @param exercise for which the build plans should be recreated
     */
    void recreateBuildPlansForExercise(ProgrammingExercise exercise);

    /**
     * Clones an existing build plan. Illegal characters in the plan key, or name will be replaced.
     *
     * @param sourceProjectKey The key of the source project, normally the key of the exercise -> courseShortName + exerciseShortName.
     * @param sourcePlanName The name of the source plan
     * @param targetProjectKey The key of the project the plan should get copied to
     * @param targetProjectName The wanted name of the new project
     * @param targetPlanName The wanted name of the new plan after copying it
     * @param targetProjectExists whether the target project already exists or not
     * @return The key of the new build plan
     */
    String copyBuildPlan(String sourceProjectKey, String sourcePlanName, String targetProjectKey, String targetProjectName, String targetPlanName, boolean targetProjectExists);

    /**
     * Configure the build plan with the given participation on the CI system. Common configurations: - update the repository in the build plan - set appropriate user permissions -
     * initialize/enable the build plan so that it works
     *
     * **Important**: make sure that participation.programmingExercise.templateParticipation is initialized, otherwise an org.hibernate.LazyInitializationException can occur
     *
     * @param participation contains the unique identifier for build plan on CI system and the url of user's personal repository copy
     * @param branch the default branch of the git repository that is used in the build plan
     */
    void configureBuildPlan(ProgrammingExerciseParticipation participation, String branch);

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
     * @throws ContinuousIntegrationException if the request to the CI failed.
     */
    void triggerBuild(ProgrammingExerciseParticipation participation) throws ContinuousIntegrationException;

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
     * (bamboo|jenkins)-server-notification-plugin, the body is described here: <a href="https://github.com/ls1intum/bamboo-server-notification-plugin">...</a> or here:
     * <a href="https://github.com/ls1intum/jenkins-server-notification-plugin">...</a>
     *
     * @param requestBody The request Body received from the CI-Server.
     * @return the plan key of the build
     * @throws ContinuousIntegrationException if the Body could not be parsed
     */
    String getPlanKey(Object requestBody) throws ContinuousIntegrationException;

    /**
     * converts the object from the CI system (Bamboo, Jenkins, or GitLabCI) into a proper Java DTO
     * @param requestBody the object sent from the CI system to Artemis
     * @return the DTO with all information in Java Object form
     */
    AbstractBuildResultNotificationDTO convertBuildResult(Object requestBody);

    /**
     * Generate an Artemis result object from the CI build result. Will use the test case results and issues in static code analysis as result feedback.
     *
     * @param buildResult   Build result data provided by build notification (already converted into a DTO)
     * @param participation to attach result to.
     * @return the created Artemis result with a score, completion date, etc.
     */
    Result createResultFromBuildResult(AbstractBuildResultNotificationDTO buildResult, ProgrammingExerciseParticipation participation);

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
    boolean checkIfBuildPlanExists(String projectKey, String buildPlanId);

    /**
     * Get the build logs of the latest CI build.
     *
     * @param programmingSubmission The programming for which the latest build logs should be retrieved
     * @return list of build log entries
     */
    List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission);

    /**
     * Get the build artifact (JAR/WAR), if any, of the latest build
     *
     * @param participation participation for which to get the build artifact
     * @return the binary build artifact. Typically a JAR/WAR ResponseEntity.
     */
    ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation);

    /**
     * Checks if the project with the given projectKey already exists
     *
     * @param projectKey to check if a project with this unique key already exists
     * @param projectName to check if a project with the same name already exists
     * @return an error message if the project exists, null otherwise
     */
    String checkIfProjectExists(String projectKey, String projectName);

    /**
     * Enables the given build plan.
     *
     * @param projectKey The key of the project for which to enable the plan
     * @param planKey to identify the plan in the CI service.
     */
    void enablePlan(String projectKey, String planKey);

    /**
     * Updates the configured exercise repository for a given build plan to the given repository, this is a key method in the Artemis system structure.
     *
     * @param buildProjectKey                   The key of the build project, e.g. 'EIST16W1', which is normally the programming exercise project key.
     * @param buildPlanKey                      The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param ciRepoName                        The name of the configured repository in the CI plan, normally 'assignment' (or 'test').
     * @param repoProjectKey                    The key of the project that contains the repository, e.g. 'EIST16W1', which is normally the programming exercise project key.
     * @param newRepoUrl                        The url of the newly to be referenced repository.
     * @param existingRepoUrl                   The url of the existing repository (which should be replaced).
     * @param newBranch                         The default branch for the new repository
     * @param optionalTriggeredByRepositories   Optional list of repositories that should trigger the new build plan. If empty, no triggers get overwritten.
     */
    void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUrl, String existingRepoUrl, String newBranch,
            Optional<List<String>> optionalTriggeredByRepositories);

    /**
     * Gives overall roles permissions for the defined project. A role can e.g. be all logged-in users
     *
     * @param projectKey The key of the project to grant permissions to
     * @param groups The role of the users that should have the permissions
     * @param permissions The permissions to grant the users
     */
    void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions);

    /**
     * Set Build Plan Permissions for admins, instructors and teaching assistants.
     *
     * @param programmingExercise   a programming exercise with the required information to set the needed build plan permissions
     * @param planName              The name of the source plan
     */
    void givePlanPermissions(ProgrammingExercise programmingExercise, String planName);

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
    void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException;

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
     * Extract the build log statistics from the BuildLogEntries and persist a BuildLogStatisticsEntry.
     * Not all programming languages and project types might be supported on all implementations of the ContinuousIntegrationService.
     *
     * @param programmingSubmission the submission to which the generated BuildLogStatisticsEntry should be attached
     * @param programmingLanguage the programming language of the programming exercise
     * @param projectType the project type of the programming exercise
     * @param buildLogEntries the list of BuildLogEntries received from the CI-Server
     */
    void extractAndPersistBuildLogStatistics(ProgrammingSubmission programmingSubmission, ProgrammingLanguage programmingLanguage, ProjectType projectType,
            List<BuildLogEntry> buildLogEntries);

    /**
     * Path a repository should get checked out in a build plan. E.g. the assignment repository should get checked out
     * to a subdirectory called "assignment" for the Python programming language.
     */
    enum RepositoryCheckoutPath implements CustomizableCheckoutPath {
        ASSIGNMENT {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                return switch (language) {
                    case JAVA, PYTHON, C, HASKELL, KOTLIN, VHDL, ASSEMBLER, SWIFT, OCAML, EMPTY -> Constants.ASSIGNMENT_CHECKOUT_PATH;
                };
            }
        },
        TEST {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                return switch (language) {
                    case JAVA, PYTHON, HASKELL, KOTLIN, SWIFT, EMPTY -> "";
                    case C, VHDL, ASSEMBLER, OCAML -> Constants.TESTS_CHECKOUT_PATH;
                };
            }
        },
        SOLUTION {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                return switch (language) {
                    case HASKELL, OCAML -> Constants.SOLUTION_CHECKOUT_PATH;
                    default -> throw new IllegalArgumentException("Repository checkout path for solution repo has not yet been defined for " + language);
                };
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

package de.tum.cit.aet.artemis.programming.service.ci;

import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_REPO_NAME;

import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

/**
 * Abstract service for managing entities related to continuous integration.
 */
public interface ContinuousIntegrationService extends StatelessCIService {

    // Match Unix and Windows paths because the notification plugin uses '/' and
    // reports Windows paths like '/C:/
    String matchPathEndingWithAssignmentDirectory = "(/?[^\0]+)*" + ASSIGNMENT_DIRECTORY;

    String orMatchStartingWithRepoName = "|^" + ASSIGNMENT_REPO_NAME + "/"; // Needed for C build logs

    Pattern ASSIGNMENT_PATH = Pattern.compile(matchPathEndingWithAssignmentDirectory + orMatchStartingWithRepoName);

    /**
     * Creates the base build plan for the given programming exercise
     *
     * @param exercise              a programming exercise with the required
     *                                  information to create the base build plan
     * @param planKey               the key of the plan
     * @param repositoryUri         the URI of the assignment repository (used to
     *                                  separate between exercise and solution)
     * @param testRepositoryUri     the URI of the test repository
     * @param solutionRepositoryUri the URI of the solution repository. Only used
     *                                  for HASKELL exercises with
     *                                  checkoutSolutionRepository=true. Otherwise,
     *                                  ignored.
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri);

    /**
     * Recreates BASE and SOLUTION Build Plan for the given programming exercise
     *
     * @param exercise for which the build plans should be recreated
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    void recreateBuildPlansForExercise(ProgrammingExercise exercise) throws JsonProcessingException;

    /**
     * Clones an existing build plan. Illegal characters in the plan key, or name
     * will be replaced.
     *
     * @param sourceExercise      The exercise from which the build plan should be
     *                                copied
     * @param sourcePlanName      The name of the source plan
     * @param targetExercise      The exercise to which the build plan is copied to
     * @param targetProjectName   The wanted name of the new project
     * @param targetPlanName      The wanted name of the new plan after copying it
     * @param targetProjectExists whether the target project already exists or not
     * @return The key of the new build plan
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    String copyBuildPlan(ProgrammingExercise sourceExercise, String sourcePlanName, ProgrammingExercise targetExercise, String targetProjectName, String targetPlanName,
            boolean targetProjectExists);

    /**
     * Configure the build plan with the given participation on the CI system.
     * Common configurations: - update the repository in the build plan - set
     * appropriate user permissions -
     * initialize/enable the build plan so that it works
     * <p>
     * **Important**: make sure that
     * participation.programmingExercise.templateParticipation is initialized,
     * otherwise an org.hibernate.LazyInitializationException can occur
     *
     * @param participation contains the unique identifier for build plan on CI
     *                          system and the url of user's personal repository copy
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    void configureBuildPlan(ProgrammingExerciseParticipation participation);

    /**
     * Delete project with given identifier from CI system.
     *
     * @param projectKey unique identifier for the project on CI system
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    void deleteProject(String projectKey);

    /**
     * Delete build plan with given identifier from CI system.
     *
     * @param projectKey  The key of the related programming exercise
     * @param buildPlanId unique identifier for build plan on CI system
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    void deleteBuildPlan(String projectKey, String buildPlanId);

    /**
     * Get the plan key of the finished build, the information of the build gets
     * passed via the requestBody. The requestBody must match the information passed
     * from the
     * jenkins-server-notification-plugin, the body is described here: <a href=
     * "https://github.com/ls1intum/jenkins-server-notification-plugin">...</a>
     *
     * @param requestBody The request Body received from the CI-Server.
     * @return the plan key of the build
     * @throws ContinuousIntegrationException if the Body could not be parsed
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    String getPlanKey(Object requestBody) throws ContinuousIntegrationException;

    /**
     * Check if the given build plan ID is valid and accessible.
     *
     * @param projectKey  The key of the related programming exercise
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if build plan is valid otherwise false
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    boolean checkIfBuildPlanExists(String projectKey, String buildPlanId);

    /**
     * Checks if the project with the given projectKey already exists
     *
     * @param projectKey  to check if a project with this unique key already exists
     * @param projectName to check if a project with the same name already exists
     * @return an error message if the project exists, null otherwise
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    String checkIfProjectExists(String projectKey, String projectName);

    /**
     * Enables the given build plan.
     *
     * @param projectKey The key of the project for which to enable the plan
     * @param planKey    to identify the plan in the CI service.
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    void enablePlan(String projectKey, String planKey);

    /**
     * Updates the configured exercise repository for a given build plan to the
     * given repository, this is a key method in the Artemis system structure.
     *
     * @param buildProjectKey The key of the build project, e.g. 'EIST16W1', which
     *                            is normally the programming exercise project key.
     * @param buildPlanKey    The key of the build plan, which is usually the name
     *                            combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param ciRepoName      The name of the configured repository in the CI plan,
     *                            normally 'assignment' (or 'test').
     * @param repoProjectKey  The key of the project that contains the repository,
     *                            e.g. 'EIST16W1', which is normally the programming
     *                            exercise project key.
     * @param newRepoUri      The url of the newly to be referenced repository.
     * @param existingRepoUri The url of the existing repository (which should be
     *                            replaced).
     * @param newBranch       The default branch for the new repository
     */
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUri, String existingRepoUri, String newBranch);

    /**
     * Set Build Plan Permissions for admins, instructors and teaching assistants.
     *
     * @param programmingExercise a programming exercise with the required
     *                                information to set the needed build plan
     *                                permissions
     * @param planName            The name of the source plan
     */
    // TODO: Move to a new ContinuousIntegrationPermissionService that is only
    // implemented by the Jenkins subsystem
    // void givePlanPermissions(ProgrammingExercise programmingExercise, String planName);

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
    // TODO: Move to a new ContinuousIntegrationBuildPlanService that is only
    // implemented by the Jenkins subsystem
    void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException;

}

package de.tum.cit.aet.artemis.programming.service.ci;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

/**
 * Service for managing build plans on a continuous integration server.
 * This interface is only implemented by CI systems that manage build plans (e.g. Jenkins).
 * Stateless CI systems (e.g. LocalCI, Hades) do not implement this interface.
 */
public interface ContinuousIntegrationBuildPlanService {

    /**
     * Creates the base build plan for the given programming exercise
     *
     * @param exercise              a programming exercise with the required information to create the base build plan
     * @param planKey               the key of the plan
     * @param repositoryUri         the URI of the assignment repository (used to separate between exercise and solution)
     * @param testRepositoryUri     the URI of the test repository
     * @param solutionRepositoryUri the URI of the solution repository. Only used for HASKELL exercises with
     *                                  checkoutSolutionRepository=true. Otherwise, ignored.
     */
    void createBuildPlanForExercise(ProgrammingExercise exercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri);

    /**
     * Recreates BASE and SOLUTION Build Plan for the given programming exercise
     *
     * @param exercise for which the build plans should be recreated
     */
    void recreateBuildPlansForExercise(ProgrammingExercise exercise) throws JsonProcessingException;

    /**
     * Clones an existing build plan. Illegal characters in the plan key, or name
     * will be replaced.
     *
     * @param sourceExercise      The exercise from which the build plan should be copied
     * @param sourcePlanName      The name of the source plan
     * @param targetExercise      The exercise to which the build plan is copied to
     * @param targetProjectName   The wanted name of the new project
     * @param targetPlanName      The wanted name of the new plan after copying it
     * @param targetProjectExists whether the target project already exists or not
     * @return The key of the new build plan
     */
    String copyBuildPlan(ProgrammingExercise sourceExercise, String sourcePlanName, ProgrammingExercise targetExercise, String targetProjectName, String targetPlanName,
            boolean targetProjectExists);

    /**
     * Configure the build plan with the given participation on the CI system.
     * Common configurations: - update the repository in the build plan - set appropriate user permissions -
     * initialize/enable the build plan so that it works
     * <p>
     * **Important**: make sure that participation.programmingExercise.templateParticipation is initialized,
     * otherwise an org.hibernate.LazyInitializationException can occur
     *
     * @param participation contains the unique identifier for build plan on CI system and the url of user's personal repository copy
     */
    void configureBuildPlan(ProgrammingExerciseParticipation participation);

    /**
     * Delete project with given identifier from CI system.
     *
     * @param projectKey unique identifier for the project on CI system
     */
    void deleteProject(String projectKey);

    /**
     * Delete build plan with given identifier from CI system.
     *
     * @param projectKey  The key of the related programming exercise
     * @param buildPlanId unique identifier for build plan on CI system
     */
    void deleteBuildPlan(String projectKey, String buildPlanId);

    /**
     * Check if the given build plan ID is valid and accessible.
     *
     * @param projectKey  The key of the related programming exercise
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if build plan is valid otherwise false
     */
    boolean checkIfBuildPlanExists(String projectKey, String buildPlanId);

    /**
     * Checks if the project with the given projectKey already exists
     *
     * @param projectKey  to check if a project with this unique key already exists
     * @param projectName to check if a project with the same name already exists
     * @return an error message if the project exists, null otherwise
     */
    String checkIfProjectExists(String projectKey, String projectName);

    /**
     * Enables the given build plan.
     *
     * @param projectKey The key of the project for which to enable the plan
     * @param planKey    to identify the plan in the CI service.
     */
    void enablePlan(String projectKey, String planKey);

    /**
     * Updates the configured exercise repository for a given build plan to the given repository, this is a key method in the Artemis system structure.
     *
     * @param buildProjectKey The key of the build project, e.g. 'EIST16W1', which is normally the programming exercise project key.
     * @param buildPlanKey    The key of the build plan, which is usually the name combined with the project, e.g. 'EIST16W1-GA56HUR'.
     * @param ciRepoName      The name of the configured repository in the CI plan, normally 'assignment' (or 'test').
     * @param repoProjectKey  The key of the project that contains the repository, e.g. 'EIST16W1', which is normally the programming exercise project key.
     * @param newRepoUri      The url of the newly to be referenced repository.
     * @param existingRepoUri The url of the existing repository (which should be replaced).
     * @param newBranch       The default branch for the new repository
     */
    void updatePlanRepository(String buildProjectKey, String buildPlanKey, String ciRepoName, String repoProjectKey, String newRepoUri, String existingRepoUri, String newBranch);

    /**
     * Creates a project on the CI server.
     *
     * @param programmingExercise for which a project should be created
     */
    void createProjectForExercise(ProgrammingExercise programmingExercise) throws ContinuousIntegrationException;
}
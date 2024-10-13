package de.tum.cit.aet.artemis.programming.service.ci;

import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.ASSIGNMENT_REPO_NAME;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.CheckoutDirectoriesDTO;

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
     * @param repositoryUri         the URI of the assignment repository (used to separate between exercise and solution)
     * @param testRepositoryUri     the URI of the test repository
     * @param solutionRepositoryUri the URI of the solution repository. Only used for HASKELL exercises with checkoutSolutionRepository=true. Otherwise, ignored.
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
     * Clones an existing build plan. Illegal characters in the plan key, or name will be replaced.
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
     * Configure the build plan with the given participation on the CI system. Common configurations: - update the repository in the build plan - set appropriate user permissions -
     * initialize/enable the build plan so that it works
     * <p>
     * **Important**: make sure that participation.programmingExercise.templateParticipation is initialized, otherwise an org.hibernate.LazyInitializationException can occur
     *
     * @param participation contains the unique identifier for build plan on CI system and the url of user's personal repository copy
     * @param branch        the default branch of the git repository that is used in the build plan
     */
    void configureBuildPlan(ProgrammingExerciseParticipation participation, String branch);

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
     * Get the plan key of the finished build, the information of the build gets passed via the requestBody. The requestBody must match the information passed from the
     * jenkins-server-notification-plugin, the body is described here: <a href="https://github.com/ls1intum/jenkins-server-notification-plugin">...</a>
     *
     * @param requestBody The request Body received from the CI-Server.
     * @return the plan key of the build
     * @throws ContinuousIntegrationException if the Body could not be parsed
     */
    String getPlanKey(Object requestBody) throws ContinuousIntegrationException;

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
     * @param projectKey  The key of the related programming exercise
     * @param buildPlanId unique identifier for build plan on CI system
     * @return true if build plan is valid otherwise false
     */
    boolean checkIfBuildPlanExists(String projectKey, String buildPlanId);

    /**
     * Get the build artifact (JAR/WAR), if any, of the latest build
     *
     * @param participation participation for which to get the build artifact
     * @return the binary build artifact. Typically, a JAR/WAR ResponseEntity.
     */
    ResponseEntity<byte[]> retrieveLatestArtifact(ProgrammingExerciseParticipation participation);

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
     * Gives overall roles permissions for the defined project. A role can e.g. be all logged-in users
     *
     * @param projectKey  The key of the project to grant permissions to
     * @param groups      The role of the users that should have the permissions
     * @param permissions The permissions to grant the users
     */
    void giveProjectPermissions(String projectKey, List<String> groups, List<CIPermission> permissions);

    /**
     * Set Build Plan Permissions for admins, instructors and teaching assistants.
     *
     * @param programmingExercise a programming exercise with the required information to set the needed build plan permissions
     * @param planName            The name of the source plan
     */
    void givePlanPermissions(ProgrammingExercise programmingExercise, String planName);

    /**
     * Some CI systems give projects default permissions.
     * This method removes all of these unnecessary and potentially insecure permissions.
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
     * @param projectKey  The key of the project related to the build plan
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
                return switch (language) {
                    case JAVA, PYTHON, C, HASKELL, KOTLIN, VHDL, ASSEMBLER, SWIFT, OCAML, EMPTY, RUST, JAVASCRIPT, R -> "assignment";
                    case C_SHARP, C_PLUS_PLUS, SQL, TYPESCRIPT, GO, MATLAB, BASH, RUBY, POWERSHELL, ADA, DART, PHP ->
                        throw new UnsupportedOperationException("Unsupported programming language: " + language);
                };
            }
        },
        TEST {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                return switch (language) {
                    case JAVA, PYTHON, HASKELL, KOTLIN, SWIFT, EMPTY, RUST, JAVASCRIPT, R -> "";
                    case C, VHDL, ASSEMBLER, OCAML -> "tests";
                    case C_SHARP, C_PLUS_PLUS, SQL, TYPESCRIPT, GO, MATLAB, BASH, RUBY, POWERSHELL, ADA, DART, PHP ->
                        throw new UnsupportedOperationException("Unsupported programming language: " + language);
                };
            }
        },
        SOLUTION {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                return switch (language) {
                    case HASKELL, OCAML -> "solution";
                    case JAVA, PYTHON, KOTLIN, SWIFT, EMPTY, C, VHDL, ASSEMBLER, JAVASCRIPT, C_SHARP, C_PLUS_PLUS, SQL, R, TYPESCRIPT, RUST, GO, MATLAB, BASH, RUBY, POWERSHELL,
                            ADA, DART, PHP ->
                        throw new IllegalArgumentException("The solution repository is not checked out during the template/submission build plan for " + language);
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

    enum DependencyDownloadScript {

        GRADLE {

            @Override
            public String getScript() {
                return getBaseScript() + """
                        chmod +x ./gradlew
                        ./gradlew build --refresh-dependencies
                        """;
            }
        },
        MAVEN {

            @Override
            public String getScript() {
                return getBaseScript() + """
                        mvn clean install -U
                        """;
            }
        },
        RUST {

            @Override
            public String getScript() {
                return getBaseScript() + """
                        cargo build --verbose
                        """;
            }
        },
        JAVASCRIPT {

            @Override
            public String getScript() {
                return getBaseScript() + """
                        npm ci --prefer-offline --no-audit
                        """;
            }
        },
        OTHER {

            @Override
            public String getScript() {
                return getBaseScript() + """
                        echo "No dependency download script needed for this programming language"
                        """;
            }
        };

        private static String getBaseScript() {
            return """
                    #!/bin/bash
                    cd /var/tmp/testing-dir
                    #!/usr/bin/env bash
                    set -e
                    """;
        }

        // Abstract method to be implemented by each enum constant.
        public abstract String getScript();
    }

    /**
     * Get the checkout directories for the template and submission build plan for a given programming language.
     *
     * @param programmingLanguage for which the checkout directories should be retrieved
     * @param checkoutSolution    whether the checkout solution repository shall be checked out during the template and submission build plan
     * @return the paths of the checkout directories for the default repositories (exercise, solution, tests) for the
     *         template and submission build plan
     */
    CheckoutDirectoriesDTO getCheckoutDirectories(ProgrammingLanguage programmingLanguage, boolean checkoutSolution);
}

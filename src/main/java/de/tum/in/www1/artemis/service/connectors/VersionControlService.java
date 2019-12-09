package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;

import de.tum.in.www1.artemis.domain.Commit;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;

public interface VersionControlService {

    void configureRepository(URL repositoryUrl, String username);

    /**
     * This creates a WebHook on the Version Control System that notifies the given URL about pushes to the repository. Multiple calls won't affect the result as the implementation
     * must ensure that there is only one WebHook per URL.
     *
     * @param repositoryUrl   The repository to create the hook on
     * @param notificationUrl The URL that should be notified when a push occurred. This includes all arguments.
     * @param webHookName     The name of the WebHook that should be added as additional information (if applicable)
     */
    void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName);

    /**
     * Deletes the project for the given project key
     *
     * @param projectKey of the project that should be deleted
     */
    void deleteProject(String projectKey);

    /**
     * Deletes the repository at the given url
     *
     * @param repositoryUrl of the repository that should be deleted
     */
    void deleteRepository(URL repositoryUrl);

    /**
     * Generates the web url for the repository that belongs to the given participation
     *
     * @param participation a participation of a programming exercise
     * @return the URL of the repository of the participation
     */
    URL getRepositoryWebUrl(ProgrammingExerciseParticipation participation);

    /**
     * Get the clone URL used for cloning
     *
     * @param projectKey     The project key
     * @param repositorySlug The repository slug
     * @return The clone URL
     */
    VcsRepositoryUrl getCloneRepositoryUrl(String projectKey, String repositorySlug);

    /**
     * Check if the given repository url is valid and accessible.
     *
     * @param repositoryUrl repository URL
     * @return whether the repository is valid
     */
    Boolean repositoryUrlIsValid(URL repositoryUrl);

    /**
     * Get the last commit details that are included in the given requestBody that notifies about a push
     *
     * @param requestBody The request Body received from the VCS.
     * @return the last commit details that are included in the given requestBody
     * @throws VersionControlException if the Body could not be parsed
     */
    Commit getLastCommitDetails(Object requestBody) throws VersionControlException;

    /**
     * Creates a project on the VCS.
     *
     * @param programmingExercise for which a project should be created
     * @throws VersionControlException if the project could not be created
     */
    void createProjectForExercise(ProgrammingExercise programmingExercise) throws VersionControlException;

    /**
     * Creates a repository on the VCS.
     *
     * @param repoName         The name of repository
     * @param projectKey       The key of the project that contains the repository (must exist)
     * @param parentProjectKey The key of parent project (for sub-groups in Gitlab), null if not applicable
     * @throws VersionControlException if the repository could not be created
     */
    void createRepository(String projectKey, String repoName, String parentProjectKey) throws VersionControlException;

    /**
     * Gets the repository name of a given repository url
     *
     * @param repositoryUrl The repository url
     * @return The repository name
     */
    String getRepositoryName(URL repositoryUrl);

    /**
     * Checks if the project with the given projectKey already exists
     *
     * @param projectKey to check if a project with this unique key already exists
     * @param projectName to check if a project with the same name already exists
     * @return true if the project exists, false otherwise
     */
    String checkIfProjectExists(String projectKey, String projectName);

    /**
     * Copies a repository from one project to another one. The project can be the same.
     *
     * @param sourceProjectKey The key of the template project (normally based on the course and exercise short name)
     * @param sourceRepositoryName The name of the repository which should be copied
     * @param targetProjectKey The key of the target project to which to copy the new plan to
     * @param targetRepositoryName The desired name of the target repository
     * @return The URL for cloning the repository
     * @throws VersionControlException if the repository could not be copied on the VCS server (e.g. because the source repo does not exist)
     */
    VcsRepositoryUrl copyRepository(String sourceProjectKey, String sourceRepositoryName, String targetProjectKey, String targetRepositoryName) throws VersionControlException;

    /**
     * Removes the user's write permissions for a repository.
     *
     * @param repositoryUrl     The repository url of the repository to update. It contains the project key & the repository name.
     * @param projectKey        The projectKey that the repo is part of in the VCS.
     * @param username          String to identify the user with.
     * @throws Exception        If the communication with the VCS fails.
     */
    void setRepositoryPermissionsToReadOnly(URL repositoryUrl, String projectKey, String username) throws Exception;

    /**
     * Gets the repository slug from the given URL
     *
     * @param repositoryUrl The complete repository-url (including protocol, host and the complete path)
     * @return The repository slug
     * @throws VersionControlException if the URL is invalid and no repository slug could be extracted
     */
    // TODO: we need this functionality in ParticipationService, but it is really really Bitbucket specific, so we should find a better way to handle this in the future
    String getRepositorySlugFromUrl(URL repositoryUrl) throws VersionControlException;

    /**
     * Checks if the underlying VCS server is up and running and gives some additional information about the running
     * services if available
     *
     * @return The health of the VCS service containing if it is up and running and any additional data, or the throwing exception otherwise
     */
    ConnectorHealth health();
}

package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

import java.net.URL;

public interface VersionControlService {

    public URL copyRepository(URL baseRepositoryUrl, String username);

    public void configureRepository(URL repositoryUrl, String username);

    /**
     * This creates a WebHook on the Version Control System that notifies the given URL about pushes to the repository.
     * Multiple calls won't affect the result as the implementation must ensure that there is only one WebHook per URL.
     *
     * @param repositoryUrl   The repository to create the hook on
     * @param notificationUrl The URL that should be notified when a push occurred. This includes all arguments.
     * @param webHookName     The name of the WebHook that should be added as additional information (if applicable)
     */
    public void addWebHook(URL repositoryUrl, String notificationUrl, String webHookName);

    /**
     * Add a Bamboo-Service on the VCS-Server
     *
     * @param projectKey              The project key
     * @param repositorySlug          The repository slug
     * @param bambooUrl               The base URL of the Bamboo-Server
     * @param buildKey                The buildKey (including Project and Build Plan)
     * @param bambooUsername          The Bamboo Username
     * @param bambooPassword          The Bamboo Password
     */
    //TODO: we should rename this method, because it's not very clear what it means
    public void addBambooService(String projectKey, String repositorySlug, String bambooUrl, String buildKey, String bambooUsername, String bambooPassword);

    /**
     * Deletes the project for the given project key
     *
     * @param projectKey
     */
    public void deleteProject(String projectKey);

    /**
     * Deletes the repository at the given url
     *
     * @param repositoryUrl
     */
    public void deleteRepository(URL repositoryUrl);

    /**
     * Generates the web url for the repository that belongs to the given participation
     *
     * @param participation a participation of a programming exercise
     * @return the URL of the repository of the participation
     */
    public URL getRepositoryWebUrl(Participation participation);

    /**
     * Get the clone URL used for cloning
     *
     * @param projectKey              The project key
     * @param repositorySlug          The repository slug
     * @return                        The clone URL
     */
    public URL getCloneURL(String projectKey, String repositorySlug);

    /**
     * Check if the given repository url is valid and accessible.
     *
     * @param repositoryUrl   repository URL
     * @return                whether the repository is valid
     */
    public Boolean repositoryUrlIsValid(URL repositoryUrl);

    /**
     * Get the last commit hash that is included in the given requestBody that notifies about a push.
     *
     * @param requestBody The request Body received from the VCS.
     * @return the last commit hash that is included in the given requestBody
     * @throws Exception if the Body could not be parsed
     */
    public String getLastCommitHash(Object requestBody) throws Exception;

    /**
     * Creates a project on the VCS.
     *
     * @param programmingExercise
     * @throws Exception if the project could not be created
     */
    public void createProjectForExercise(ProgrammingExercise programmingExercise) throws Exception;

    /**
     * Creates a repository on the VCS.
     *
     * @param repoName The name of repository
     * @param projectKey The key of the project that contains the repository (must exist)
     * @param parentProjectKey The key of parent project (for sub-groups in Gitlab), null if not applicable
     * @throws Exception if the repository could not be created
     */
    public void createRepository(String projectKey, String repoName, String parentProjectKey) throws Exception;

    /**
     * Gets the project name of a given repository url
     *
     * @param repositoryUrl The repository url
     * @return The project name
     */
    public String getProjectName(URL repositoryUrl);

    /**
     * Gets the repository name of a given repository url
     *
     * @param repositoryUrl The repository url
     * @return The repository name
     */
    public String getRepositoryName(URL repositoryUrl);

    /**
     * Checks if the project with the given projectKey already exists
     *
     * @param projectKey
     * @return true if the project exists, false otherwise
     */
    public boolean checkIfProjectExists(String projectKey);
}

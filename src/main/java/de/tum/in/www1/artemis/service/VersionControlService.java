package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;

import java.net.URL;

/**
 * Created by muenchdo on 07/09/16.
 */
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
    public void addBambooService(String projectKey, String repositorySlug, String bambooUrl, String buildKey, String bambooUsername, String bambooPassword);

    public void deleteRepository(URL repositoryUrl);

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
     * @return
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
     * @param projectName The name of the new project
     * @param projectKey The key/short name of tne new project
     * @throws Exception if the project could not be created
     */
    public void createProject(String projectName, String projectKey) throws Exception;


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
     * Grants permissions for the instructors/teachingAssistants on a project.
     *
     * @param projectKey The key of the project
     * @param instructorGroupName The name of the group that contains the instructors
     * @param teachingAssistantGroupName The name of the group that contains the teachingAssistants
     */
    public void grantProjectPermissions(String projectKey, String instructorGroupName, String teachingAssistantGroupName);

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

}

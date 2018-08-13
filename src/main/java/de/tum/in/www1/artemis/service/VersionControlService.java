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

    public void deleteRepository(URL repositoryUrl);

    public URL getRepositoryWebUrl(Participation participation);

    /**
     * Check if the given repository url is valid and accessible.
     *
     * @param repositoryUrl   repository URL
     * @return
     */
    public Boolean repositoryUrlIsValid(URL repositoryUrl);

    /**
     * Whether an WebHook should be created, that informs the CI-server about a push
     *
     * @return whether the WebHook should be created
     */
    public Boolean isCreateCIWebHook();

    /**
     * Get the last commit hash that is included in the given requestBody that notifies about a push.
     *
     * @param requestBody The request Body received from the VCS.
     * @return the last commit hash that is included in the given requestBody
     * @throws Exception if the Body could not be parsed
     */
    public String getLastCommitHash(Object requestBody) throws Exception;
}

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
     * This creates a Webhook on the Version Control System that notifies ArTEMiS about pushes to the repository.
     *
     * @param repositoryUrl   The repository to create the hook on
     * @param notificationUrl The URL that should be notified when a push occurred. This includes all arguments.
     */
    public void addWebHook(URL repositoryUrl, String notificationUrl);

    public void deleteRepository(URL repositoryUrl);

    public URL getRepositoryWebUrl(Participation participation);

    /**
     * Check if the given repository url is valid and accessible.
     *
     * @param repositoryUrl   repository URL
     * @return
     */
    public Boolean repositoryUrlIsValid(URL repositoryUrl);
}

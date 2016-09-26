package de.tum.in.www1.exerciseapp.service;

import java.net.URL;
import java.util.Map;

/**
 * Created by muenchdo on 07/09/16.
 */
public interface VersionControlService {

    URL copyRepository(URL baseRepositoryUrl, String username);

    @Deprecated
    Map<String, String> copyRepository(String baseProjectKey, String baseRepositorySlug, String username);

    void configureRepository(URL repositoryUrl, String username);

    @Deprecated
    void configureRepository(String projectKey, String repositorySlug, String username);

    void deleteRepository(URL repositoryUrl);

    @Deprecated
    void deleteRepository(String projectKey, String repositorySlug);

    String getRepositoryWebUrl();
}

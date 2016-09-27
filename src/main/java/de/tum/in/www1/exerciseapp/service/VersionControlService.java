package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;

import java.net.URL;
import java.util.Map;

/**
 * Created by muenchdo on 07/09/16.
 */
public interface VersionControlService {

    URL copyRepository(URL baseRepositoryUrl, String username);

    void configureRepository(URL repositoryUrl, String username);

    void deleteRepository(URL repositoryUrl);

    URL getRepositoryWebUrl(Participation participation);
}

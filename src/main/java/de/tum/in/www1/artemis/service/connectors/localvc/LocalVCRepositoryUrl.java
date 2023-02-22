package de.tum.in.www1.artemis.service.connectors.localvc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.LocalVCException;

public class LocalVCRepositoryUrl extends VcsRepositoryUrl {

    public LocalVCRepositoryUrl(String projectKey, String repositorySlug, URL localVCServerUrl) {
        final String urlString = localVCServerUrl + buildRepositoryPath(projectKey, repositorySlug) + ".git";
        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCException("Could not create local VC Repository URL", e);
        }
    }

    public LocalVCRepositoryUrl(Path repositoryPath, URL localVCServerUrl) {
        String projectKey = repositoryPath.getParent().getFileName().toString();
        String repositorySlug = repositoryPath.getFileName().toString();
        if (!repositorySlug.endsWith(".git") || !repositorySlug.toLowerCase().startsWith(projectKey.toLowerCase())) {
            throw new LocalVCException("Invalid repository path: " + repositoryPath);
        }

        final String urlString = localVCServerUrl + buildRepositoryPath(projectKey, repositorySlug);
        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCException("Could not create local VC Repository URL", e);
        }
    }

    private String buildRepositoryPath(String projectKey, String repositorySlug) {
        return "/git/" + projectKey + "/" + repositorySlug;
    }
}

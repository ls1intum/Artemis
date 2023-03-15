package de.tum.in.www1.artemis.service.connectors.localvc;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.localvc.LocalVCException;

/**
 * Represents a URL to a local VC repository.
 */
public class LocalVCRepositoryUrl extends VcsRepositoryUrl {

    private final String projectKey;

    private final String repositorySlug;

    private final String repositoryTypeOrUserName;

    private final boolean isPracticeRepository;

    /**
     * Constructor that build a LocalVCRepositoryUrl from a project key and a repository slug.
     *
     * @param projectKey     the project key.
     * @param repositorySlug the repository slug.
     * @param localVCBaseUrl the base URL of the local VC server defined in an environment variable.
     */
    public LocalVCRepositoryUrl(String projectKey, String repositorySlug, URL localVCBaseUrl) {
        final String urlString = localVCBaseUrl + buildRepositoryPath(projectKey, repositorySlug);
        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCException("Could not create local VC Repository URL", e);
        }

        this.projectKey = projectKey;
        this.repositorySlug = repositorySlug;
        this.repositoryTypeOrUserName = getRepositoryTypeOrUserName(repositorySlug, projectKey);
        this.isPracticeRepository = getIsPracticeRepository(repositorySlug, projectKey);
    }

    /**
     * Constructor that builds a LocalVCRepositoryUrl from a URL string.
     *
     * @param urlString      the enire URL string (should already contain the base URL, otherwise an exception is thrown).
     * @param localVCBaseUrl the base URL of the local VC server defined in an environment variable.
     */
    public LocalVCRepositoryUrl(String urlString, URL localVCBaseUrl) {
        if (!urlString.startsWith(localVCBaseUrl.toString())) {
            throw new LocalVCException("Invalid local VC Repository URL: " + urlString);
        }

        Path urlPath = Paths.get(urlString.replaceFirst(localVCBaseUrl.toString(), ""));

        if (!urlPath.getName(0).toString().equals("git") || !urlPath.getName(2).toString().endsWith(".git")) {
            throw new LocalVCException("Invalid local VC Repository URL: " + urlString);
        }

        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCException("Could not create local VC Repository URL", e);
        }

        this.projectKey = urlPath.getName(1).toString();
        this.repositorySlug = urlPath.getName(2).toString().replace(".git", "");
        this.repositoryTypeOrUserName = getRepositoryTypeOrUserName(repositorySlug, projectKey);
        this.isPracticeRepository = getIsPracticeRepository(repositorySlug, projectKey);
    }

    /**
     * Constructor that builds a LocalVCRepositoryUrl from a repository path.
     *
     * @param repositoryPath   the path to the repository, also works with a path to a local checked out repository.
     * @param localVCServerUrl the base URL of the local VC server defined in an environment variable.
     */
    public LocalVCRepositoryUrl(Path repositoryPath, URL localVCServerUrl) {
        if (repositoryPath.getFileName().toString().equals(".git")) {
            // This is the case when a local repository path is passed instead of a path to a remote repository in the "local-vcs-repos" folder.
            // In this case we remove the ".git" suffix.
            repositoryPath = repositoryPath.getParent();
        }

        String projectKey = repositoryPath.getParent().getFileName().toString();
        String repositorySlug = repositoryPath.getFileName().toString().replace(".git", "");
        if (!repositorySlug.toLowerCase().startsWith(projectKey.toLowerCase())) {
            throw new LocalVCException("Invalid repository path: " + repositoryPath);
        }

        final String urlString = localVCServerUrl + buildRepositoryPath(projectKey, repositorySlug);
        try {
            this.uri = new URI(urlString);
        }
        catch (URISyntaxException e) {
            throw new LocalVCException("Could not create local VC Repository URL", e);
        }

        this.projectKey = projectKey;
        this.repositorySlug = repositorySlug;
        this.repositoryTypeOrUserName = getRepositoryTypeOrUserName(repositorySlug, projectKey);
        this.isPracticeRepository = getIsPracticeRepository(repositorySlug, projectKey);
    }

    private String buildRepositoryPath(String projectKey, String repositorySlug) {
        return "/git/" + projectKey + "/" + repositorySlug + ".git";
    }

    private String getRepositoryTypeOrUserName(String repositorySlug, String projectKey) {
        String repositoryTypeOrUserNameWithPracticePrefix = repositorySlug.toLowerCase().replace(projectKey.toLowerCase() + "-", "");
        return repositoryTypeOrUserNameWithPracticePrefix.replace("practice-", "");
    }

    private boolean getIsPracticeRepository(String repositorySlug, String projectKey) {
        return repositorySlug.toLowerCase().startsWith(projectKey.toLowerCase() + "-practice-");
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    public String getRepositoryTypeOrUserName() {
        return repositoryTypeOrUserName;
    }

    /**
     * @return true if the repository slug contains "-practice-" and false otherwise.
     */
    public boolean isPracticeRepository() {
        return isPracticeRepository;
    }

    /**
     * Get the path to the repository saved in the folder for the local VC system.
     *
     * @param localVCBasePath the base path of the local VC system defined in an environment variable.
     * @return the path to the repository.
     */
    public Path getLocalRepositoryPath(String localVCBasePath) {
        return Paths.get(localVCBasePath, projectKey, repositorySlug + ".git");
    }
}

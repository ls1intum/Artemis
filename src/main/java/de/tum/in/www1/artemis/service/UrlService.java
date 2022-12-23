package de.tum.in.www1.artemis.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.VersionControlException;

@Service
public class UrlService {

    private final Logger log = LoggerFactory.getLogger(UrlService.class);

    /**
     * Gets the repository slug from the given repository URL, see {@link #getRepositorySlugFromUrl}
     *
     * @param repositoryUrl The repository url object
     * @return The repository slug
     * @throws VersionControlException if the URL is invalid and no repository slug could be extracted
     */
    public String getRepositorySlugFromRepositoryUrl(VcsRepositoryUrl repositoryUrl) throws VersionControlException {
        return getRepositorySlugFromUrl(repositoryUrl.getURI());
    }

    /**
     * Gets the repository slug from the given repository URL string, see {@link #getRepositorySlugFromUrl}
     * @param repositoryUrl The repository url as string
     * @return The repository slug
     * @throws VersionControlException if the URL is invalid and no repository slug could be extracted
     */
    public String getRepositorySlugFromRepositoryUrlString(String repositoryUrl) throws VersionControlException {
        try {
            return getRepositorySlugFromUrl(new URI(repositoryUrl));
        }
        catch (URISyntaxException e) {
            log.error("Cannot get repository slug from repository url string {}", repositoryUrl, e);
            throw new VersionControlException("Repository URL is not a git URL! Can't get repository slug for " + repositoryUrl);
        }
    }

    /**
     * Gets the repository slug from the given URL
     *
     * Example 1: https://ga42xab@bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git --> RMEXERCISE-ga42xab
     * Example 2: https://ga63fup@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga63fup.git --> RMEXERCISE-ga63fup
     * Example 3: https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git --> testadapter-exercise
     * Example 4: https://turdiu@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-turdiu.git --> ftcscagrading1-turdiu
     *
     * @param url The complete repository url (including protocol, host and the complete path)
     * @return The repository slug, i.e. the part of the url that identifies the repository (not the project) without .git in the end
     * @throws VersionControlException if the URL is invalid and no repository slug could be extracted
     */
    private String getRepositorySlugFromUrl(URI url) throws VersionControlException {
        // split the URL path in components using the separator "/"
        final var pathComponents = url.getPath().split("/");
        if (pathComponents.length < 2) {
            throw new VersionControlException("Repository URL is not a git URL! Can't get repository slug for " + url);
        }
        // Note: pathComponents[] = "" because the path always starts with "/"
        // take the last element
        String repositorySlug = pathComponents[pathComponents.length - 1];
        // if the element includes ".git" ...
        if (repositorySlug.endsWith(".git")) {
            // ... cut out the ending ".git", i.e. the last 4 characters
            repositorySlug = repositorySlug.substring(0, repositorySlug.length() - 4);
        }
        return repositorySlug;
    }

    /**
     * Gets the project key + repository slug from the given repository URL, ee {@link #getRepositoryPathFromUrl}
     *
     * @param repositoryUrl The repository url object
     * @throws VersionControlException if the URL is invalid and no project key could be extracted
     * @return <project key>/<repositorySlug>
     */
    public String getRepositoryPathFromRepositoryUrl(VcsRepositoryUrl repositoryUrl) throws VersionControlException {
        return getRepositoryPathFromUrl(repositoryUrl.getURI());
    }

    /**
     * Gets the project key + repository slug from the given URL
     *
     * Example: https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git --> TESTADAPTER/testadapter-exercise
     *
     * @param url The complete repository url (including protocol, host and the complete path)
     * @throws VersionControlException if the URL is invalid and no project key could be extracted
     * @return <project key>/<repositorySlug>
     */
    private String getRepositoryPathFromUrl(URI url) throws VersionControlException {
        // split the URL path in components using the separator "/"
        final var pathComponents = url.getPath().split("/");
        if (pathComponents.length < 2) {
            throw new VersionControlException("Repository URL is not a git URL! Can't get repository slug for " + url);
        }
        // Note: pathComponents[] = "" because the path always starts with "/"
        final var last = pathComponents.length - 1;
        return pathComponents[last - 1] + "/" + pathComponents[last].replace(".git", "");
    }

    /**
     * Gets the project key from the given repository URL, see {@link #getProjectKeyFromUrl}
     *
     * @param repositoryUrl The repository url object
     * @return The project key
     * @throws VersionControlException if the URL is invalid and no project key could be extracted
     */
    public String getProjectKeyFromRepositoryUrl(VcsRepositoryUrl repositoryUrl) throws VersionControlException {
        return getProjectKeyFromUrl(repositoryUrl.getURI());
    }

    /**
     * Gets the project key from the given URL
     *
     * Example: https://ga42xab@bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git --> EIST2016RME
     *
     * @param url The complete repository url (including protocol, host and the complete path)
     * @return The project key
     * @throws VersionControlException if the URL is invalid and no project key could be extracted
     */
    private String getProjectKeyFromUrl(URI url) throws VersionControlException {
        // split the URL path in components using the separator "/"
        final var pathComponents = url.getPath().split("/");
        if (pathComponents.length <= 2) {
            throw new VersionControlException("No project key could be found for " + url);
        }
        // Note: pathComponents[] = "" because the path always starts with "/"
        var projectKey = pathComponents[1];
        if ("scm".equals(pathComponents[1]) || "git".equals(pathComponents[1])) {
            // special case for Bitbucket and local Git
            projectKey = pathComponents[2];
        }
        return projectKey;
    }
}

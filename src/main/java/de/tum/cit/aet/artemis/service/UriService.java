package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

@Profile(PROFILE_CORE)
@Service
public class UriService {

    private static final Logger log = LoggerFactory.getLogger(UriService.class);

    /**
     * Gets the repository slug from the given repository URI, see {@link #getRepositorySlugFromUri}
     *
     * @param repositoryUri The repository uri object
     * @return The repository slug
     * @throws VersionControlException if the URI is invalid and no repository slug could be extracted
     */
    public String getRepositorySlugFromRepositoryUri(VcsRepositoryUri repositoryUri) throws VersionControlException {
        return getRepositorySlugFromUri(repositoryUri.getURI());
    }

    /**
     * Gets the repository slug from the given repository URI string, see {@link #getRepositorySlugFromUri}
     *
     * @param repositoryUri The repository uri as string
     * @return The repository slug
     * @throws VersionControlException if the URI is invalid and no repository slug could be extracted
     */
    public String getRepositorySlugFromRepositoryUriString(String repositoryUri) throws VersionControlException {
        try {
            return getRepositorySlugFromUri(new URI(repositoryUri));
        }
        catch (URISyntaxException e) {
            log.error("Cannot get repository slug from repository uri string {}", repositoryUri, e);
            throw new VersionControlException("Repository URI is not a git URI! Can't get repository slug for " + repositoryUri);
        }
    }

    /**
     * Gets the repository slug from the given URI
     * Example 1: https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git --> testadapter-exercise
     * Example 2: https://turdiu@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-turdiu.git --> ftcscagrading1-turdiu
     *
     * @param uri The complete repository uri (including protocol, host and the complete path)
     * @return The repository slug, i.e. the part of the uri that identifies the repository (not the project) without .git in the end
     * @throws VersionControlException if the URI is invalid and no repository slug could be extracted
     */
    private String getRepositorySlugFromUri(URI uri) throws VersionControlException {
        // split the URI path in components using the separator "/"
        final var pathComponents = uri.getPath().split("/");
        if (pathComponents.length < 2) {
            throw new VersionControlException("Repository URI is not a git URI! Can't get repository slug for " + uri);
        }
        // Note: pathComponents[0] = "" because the path always starts with "/"
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
     * Gets the project key + repository slug from the given repository URI, ee {@link #getRepositoryPathFromUri}
     *
     * @param repositoryUri The repository uri object
     * @return <project key>/<repositorySlug>
     * @throws VersionControlException if the URI is invalid and no project key could be extracted
     */
    public String getRepositoryPathFromRepositoryUri(VcsRepositoryUri repositoryUri) throws VersionControlException {
        return getRepositoryPathFromUri(repositoryUri.getURI());
    }

    /**
     * Gets the project key + repository slug from the given URI
     * <p>
     * Example: https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git --> TESTADAPTER/testadapter-exercise
     *
     * @param uri The complete repository uri (including protocol, host and the complete path)
     * @return <project key>/<repositorySlug>
     * @throws VersionControlException if the URI is invalid and no project key could be extracted
     */
    private String getRepositoryPathFromUri(URI uri) throws VersionControlException {
        // split the URI path in components using the separator "/"
        final var pathComponents = uri.getPath().split("/");
        if (pathComponents.length < 2) {
            throw new VersionControlException("Repository URI is not a git URI! Can't get repository slug for " + uri);
        }
        // Note: pathComponents[0] = "" because the path always starts with "/"
        final var last = pathComponents.length - 1;
        return pathComponents[last - 1] + "/" + pathComponents[last].replace(".git", "");
    }

    /**
     * Gets the project key from the given repository URI, see {@link #getProjectKeyFromUri}
     *
     * @param repositoryUri The repository uri object
     * @return The project key
     * @throws VersionControlException if the URI is invalid and no project key could be extracted
     */
    public String getProjectKeyFromRepositoryUri(VcsRepositoryUri repositoryUri) throws VersionControlException {
        return getProjectKeyFromUri(repositoryUri.getURI());
    }

    /**
     * Gets the project key from the given URI
     * <p>
     * Examples:
     * https://ga42xab@https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git --> TESTADAPTER
     * http://localhost:8080/git/TESTCOURSE1TESTEX1/testcourse1testex1-student1.git --> TESTCOURSE1TESTEX1
     *
     * @param uri The complete repository uri (including protocol, host and the complete path)
     * @return The project key
     * @throws VersionControlException if the URI is invalid and no project key could be extracted
     */
    private String getProjectKeyFromUri(URI uri) throws VersionControlException {
        // split the URI path in components using the separator "/"
        final var pathComponents = uri.getPath().split("/");
        if (pathComponents.length <= 2) {
            throw new VersionControlException("No project key could be found for " + uri);
        }
        // Note: pathComponents[0] = "" because the path always starts with "/"
        var projectKey = pathComponents[1];
        if ("scm".equals(pathComponents[1]) || "git".equals(pathComponents[1])) {
            // special case local VC
            projectKey = pathComponents[2];
        }
        return projectKey;
    }

    /**
     * Gets the plain URI from the given repository URI,
     * https://username@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username --> https://artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username
     *
     * @param repositoryUri The repository uri object
     * @return The plain URI
     * @throws VersionControlException if the URI is invalid and no plain URI could be extracted
     */
    public String getPlainUriFromRepositoryUri(VcsRepositoryUri repositoryUri) throws VersionControlException {
        var uri = repositoryUri.getURI();
        try {
            var updatedUri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, uri.getFragment());
            return updatedUri.toString();
        }
        catch (URISyntaxException e) {
            throw new VersionControlException(e);
        }
    }
}

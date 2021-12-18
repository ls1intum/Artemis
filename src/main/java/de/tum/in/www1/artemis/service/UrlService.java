package de.tum.in.www1.artemis.service;

import java.net.MalformedURLException;
import java.net.URL;

import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.VersionControlException;

public class UrlService {

    /**
     * Gets the repository slug from the given repository URL, see {@link #getRepositorySlugFromUrl}
     *
     * @param repositoryUrl The repository url object
     * @return The repository slug
     * @throws VersionControlException if the URL is invalid and no repository slug could be extracted
     */
    public static String getRepositorySlugFromRepositoryUrl(VcsRepositoryUrl repositoryUrl) throws VersionControlException {
        return getRepositorySlugFromUrl(repositoryUrl.getURL());
    }

    public static String getRepositorySlugFromRepositoryUrlString(String repositoryUrl) throws VersionControlException {
        try {
            String slug = getRepositorySlugFromUrl(new URL(repositoryUrl));
            System.out.println(slug);
            return slug;
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
            // TODO: better handling
            return null;
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
    private static String getRepositorySlugFromUrl(URL url) throws VersionControlException {
        // split the URL path in components using the separator "/"
        final var pathComponents = url.getFile().split("/");
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
    public static String getRepositoryPathFromRepositoryUrl(VcsRepositoryUrl repositoryUrl) throws VersionControlException {
        return getRepositoryPathFromUrl(repositoryUrl.getURL());
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
    private static String getRepositoryPathFromUrl(URL url) throws VersionControlException {
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
    public static String getProjectKeyFromRepositoryUrl(VcsRepositoryUrl repositoryUrl) throws VersionControlException {
        return getProjectKeyFromUrl(repositoryUrl.getURL());
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
    private static String getProjectKeyFromUrl(URL url) throws VersionControlException {
        // split the URL path in components using the separator "/"
        final var pathComponents = url.getPath().split("/");
        if (pathComponents.length <= 2) {
            throw new VersionControlException("No project key could be found for " + url);
        }
        // Note: pathComponents[] = "" because the path always starts with "/"
        var projectKey = pathComponents[1];
        if ("scm".equals(pathComponents[1])) {
            // special case for Bitbucket
            projectKey = pathComponents[2];
        }
        return projectKey;
    }
}

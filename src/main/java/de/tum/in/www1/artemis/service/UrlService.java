package de.tum.in.www1.artemis.service;

import java.net.URL;

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
        return getRepositorySlugFromUrl(repositoryUrl.getURL());
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
    public String getRepositorySlugFromUrl(URL url) throws VersionControlException {
        // split the URL in parts using the separator "/"
        String[] urlParts = url.getFile().split("/");
        if (urlParts.length < 1) {
            throw new VersionControlException("Repository URL is not a git URL! Can't get repository slug for " + url.toString());
        }
        // take the last element
        String repositorySlug = urlParts[urlParts.length - 1];
        // if the element includes ".git" ...
        if (repositorySlug.endsWith(".git")) {
            // ... cut out the ending ".git", i.e. the last 4 characters
            repositorySlug = repositorySlug.substring(0, repositorySlug.length() - 4);
        }
        log.debug("getRepositorySlugFromUrl " + url + " --> " + repositorySlug);
        return repositorySlug;
    }

    /**
     * Gets the project key from the given repository URL, see {@link #getProjectKeyFromUrl}
     *
     * @param repositoryUrl The repository url object
     * @return The project key
     * @throws VersionControlException if the URL is invalid and no project key could be extracted
     */
    public String getProjectKeyFromRepositoryUrl(VcsRepositoryUrl repositoryUrl) throws VersionControlException {
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
    public String getProjectKeyFromUrl(URL url) throws VersionControlException {
        String[] urlParts = url.getFile().split("/");
        if (urlParts.length <= 2) {
            throw new VersionControlException("No project key could be found for " + url.toString());
        }
        var projectKey = urlParts[2];
        log.debug("getProjectKeyFromUrl " + url + " --> " + projectKey);
        return projectKey;
    }
}

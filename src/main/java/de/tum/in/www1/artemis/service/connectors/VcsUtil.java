package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;

public class VcsUtil {

    /**
     * Gets the repository slug from the given URL
     *
     * @param repositoryUrl The complete repository-url (including protocol, host and the complete path)
     * @return The repository slug
     */
    public static String getRepositorySlugFromUrl(URL repositoryUrl) {
        // https://ga42xab@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git
        String[] urlParts = repositoryUrl.getFile().split("/");
        String repositorySlug = urlParts[urlParts.length - 1];
        if (repositorySlug.endsWith(".git")) {
            repositorySlug = repositorySlug.substring(0, repositorySlug.length() - 4);
        }
        else {
            throw new IllegalArgumentException("No repository slug could be found");
        }

        return repositorySlug;
    }
}

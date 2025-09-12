package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Utility class for converting between full (as expected in the domain) and short (stored in the database) repository URIs.
 */
@Component
@Profile(PROFILE_LOCALVC)
@Lazy
public class RepositoryUriConversionUtil {

    private static final String GIT_PREFIX = "/git/";

    private static final String GIT_SUFFIX = ".git";

    private static final Logger log = LoggerFactory.getLogger(RepositoryUriConversionUtil.class);

    @Value("${artemis.version-control.url}")
    private String serverUrl;

    private static String staticServerUrl;

    /**
     * This looks weird but is required to benefit from Spring's @Value injection but being able to use static methods.
     */
    @PostConstruct
    private void init() {
        staticServerUrl = serverUrl;
    }

    /**
     * Converts a full repository URI to a short URI for database storage.
     * Removes the server URL, /git/ prefix, and .git suffix.
     *
     * @param fullUri the full repository URI (e.g., "https://artemis.tum.de/git/project/exercise-name.git")
     * @return the short URI (e.g., "project/exercise-name") or null if the input is null/empty
     */
    public static String toShortRepositoryUri(String fullUri) {
        if (fullUri == null || fullUri.isEmpty()) {
            return null;
        }

        String uri = fullUri;

        if (staticServerUrl == null) {
            throw new IllegalStateException("Server URL is not configured. Cannot convert to short repository URI.");
        }

        String serverUrlWithGit = staticServerUrl + GIT_PREFIX;
        if (uri.startsWith(serverUrlWithGit)) {
            uri = uri.substring(serverUrlWithGit.length());
        }

        if (uri.endsWith(GIT_SUFFIX)) {
            uri = uri.substring(0, uri.length() - GIT_SUFFIX.length());
        }

        return uri;
    }

    /**
     * Converts a short repository URI to a full URI for domain logic usage.
     * Adds the server URL, /git/ prefix, and .git suffix.
     *
     * @param shortUri the short repository URI (e.g., "project/exercise-name")
     * @return the full URI (e.g., "https://artemis.tum.de/git/project/exercise-name.git") or null if the input is null/empty
     */
    public static String toFullRepositoryUri(String shortUri) {
        if (shortUri == null || shortUri.isEmpty()) {
            return null;
        }

        if (staticServerUrl == null) {
            throw new IllegalStateException("Server URL is not configured. Cannot convert to full repository URI.");
        }
        log.debug("Converting short repository url to full repository url with the following configured server URL: {} and short uri {}", staticServerUrl, shortUri);
        var result = staticServerUrl + GIT_PREFIX + shortUri + GIT_SUFFIX;
        log.debug("Converted full repository URI: {}", result);
        return result;
    }
}

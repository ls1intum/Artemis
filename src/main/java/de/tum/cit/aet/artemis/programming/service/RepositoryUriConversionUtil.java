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
 * Parallel-test safe:
 * - No globally mutable server URL.
 * - Supports per-thread overrides for tests that run multiple Spring contexts in parallel.
 */
@Component
@Profile(PROFILE_LOCALVC)
@Lazy
public final class RepositoryUriConversionUtil {

    private static final Logger log = LoggerFactory.getLogger(RepositoryUriConversionUtil.class);

    private static final String GIT_PREFIX = "/git/";

    private static final String GIT_SUFFIX = ".git";

    /**
     * Immutable default server URL captured from the (first) Spring context that initializes this bean.
     * It is intentionally final and never mutated after init.
     */
    private static volatile String defaultServerUrl;

    /**
     * Per-thread override for tests. If set, static methods will use this value
     * instead of {@link #defaultServerUrl}. This prevents cross-test leakage when
     * different test threads have different Spring contexts / properties.
     */
    private static final ThreadLocal<String> threadServerUrlOverride = new ThreadLocal<>();

    @Value("${artemis.version-control.url}")
    private String injectedServerUrl;

    @PostConstruct
    private void init() {
        final String normalized = normalizeBaseUrl(injectedServerUrl);
        // Capture the first initialized value only; don't overwrite on later contexts
        // (production has one context; in tests, use per-thread override below).
        if (defaultServerUrl == null) {
            defaultServerUrl = normalized;
            log.debug("RepositoryUriConversionUtil default server URL set to {}", defaultServerUrl);
        }
        else {
            log.debug("RepositoryUriConversionUtil already initialized with default server URL: {}", defaultServerUrl);
        }
    }

    /**
     * Converts a full repository URI to a short URI for database storage.
     * Removes the server URL, /git/ prefix, and .git suffix.
     *
     * @param fullUri the full repository URI (e.g., "https://artemis.tum.de/git/project/exercise-name.git")
     * @return the short URI (e.g., "project/exercise-name") or null if the input is null/empty
     */

    public static String toShortRepositoryUri(String fullUri) {
        if (isNullOrEmpty(fullUri)) {
            return null;
        }
        String base = currentServerUrl();
        String uri = fullUri;

        if (base == null) {
            throw new IllegalStateException("Server URL is not configured. Cannot convert to full repository URI.");
        }

        String serverUrlWithGit = base + GIT_PREFIX;
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
        if (isNullOrEmpty(shortUri)) {
            return null;
        }
        String base = currentServerUrl();
        if (base == null) {
            throw new IllegalStateException("Server URL is not configured. Cannot convert to full repository URI.");
        }
        return base + GIT_PREFIX + shortUri + GIT_SUFFIX;
    }

    /**
     * Override the server URL for the current thread (e.g., in tests that run in parallel).
     * Call in @BeforeEach (and clear in @AfterEach) of your test class.
     *
     * @param serverUrl the server URL to use for this thread (e.g., "http://localhost:49152")
     * @see #clearServerUrlOverrideForCurrentThread()
     */
    public static void overrideServerUrlForCurrentThread(String serverUrl) {
        threadServerUrlOverride.set(normalizeBaseUrl(serverUrl));
    }

    /**
     * Clear the per-thread override. Call in @AfterEach.
     *
     * @see #overrideServerUrlForCurrentThread(String)
     */
    public static void clearServerUrlOverrideForCurrentThread() {
        threadServerUrlOverride.remove();
    }

    private static String currentServerUrl() {
        String override = threadServerUrlOverride.get();
        return override != null ? override : defaultServerUrl;
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("/+$", "");
    }

    private static boolean isNullOrEmpty(String uriString) {
        return uriString == null || uriString.isEmpty();
    }
}

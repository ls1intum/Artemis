package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.SemverException;

import de.tum.cit.aet.artemis.core.dto.ArtemisVersionDTO;

/**
 * Service for checking the latest Artemis version from GitHub releases.
 * <p>
 * This service queries the GitHub API to determine if a newer version of Artemis
 * is available and provides information about the latest release.
 * <p>
 * Results are cached to avoid excessive API calls to GitHub.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class ArtemisVersionService {

    private static final Logger log = LoggerFactory.getLogger(ArtemisVersionService.class);

    /**
     * GitHub API URL for fetching the latest release.
     */
    private static final String GITHUB_RELEASES_API_URL = "https://api.github.com/repos/ls1intum/Artemis/releases/latest";

    /**
     * Cache name for storing version data in Hazelcast.
     */
    private static final String VERSION_CACHE_NAME = "artemisVersion";

    /**
     * Cache key for the version data.
     */
    private static final String VERSION_CACHE_KEY = "latestVersion";

    private final RestTemplate restTemplate;

    private final CacheManager cacheManager;

    @Value("${artemis.version:unknown}")
    private String currentVersion;

    /**
     * Creates a new ArtemisVersionService with the required dependencies.
     *
     * @param restTemplate the REST client for making HTTP requests to GitHub API
     * @param cacheManager the cache manager for distributed caching (Hazelcast)
     */
    public ArtemisVersionService(RestTemplate restTemplate, CacheManager cacheManager) {
        this.restTemplate = restTemplate;
        this.cacheManager = cacheManager;
    }

    /**
     * Gets the current and latest version information for Artemis.
     * <p>
     * Results are cached to minimize GitHub API calls.
     *
     * @return DTO containing current version, latest version, and update status
     */
    public ArtemisVersionDTO getVersionInfo() {
        // Try to retrieve from cache
        var cache = cacheManager.getCache(VERSION_CACHE_NAME);
        if (cache != null) {
            ArtemisVersionDTO cachedResult = cache.get(VERSION_CACHE_KEY, ArtemisVersionDTO.class);
            if (cachedResult != null) {
                log.debug("Returning cached version info, last checked: {}", cachedResult.lastChecked());
                return cachedResult;
            }
        }

        log.info("Fetching latest Artemis version from GitHub");
        ArtemisVersionDTO result = fetchVersionFromGitHub();

        // Store in cache
        if (cache != null) {
            cache.put(VERSION_CACHE_KEY, result);
        }

        return result;
    }

    /**
     * Forces a refresh of version information from GitHub API.
     *
     * @return DTO containing fresh version information
     */
    public ArtemisVersionDTO refreshVersionInfo() {
        log.info("Force refreshing version info from GitHub");

        var cache = cacheManager.getCache(VERSION_CACHE_NAME);
        if (cache != null) {
            cache.evict(VERSION_CACHE_KEY);
        }

        ArtemisVersionDTO result = fetchVersionFromGitHub();

        if (cache != null) {
            cache.put(VERSION_CACHE_KEY, result);
        }

        return result;
    }

    /**
     * Fetches the latest release information from GitHub API.
     *
     * @return DTO containing version information
     */
    private ArtemisVersionDTO fetchVersionFromGitHub() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github+json");
            headers.set("User-Agent", "Artemis-Version-Check");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<GitHubReleaseResponse> response = restTemplate.exchange(GITHUB_RELEASES_API_URL, HttpMethod.GET, requestEntity, GitHubReleaseResponse.class);

            GitHubReleaseResponse release = response.getBody();
            if (release == null || release.tagName() == null) {
                return createVersionInfoWithoutUpdate();
            }

            String latestVersion = normalizeVersion(release.tagName());
            boolean updateAvailable = isNewerVersionAvailable(currentVersion, latestVersion);

            return new ArtemisVersionDTO(currentVersion, latestVersion, updateAvailable, release.htmlUrl(), truncateReleaseNotes(release.body()), Instant.now().toString());
        }
        catch (RestClientException e) {
            log.error("Failed to fetch latest version from GitHub: {}", e.getMessage());
            return createVersionInfoWithoutUpdate();
        }
    }

    /**
     * Creates a version info DTO when GitHub API is unavailable.
     *
     * @return DTO with current version only
     */
    private ArtemisVersionDTO createVersionInfoWithoutUpdate() {
        return new ArtemisVersionDTO(currentVersion, null, false, null, null, Instant.now().toString());
    }

    /**
     * Normalizes a version string by removing common prefixes.
     *
     * @param version the version string (e.g., "v7.8.0" or "7.8.0")
     * @return normalized version (e.g., "7.8.0")
     */
    private String normalizeVersion(String version) {
        if (version == null) {
            return "unknown";
        }
        // Remove leading 'v' if present
        if (version.startsWith("v") || version.startsWith("V")) {
            return version.substring(1);
        }
        return version;
    }

    /**
     * Compares versions to determine if an update is available.
     *
     * @param current the current version
     * @param latest  the latest version from GitHub
     * @return true if latest is newer than current
     */
    private boolean isNewerVersionAvailable(String current, String latest) {
        if (current == null || latest == null || "unknown".equals(current)) {
            return false;
        }

        try {
            String normalizedCurrent = normalizeVersion(current);
            Semver currentSemver = new Semver(normalizedCurrent, Semver.SemverType.NPM);
            Semver latestSemver = new Semver(latest, Semver.SemverType.NPM);

            return latestSemver.isGreaterThan(currentSemver);
        }
        catch (SemverException e) {
            log.debug("Failed to compare versions '{}' and '{}': {}", current, latest, e.getMessage());
            // Fall back to string comparison if semver parsing fails
            return !normalizeVersion(current).equals(latest);
        }
    }

    /**
     * Truncates release notes to a reasonable length for display.
     *
     * @param notes the full release notes
     * @return truncated notes (first 500 characters)
     */
    @Nullable
    private String truncateReleaseNotes(@Nullable String notes) {
        if (notes == null) {
            return null;
        }
        // Extract just the first line or first 500 characters
        int firstNewline = notes.indexOf('\n');
        if (firstNewline > 0 && firstNewline < 500) {
            return notes.substring(0, firstNewline).trim();
        }
        if (notes.length() > 500) {
            return notes.substring(0, 497) + "...";
        }
        return notes.trim();
    }

    /**
     * DTO for parsing GitHub release API response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubReleaseResponse(@JsonProperty("tag_name") String tagName, @JsonProperty("html_url") String htmlUrl, @JsonProperty("body") String body,
            @JsonProperty("name") String name) {
    }
}

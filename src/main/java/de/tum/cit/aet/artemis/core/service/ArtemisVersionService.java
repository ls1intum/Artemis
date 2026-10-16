package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.semver4j.Semver;
import org.semver4j.SemverException;
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

import de.tum.cit.aet.artemis.core.dto.ArtemisVersionDTO;
import de.tum.cit.aet.artemis.core.util.ArtemisVersionUtil;

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
     * GitHub API URL for fetching the most recently created releases, newest first.
     * <p>
     * This deliberately does not use {@code /releases/latest}: by default GitHub marks the most recently published release
     * as latest, so publishing a patch release of an older line (a 9.9.x fix after 10.0) would report that patch as latest.
     * <p>
     * Reading only the first page is enough: a new line is always created after the older ones, so its releases stay on the
     * first page unless 30 patches for older lines follow them without any newer release.
     */
    private static final String GITHUB_RELEASES_API_URL = "https://api.github.com/repos/ls1intum/Artemis/releases?per_page=30";

    /**
     * Cache name for storing version data in the distributed cache. Its entries expire, see {@code CacheManagerConfiguration}.
     */
    private static final String VERSION_CACHE_NAME = "artemisVersion";

    /**
     * Prefix of the cache key for the version data, completed by the running version. During a rolling update, nodes on
     * different versions then keep separate entries instead of replacing each other's, and the entries of a version that
     * no longer runs expire with the cache.
     */
    private static final String VERSION_CACHE_KEY_PREFIX = "latestVersion:";

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
            ArtemisVersionDTO cachedResult = cache.get(versionCacheKey(), ArtemisVersionDTO.class);
            if (cachedResult != null) {
                log.debug("Returning cached version info, last checked: {}", cachedResult.lastChecked());
                return cachedResult;
            }
        }

        log.info("Fetching latest Artemis version from GitHub");
        ArtemisVersionDTO result = fetchVersionFromGitHub();

        // Store in cache
        if (cache != null) {
            cache.put(versionCacheKey(), result);
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
            cache.evict(versionCacheKey());
        }

        ArtemisVersionDTO result = fetchVersionFromGitHub();

        if (cache != null) {
            cache.put(versionCacheKey(), result);
        }

        return result;
    }

    private String versionCacheKey() {
        return VERSION_CACHE_KEY_PREFIX + currentVersion;
    }

    /**
     * Fetches the recent releases from GitHub API and picks the highest published version among them.
     *
     * @return DTO containing version information
     */
    private ArtemisVersionDTO fetchVersionFromGitHub() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/vnd.github+json");
            headers.set("User-Agent", "Artemis-Version-Check");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<GitHubReleaseResponse[]> response = restTemplate.exchange(GITHUB_RELEASES_API_URL, HttpMethod.GET, requestEntity, GitHubReleaseResponse[].class);

            GitHubReleaseResponse release = findHighestRelease(response.getBody());
            if (release == null) {
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
     * Picks the release with the highest version, ignoring drafts, pre-releases and tags that are not Artemis versions.
     *
     * @param releases the releases returned by GitHub, may be null
     * @return the highest release, or null if there is none
     */
    @Nullable
    private static GitHubReleaseResponse findHighestRelease(GitHubReleaseResponse @Nullable [] releases) {
        if (releases == null) {
            return null;
        }
        GitHubReleaseResponse highestRelease = null;
        Semver highestVersion = null;
        for (GitHubReleaseResponse release : releases) {
            if (release == null || release.draft() || release.prerelease() || release.tagName() == null) {
                continue;
            }
            try {
                Semver version = ArtemisVersionUtil.parseForComparison(normalizeVersion(release.tagName()));
                if (highestVersion == null || version.isGreaterThan(highestVersion)) {
                    highestRelease = release;
                    highestVersion = version;
                }
            }
            catch (SemverException e) {
                log.debug("Ignoring GitHub release with tag '{}' that is not an Artemis version", release.tagName());
            }
        }
        return highestRelease;
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
    private static String normalizeVersion(String version) {
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
            Semver currentSemver = ArtemisVersionUtil.parseForComparison(normalizedCurrent);
            Semver latestSemver = ArtemisVersionUtil.parseForComparison(latest);

            return latestSemver.isGreaterThan(currentSemver);
        }
        catch (SemverException e) {
            log.debug("Failed to compare versions '{}' and '{}': {}", current, latest, e.getMessage());
            // Malformed inputs must not falsely report "update available"; surface unparseable
            // versions as "no update" rather than guessing via string comparison.
            return false;
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
            @JsonProperty("name") String name, @JsonProperty("draft") boolean draft, @JsonProperty("prerelease") boolean prerelease) {
    }
}

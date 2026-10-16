package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.lang.reflect.Method;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.dto.ArtemisVersionDTO;

/**
 * Unit tests for {@link ArtemisVersionService}.
 * <p>
 * Verifies that the version comparison accepts both the new two-part canonical scheme
 * ({@code "9.2"}) and the legacy three-part hotfix scheme ({@code "10.4.1"}), and that
 * malformed inputs fall back to "no update available" instead of producing a false positive.
 * Also verifies that the latest version is the highest published release rather than the one
 * GitHub flags as latest, and that cached lookups are kept per running version.
 */
class ArtemisVersionServiceTest {

    private static final String RELEASES_URL = "https://api.github.com/repos/ls1intum/Artemis/releases?per_page=30";

    private ArtemisVersionService service;

    private Method isNewerVersionAvailable;

    @BeforeEach
    void setUp() throws Exception {
        service = new ArtemisVersionService(mock(RestTemplate.class), mock(CacheManager.class));
        isNewerVersionAvailable = ArtemisVersionService.class.getDeclaredMethod("isNewerVersionAvailable", String.class, String.class);
        isNewerVersionAvailable.setAccessible(true);
    }

    private boolean call(String current, String latest) throws Exception {
        return (boolean) isNewerVersionAvailable.invoke(service, current, latest);
    }

    @Test
    void detectsUpdateWhenLatestMinorIsHigherTwoPart() throws Exception {
        assertThat(call("9.2", "9.3")).isTrue();
    }

    @Test
    void noUpdateWhenVersionsMatchTwoPart() throws Exception {
        assertThat(call("9.2", "9.2")).isFalse();
    }

    @Test
    void detectsUpdateAcrossMajor() throws Exception {
        assertThat(call("9.2", "10.0")).isTrue();
    }

    @Test
    void detectsUpdateFromHotfixToNextMinor() throws Exception {
        assertThat(call("10.4.1", "10.5")).isTrue();
    }

    @Test
    void fallsBackToNoUpdateOnMalformedLatest() throws Exception {
        assertThat(call("9.2", "garbage")).isFalse();
    }

    @Test
    void stripsLeadingVPrefixInCurrent() throws Exception {
        assertThat(call("v9.2", "9.3")).isTrue();
    }

    @Test
    void reportsHighestReleaseEvenWhenAnOlderLineWasPublishedLater() {
        // GitHub lists releases newest first, so the 9.9.4 patch comes before the 10.0 release it must not outrank
        ArtemisVersionDTO versionInfo = fetchWith("10.0", mock(CacheManager.class), release("9.9.4", false, false), release("10.0", false, false), release("9.9.3", false, false));

        assertThat(versionInfo.latestVersion()).isEqualTo("10.0");
        assertThat(versionInfo.updateAvailable()).isFalse();
        assertThat(versionInfo.releaseUrl()).isEqualTo("https://github.com/ls1intum/Artemis/releases/tag/10.0");
    }

    @Test
    void ignoresDraftsPrereleasesAndTagsThatAreNotVersions() {
        ArtemisVersionDTO versionInfo = fetchWith("9.9.3", mock(CacheManager.class), release("10.2", true, false), release("10.1", false, true), release("nightly", false, false),
                release("v10.0", false, false));

        assertThat(versionInfo.latestVersion()).isEqualTo("10.0");
        assertThat(versionInfo.updateAvailable()).isTrue();
    }

    @Test
    void reportsNoLatestVersionWithoutPublishedRelease() {
        ArtemisVersionDTO versionInfo = fetchWith("10.0", mock(CacheManager.class), release("10.1", false, true));

        assertThat(versionInfo.latestVersion()).isNull();
        assertThat(versionInfo.updateAvailable()).isFalse();
    }

    @Test
    void reusesCachedLookupOfTheRunningVersion() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        ArtemisVersionDTO cached = new ArtemisVersionDTO("10.0", "10.0", false, null, null, Instant.now().toString());
        cacheManager.getCache("artemisVersion").put("latestVersion:10.0", cached);
        RestTemplate restTemplate = new RestTemplate();
        // No request is expected, so any call to GitHub fails the test
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        ArtemisVersionDTO versionInfo = createService(restTemplate, cacheManager, "10.0").getVersionInfo();

        server.verify();
        assertThat(versionInfo).isEqualTo(cached);
    }

    @Test
    void keepsSeparateLookupsPerRunningVersion() {
        CacheManager cacheManager = new ConcurrentMapCacheManager();
        ArtemisVersionDTO cachedForOldVersion = new ArtemisVersionDTO("9.9.3", "9.9.3", false, null, null, Instant.now().toString());
        cacheManager.getCache("artemisVersion").put("latestVersion:9.9.3", cachedForOldVersion);

        ArtemisVersionDTO versionInfo = fetchWith("10.0", cacheManager, release("10.0", false, false));

        assertThat(versionInfo.currentVersion()).isEqualTo("10.0");
        assertThat(versionInfo.latestVersion()).isEqualTo("10.0");
        assertThat(cacheManager.getCache("artemisVersion").get("latestVersion:10.0", ArtemisVersionDTO.class)).isEqualTo(versionInfo);
        assertThat(cacheManager.getCache("artemisVersion").get("latestVersion:9.9.3", ArtemisVersionDTO.class)).isEqualTo(cachedForOldVersion);
    }

    private static ArtemisVersionDTO fetchWith(String currentVersion, CacheManager cacheManager, String... releases) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(RELEASES_URL)).andRespond(withSuccess("[" + String.join(",", releases) + "]", MediaType.APPLICATION_JSON));

        ArtemisVersionDTO versionInfo = createService(restTemplate, cacheManager, currentVersion).getVersionInfo();

        server.verify();
        return versionInfo;
    }

    private static ArtemisVersionService createService(RestTemplate restTemplate, CacheManager cacheManager, String currentVersion) {
        ArtemisVersionService versionService = new ArtemisVersionService(restTemplate, cacheManager);
        ReflectionTestUtils.setField(versionService, "currentVersion", currentVersion);
        return versionService;
    }

    private static String release(String tag, boolean draft, boolean prerelease) {
        return """
                {"tag_name": "%s", "html_url": "https://github.com/ls1intum/Artemis/releases/tag/%s", "body": "notes", "name": "%s", "draft": %b, "prerelease": %b}
                """.formatted(tag, tag, tag, draft, prerelease);
    }
}

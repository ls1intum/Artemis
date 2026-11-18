package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class ArtemisVersionPublicApiTest {

    private static final String RELEASES_URL = "https://api.github.com/repos/ls1intum/Artemis/releases?per_page=30";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "unknown", "invalid", "V10.0", "10.1" })
    void currentVersionComparisonNeverInventsUpdatesForUnknownOrMalformedVersions(String current) {
        RestTemplate rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(RELEASES_URL)).andRespond(withSuccess(releases(release("V10.1", "notes")), MediaType.APPLICATION_JSON));
        var result = service(rest, new ConcurrentMapCacheManager(), current).getVersionInfo();
        assertThat(result.currentVersion()).isEqualTo(current);
        assertThat(result.latestVersion()).isEqualTo("10.1");
        assertThat(result.updateAvailable()).isEqualTo("V10.0".equals(current));
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("releaseNotes")
    void releaseNotesFollowFirstLineAndFiveHundredCharacterDisplayLimit(String notes, String expected) {
        RestTemplate rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(RELEASES_URL)).andExpect(header("Accept", "application/vnd.github+json")).andExpect(header("User-Agent", "Artemis-Version-Check"))
                .andRespond(withSuccess(releases(release("10.1", notes)), MediaType.APPLICATION_JSON));
        Instant beforeRequest = Instant.now();
        var result = service(rest, new ConcurrentMapCacheManager(), "10.0").getVersionInfo();
        assertThat(result.releaseNotes()).isEqualTo(expected);
        assertThat(result.releaseUrl()).isEqualTo("https://release.invalid/10.1");
        assertThat(result.updateAvailable()).isTrue();
        assertThat(Instant.parse(result.lastChecked())).isBetween(beforeRequest, Instant.now());
        server.verify();
    }

    static Stream<Arguments> releaseNotes() {
        return Stream.of(Arguments.of(null, null), Arguments.of("", ""), Arguments.of("  short note  ", "short note"), Arguments.of("  summary  \nrest", "summary"),
                Arguments.of("\nsummary", "summary"), Arguments.of("x".repeat(500), "x".repeat(500)), Arguments.of("x".repeat(501), "x".repeat(497) + "..."),
                Arguments.of("x".repeat(500) + "\nrest", "x".repeat(497) + "..."));
    }

    @Test
    void releaseFilteringIgnoresNullEntriesAndTagsAndRefreshEvictsStaleCache() {
        RestTemplate rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        var invalidTag = release(null, "invalid");
        var draft = release("99.0", "draft");
        draft.put("draft", true);
        var prerelease = release("98.0", "pre");
        prerelease.put("prerelease", true);
        server.expect(requestTo(RELEASES_URL)).andRespond(withSuccess(releases(null, invalidTag, draft, prerelease, release("10.1", "first")), MediaType.APPLICATION_JSON));
        server.expect(requestTo(RELEASES_URL)).andRespond(withSuccess(releases(release("10.2", "second")), MediaType.APPLICATION_JSON));
        var service = service(rest, new ConcurrentMapCacheManager(), "10.0");
        var first = service.getVersionInfo();
        assertThat(first.latestVersion()).isEqualTo("10.1");
        assertThat(service.getVersionInfo()).isSameAs(first);
        var refreshed = service.refreshVersionInfo();
        assertThat(refreshed.latestVersion()).isEqualTo("10.2");
        assertThat(service.getVersionInfo()).isSameAs(refreshed);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "null", "[]", "server-error" })
    void missingReleasePayloadAndTransportErrorsReturnOnlyCurrentVersion(String payload) {
        RestTemplate rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        var expectation = server.expect(requestTo(RELEASES_URL));
        if (payload.equals("server-error")) {
            expectation.andRespond(withServerError());
        }
        else {
            expectation.andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        }
        var cacheManager = org.mockito.Mockito.mock(org.springframework.cache.CacheManager.class);
        var result = service(rest, cacheManager, "10.0").refreshVersionInfo();
        assertThat(result.currentVersion()).isEqualTo("10.0");
        assertThat(result.latestVersion()).isNull();
        assertThat(result.releaseNotes()).isNull();
        assertThat(result.releaseUrl()).isNull();
        assertThat(result.updateAvailable()).isFalse();
        server.verify();
    }

    private ArtemisVersionService service(RestTemplate rest, org.springframework.cache.CacheManager cache, String current) {
        var service = new ArtemisVersionService(rest, cache);
        ReflectionTestUtils.setField(service, "currentVersion", current);
        return service;
    }

    private Map<String, Object> release(String version, String notes) {
        Map<String, Object> release = new HashMap<>();
        release.put("tag_name", version);
        release.put("body", notes);
        release.put("html_url", "https://release.invalid/" + version);
        release.put("draft", false);
        release.put("prerelease", false);
        return release;
    }

    @SafeVarargs
    private String releases(Map<String, Object>... releases) {
        return JsonObjectMapper.get().writeValueAsString(Arrays.asList(releases));
    }
}

package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for the comparison of a received base URL with the one of the connected Sharing Platform.
 */
class SharingConnectorServiceBaseUrlTest {

    private static final String CONFIGURED = "https://sharing.example.org/pluginIF/v0.1";

    private SharingConnectorService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new SharingConnectorService(mock(RestTemplate.class), mock(TaskScheduler.class));
        service.setSharingApiBaseUrl(URI.create(CONFIGURED).toURL());
    }

    @Test
    void withoutConfiguredUrlEveryValueIsLeftToTheChecksum() {
        var unconnected = new SharingConnectorService(mock(RestTemplate.class), mock(TaskScheduler.class));

        assertThat(unconnected.matchesConfiguredApiBaseUrl("https://anything.example/api")).isTrue();
        assertThat(unconnected.matchesConfiguredApiBaseUrl("not a url")).isTrue();
        assertThat(unconnected.matchesConfiguredApiBaseUrl(null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { CONFIGURED, CONFIGURED + "/", CONFIGURED + "///", "HTTPS://SHARING.EXAMPLE.ORG/pluginIF/v0.1", "https://sharing.example.org:443/pluginIF/v0.1",
            "https://user:secret@sharing.example.org/pluginIF/v0.1", CONFIGURED + "?token=abc", CONFIGURED + "#fragment" })
    void sameServerAndPathIsAccepted(String received) {
        assertThat(service.matchesConfiguredApiBaseUrl(received)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://other.example.org/pluginIF/v0.1", "https://sharing.example.org.evil.example/pluginIF/v0.1", "https://sharing.example.org:8443/pluginIF/v0.1",
            "http://sharing.example.org/pluginIF/v0.1", "https://sharing.example.org/pluginIF/v0.2", "https://sharing.example.org/pluginIF", "https://sharing.example.org/",
            "https://sharing.example.org/other/pluginIF/v0.1" })
    void differentServerPortSchemeOrPathIsRefused(String received) {
        assertThat(service.matchesConfiguredApiBaseUrl(received)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "not a url", "/pluginIF/v0.1", "pluginIF/v0.1", "https://", "unknown-scheme://sharing.example.org/pluginIF/v0.1" })
    void malformedOrRelativeValuesAreRefused(String received) {
        assertThat(service.matchesConfiguredApiBaseUrl(received)).isFalse();
    }

    @Test
    void missingValueIsRefusedOnceAUrlIsConfigured() {
        assertThat(service.matchesConfiguredApiBaseUrl(null)).isFalse();
    }

    @Test
    void ipv6LiteralHostIsComparedLikeAnyOtherHost() throws Exception {
        service.setSharingApiBaseUrl(URI.create("http://[2001:db8::1]:8080/pluginIF").toURL());

        assertThat(service.matchesConfiguredApiBaseUrl("http://[2001:db8::1]:8080/pluginIF")).isTrue();
        assertThat(service.matchesConfiguredApiBaseUrl("http://[2001:DB8::1]:8080/pluginIF/")).isTrue();
        assertThat(service.matchesConfiguredApiBaseUrl("http://[2001:db8::2]:8080/pluginIF")).isFalse();
        assertThat(service.matchesConfiguredApiBaseUrl("http://[2001:db8::1]:8081/pluginIF")).isFalse();
    }

    @Test
    void implicitDefaultPortMatchesTheExplicitOne() throws Exception {
        service.setSharingApiBaseUrl(URI.create("http://sharing.example.org:80/pluginIF").toURL());

        assertThat(service.matchesConfiguredApiBaseUrl("http://sharing.example.org/pluginIF")).isTrue();
        assertThat(service.matchesConfiguredApiBaseUrl("https://sharing.example.org/pluginIF")).isFalse();
    }
}

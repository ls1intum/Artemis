package de.tum.cit.aet.artemis.videosource.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GocastConfigurationTest {

    @Test
    void acceptsHttpsAndExplicitLocalHttpBaseUrls() {
        assertThat(GocastConfiguration.validateBaseUrl("https://live.example.edu/api/v2", "URL")).hasScheme("https").hasHost("live.example.edu");
        assertThat(GocastConfiguration.validateBaseUrl("http://gocast.localhost:8081/api/v2", "URL")).hasScheme("http").hasHost("gocast.localhost");
        assertThat(GocastConfiguration.validateBaseUrl("http://service.orb.local/api/v2", "URL")).hasHost("service.orb.local");
    }

    @Test
    void rejectsRemoteHttpCredentialsQueriesFragmentsAndEncodedPaths() {
        assertInvalid("http://live.example.edu/api/v2");
        assertInvalid("https://user:password@live.example.edu/api/v2");
        assertInvalid("https://live.example.edu/api/v2?token=secret");
        assertInvalid("https://live.example.edu/api/v2#fragment");
        assertInvalid("https://live.example.edu/api/%2e%2e/v2");
        assertInvalid("/api/v2");
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> GocastConfiguration.validateBaseUrl(value, "URL")).isInstanceOf(IllegalArgumentException.class);
    }
}

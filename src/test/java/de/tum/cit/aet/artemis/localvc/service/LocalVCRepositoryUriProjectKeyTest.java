package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;

/**
 * The project key of a repository URI consists of ASCII letters and digits, like the short names it is built from.
 */
class LocalVCRepositoryUriProjectKeyTest {

    @Test
    void shouldParseAUriWithAnAlphanumericProjectKey() {
        var uri = new LocalVCRepositoryUri("https://artemis.example.com/git/ABC123/abc123-exercise.git");

        assertThat(uri.getProjectKey()).isEqualTo("ABC123");
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://artemis.example.com/git/./.-x.git", "https://artemis.example.com/git/../..-x.git", "https://artemis.example.com/git/ÄBC/äbc-x.git",
            "https://artemis.example.com/git/AB-C/ab-c-x.git" })
    void shouldRejectAProjectKeyThatIsNotAlphanumeric(String uriString) {
        assertThatExceptionOfType(LocalVCInternalException.class).as("the URI '%s' must be rejected", uriString).isThrownBy(() -> new LocalVCRepositoryUri(uriString));
    }
}

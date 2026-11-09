package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.UsernameAndPassword;

class LocalVCCredentialsTest {

    private static String encoded(String credentials) {
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Basic", "basic", "BASIC" })
    void theNameOfTheSchemeIsCaseInsensitive(String scheme) throws Exception {
        UsernameAndPassword credentials = LocalVCCredentials.extractUsernameAndPassword(scheme + " " + encoded("student:secret"));

        assertThat(credentials.username()).isEqualTo("student");
        assertThat(credentials.password()).isEqualTo("secret");
    }

    @Test
    void theCredentialsAreDecodedAsUtf8() throws Exception {
        UsernameAndPassword credentials = LocalVCCredentials.extractUsernameAndPassword("Basic " + encoded("student:pässword"));

        assertThat(credentials.password()).isEqualTo("pässword");
    }

    @Test
    void severalSpacesMaySeparateTheSchemeFromTheCredentials() throws Exception {
        UsernameAndPassword credentials = LocalVCCredentials.extractUsernameAndPassword("Basic   " + encoded("student:secret"));

        assertThat(credentials.username()).isEqualTo("student");
        assertThat(credentials.password()).isEqualTo("secret");
    }

    @Test
    void aHeaderWithTokensAfterTheCredentialsIsRejected() {
        assertThatExceptionOfType(LocalVCAuthException.class)
                .isThrownBy(() -> LocalVCCredentials.extractUsernameAndPassword("Basic " + encoded("student:secret") + " " + encoded("other:credentials")));
    }

    @Test
    void aPasswordMayContainAColon() throws Exception {
        UsernameAndPassword credentials = LocalVCCredentials.extractUsernameAndPassword("Basic " + encoded("student:pass:word"));

        assertThat(credentials.username()).isEqualTo("student");
        assertThat(credentials.password()).isEqualTo("pass:word");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Bearer abc", "Basic", "Basic !!!not-base64!!!" })
    void aHeaderThatIsNoBasicHeaderIsRejected(String header) {
        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> LocalVCCredentials.extractUsernameAndPassword(header));
    }

    @Test
    void credentialsWithoutAColonAreRejected() {
        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> LocalVCCredentials.extractUsernameAndPassword("Basic " + encoded("student")));
    }

    @Test
    void aMissingHeaderIsRejected() {
        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> LocalVCCredentials.extractUsernameAndPassword(null));
    }

    @Test
    void aSecretIsOnlyEqualToItself() {
        assertThat(LocalVCCredentials.secretMatches("secret", "secret")).isTrue();
        assertThat(LocalVCCredentials.secretMatches("secret", "Secret")).isFalse();
        assertThat(LocalVCCredentials.secretMatches(null, "secret")).isFalse();
    }
}

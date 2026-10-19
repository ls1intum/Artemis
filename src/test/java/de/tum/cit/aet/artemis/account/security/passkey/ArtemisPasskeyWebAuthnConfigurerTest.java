package de.tum.cit.aet.artemis.account.security.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.service.AndroidFingerprintService;

class ArtemisPasskeyWebAuthnConfigurerTest {

    private ArtemisPasskeyWebAuthnConfigurer configurer;

    private AndroidFingerprintService androidFingerprintService;

    @BeforeEach
    void setUp() {
        configurer = new ArtemisPasskeyWebAuthnConfigurer(null, null, null, null, null, null, null, null, mock(AndroidFingerprintService.class), null, null, null, null, null);
        androidFingerprintService = mock(AndroidFingerprintService.class);
        when(androidFingerprintService.getFingerprints()).thenReturn(List.of());
        ReflectionTestUtils.setField(configurer, "androidFingerprintService", androidFingerprintService);
        ReflectionTestUtils.setField(configurer, "passkeyEnabled", true);
        ReflectionTestUtils.setField(configurer, "additionalAllowedOrigins", List.of());
        ReflectionTestUtils.setField(configurer, "relyingPartyIdOverride", "");
    }

    @Test
    void testServerUrlWithoutPort_shouldAddOriginWithClientPort() {
        ReflectionTestUtils.setField(configurer, "serverUrl", "https://artemis.example.com");
        ReflectionTestUtils.setField(configurer, "port", "9000");

        configurer.validatePasskeyAllowedOriginConfiguration();

        Set<String> allowedOrigins = (Set<String>) ReflectionTestUtils.getField(configurer, "allowedOrigins");
        assertThat(allowedOrigins).containsExactlyInAnyOrder("https://artemis.example.com", "https://artemis.example.com:9000");
    }

    @Test
    void testServerUrlWithPort_shouldNotDuplicatePort() {
        ReflectionTestUtils.setField(configurer, "serverUrl", "https://artemis.example.com:8443");
        ReflectionTestUtils.setField(configurer, "port", "9000");

        configurer.validatePasskeyAllowedOriginConfiguration();

        Set<String> allowedOrigins = (Set<String>) ReflectionTestUtils.getField(configurer, "allowedOrigins");
        assertThat(allowedOrigins).containsExactly("https://artemis.example.com:8443");
    }

    @Test
    void testRelyingPartyIdOverride_shouldUseOverrideValue() {
        ReflectionTestUtils.setField(configurer, "serverUrl", "https://artemis.example.com");
        ReflectionTestUtils.setField(configurer, "port", "9000");
        ReflectionTestUtils.setField(configurer, "relyingPartyIdOverride", "custom-rp.example.com");

        configurer.validatePasskeyAllowedOriginConfiguration();

        String relyingPartyId = (String) ReflectionTestUtils.getField(configurer, "relyingPartyId");
        assertThat(relyingPartyId).isEqualTo("custom-rp.example.com");
    }

    @Test
    void testRelyingPartyIdDefault_shouldUseHostFromServerUrl() {
        ReflectionTestUtils.setField(configurer, "serverUrl", "https://artemis.example.com");
        ReflectionTestUtils.setField(configurer, "port", "9000");

        configurer.validatePasskeyAllowedOriginConfiguration();

        String relyingPartyId = (String) ReflectionTestUtils.getField(configurer, "relyingPartyId");
        assertThat(relyingPartyId).isEqualTo("artemis.example.com");
    }

    @Test
    void testAdditionalAllowedOrigins_shouldBeIncluded() {
        ReflectionTestUtils.setField(configurer, "serverUrl", "https://artemis.example.com");
        ReflectionTestUtils.setField(configurer, "port", "9000");
        ReflectionTestUtils.setField(configurer, "additionalAllowedOrigins", List.of("https://proxy.example.com"));

        configurer.validatePasskeyAllowedOriginConfiguration();

        Set<String> allowedOrigins = (Set<String>) ReflectionTestUtils.getField(configurer, "allowedOrigins");
        assertThat(allowedOrigins).contains("https://proxy.example.com");
    }

    @Test
    void testPasskeyDisabled_shouldSkipValidation() {
        ReflectionTestUtils.setField(configurer, "passkeyEnabled", false);
        // serverUrl is null — would throw if validation ran
        ReflectionTestUtils.setField(configurer, "serverUrl", null);

        assertThatCode(() -> configurer.validatePasskeyAllowedOriginConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void testInvalidServerUrl_shouldThrowIllegalStateException() {
        ReflectionTestUtils.setField(configurer, "serverUrl", "not a valid url");
        ReflectionTestUtils.setField(configurer, "port", "9000");

        assertThatThrownBy(() -> configurer.validatePasskeyAllowedOriginConfiguration()).isInstanceOf(IllegalStateException.class);
    }
}

package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CorsFilter;

import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.passkey.ArtemisPasskeyWebAuthnConfigurer;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;

class SecurityConfigurationTest {

    private SecurityConfiguration securityConfiguration;

    private ModuleFeatureService moduleFeatureService;

    @BeforeEach
    void setUp() {
        // Mock all dependencies
        CorsFilter corsFilter = mock(CorsFilter.class);
        Optional<CustomLti13Configurer> customLti13Configurer = Optional.empty();
        Optional<ArtemisPasskeyWebAuthnConfigurer> passkeyWebAuthnConfigurer = Optional.empty();
        PasswordService passwordService = mock(PasswordService.class);
        ProfileService profileService = mock(ProfileService.class);
        TokenProvider tokenProvider = mock(TokenProvider.class);
        JWTCookieService jwtCookieService = mock(JWTCookieService.class);
        moduleFeatureService = mock(ModuleFeatureService.class);

        securityConfiguration = new SecurityConfiguration(corsFilter, customLti13Configurer, passkeyWebAuthnConfigurer, passwordService, profileService, tokenProvider,
                jwtCookieService, moduleFeatureService);
    }

    @Test
    void testValidatePasskeyConfiguration_whenPasskeyDisabled_shouldNotThrow() {
        // Given: Passkey is disabled
        when(moduleFeatureService.isPasskeyEnabled()).thenReturn(false);

        // Set token validity to 0 (invalid)
        ReflectionTestUtils.setField(securityConfiguration, "tokenValidityInSecondsForPasskey", 0L);

        // Then: Validation should not throw exception since passkey is disabled
        assertThatCode(() -> securityConfiguration.validatePasskeyAllowedOriginConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void testValidatePasskeyConfiguration_whenPasskeyEnabledWithValidTokenValidity_shouldNotThrow() {
        // Given: Passkey is enabled with valid token validity
        when(moduleFeatureService.isPasskeyEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(securityConfiguration, "tokenValidityInSecondsForPasskey", 15552000L);

        // Then: Validation should not throw exception
        assertThatCode(() -> securityConfiguration.validatePasskeyAllowedOriginConfiguration()).doesNotThrowAnyException();
    }

    @Test
    void testValidatePasskeyConfiguration_whenPasskeyEnabledWithZeroTokenValidity_shouldThrow() {
        // Given: Passkey is enabled with zero token validity
        when(moduleFeatureService.isPasskeyEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(securityConfiguration, "tokenValidityInSecondsForPasskey", 0L);

        // Then: Validation should throw IllegalStateException
        assertThatThrownBy(() -> securityConfiguration.validatePasskeyAllowedOriginConfiguration()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Token validity in seconds for passkey must be greater than 0");
    }

    @Test
    void testValidatePasskeyConfiguration_whenPasskeyEnabledWithNegativeTokenValidity_shouldThrow() {
        // Given: Passkey is enabled with negative token validity
        when(moduleFeatureService.isPasskeyEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(securityConfiguration, "tokenValidityInSecondsForPasskey", -100L);

        // Then: Validation should throw IllegalStateException
        assertThatThrownBy(() -> securityConfiguration.validatePasskeyAllowedOriginConfiguration()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Token validity in seconds for passkey must be greater than 0");
    }
}

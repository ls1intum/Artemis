package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CorsFilter;

import de.tum.cit.aet.artemis.account.security.passkey.ArtemisPasskeyWebAuthnConfigurer;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.core.service.PasskeyTokenRenewalService;
import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;

class SecurityConfigurationTest {

    /** The shipped default of artemis.user-management.max-session-lifetime-in-seconds, thirty days. */
    private static final long DEFAULT_MAX_SESSION_LIFETIME_IN_SECONDS = 2_592_000L;

    private SecurityConfiguration securityConfiguration;

    private ModuleFeatureService moduleFeatureService;

    @BeforeEach
    void setUp() {
        // Mock all dependencies
        CorsFilter corsFilter = mock(CorsFilter.class);
        Optional<CustomLti13Configurer> customLti13Configurer = Optional.empty();
        Optional<ArtemisPasskeyWebAuthnConfigurer> passkeyWebAuthnConfigurer = Optional.empty();
        PasswordService passwordService = mock(PasswordService.class);
        TokenProvider tokenProvider = mock(TokenProvider.class);
        JWTCookieService jwtCookieService = mock(JWTCookieService.class);
        moduleFeatureService = mock(ModuleFeatureService.class);

        securityConfiguration = createSecurityConfiguration(DEFAULT_MAX_SESSION_LIFETIME_IN_SECONDS);
    }

    /**
     * Builds a configuration with the given session lifetime, everything else mocked.
     *
     * @param maxSessionLifetimeInSeconds the value under test
     * @return the configuration, if the constructor accepts the lifetime
     */
    private SecurityConfiguration createSecurityConfiguration(long maxSessionLifetimeInSeconds) {
        // Never resolved here: these tests only exercise the session lifetime validation in the constructor.
        ObjectProvider<ElevatedAccessService> elevatedAccessService = mock();
        return new SecurityConfiguration(mock(CorsFilter.class), Optional.empty(), Optional.empty(), mock(PasswordService.class), mock(TokenProvider.class),
                mock(JWTCookieService.class), mock(PasskeyTokenRenewalService.class), moduleFeatureService, elevatedAccessService, maxSessionLifetimeInSeconds);
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

    @Test
    void testCspPolicyDirectives_scriptSrc_shouldAllowYouTubeIFrameApiOrigin() {
        // The YouTube IFrame API is loaded from https://www.youtube.com.
        // The CSP script-src directive must explicitly allow this origin so the browser
        // does not block the IFrame API script tag on lecture-unit pages.
        assertThat(SecurityConfiguration.CSP_POLICY_DIRECTIVES).contains("script-src").contains("https://www.youtube.com");
    }

    /**
     * JWTFilter converts this ceiling with {@code Math.multiplyExact(sessionCeilingInSeconds, 1000)} while rotating a
     * remember-me token. A value past the millisecond range would overflow there and throw on every renewal, so a
     * configuration mistake would surface as a request-time failure for the affected users rather than at startup.
     *
     * @param unusableLifetime a lifetime that cannot be converted to milliseconds
     */
    @ParameterizedTest
    @ValueSource(longs = { 0L, -1L, Long.MAX_VALUE, Long.MAX_VALUE / 1000 + 1 })
    void testUnusableMaxSessionLifetimeIsRejectedAtStartup(long unusableLifetime) {
        assertThatThrownBy(() -> createSecurityConfiguration(unusableLifetime)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("artemis.user-management.max-session-lifetime-in-seconds");
    }

    @ParameterizedTest
    @ValueSource(longs = { 1L, 2_592_000L, Long.MAX_VALUE / 1000 })
    void testUsableMaxSessionLifetimeIsAccepted(long usableLifetime) {
        assertThatCode(() -> createSecurityConfiguration(usableLifetime)).doesNotThrowAnyException();
    }
}

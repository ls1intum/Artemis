package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.dto.LoginOptionsDTO;
import de.tum.cit.aet.artemis.account.repository.UserRepository;

/**
 * Unit tests for LoginOptionsService.
 * Verifies that the service correctly determines login options from the local account state alone.
 */
@ExtendWith(MockitoExtension.class)
class LoginOptionsServiceTest {

    @Mock
    private UserRepository userRepository;

    private LoginOptionsService loginOptionsService;

    private static final String OIDC_LABEL = "TUM Login (OIDC)";

    private static final String SAML_LABEL = "TUM Login (SAML)";

    /**
     * Initializes the service under test with mock dependencies and sets @Value configuration fields.
     */
    @BeforeEach
    void setUp() {
        loginOptionsService = new LoginOptionsService(userRepository);

        ReflectionTestUtils.setField(loginOptionsService, "oidcEnabled", true);
        ReflectionTestUtils.setField(loginOptionsService, "samlEnabled", false);
        ReflectionTestUtils.setField(loginOptionsService, "oidcDisplayName", OIDC_LABEL);
        ReflectionTestUtils.setField(loginOptionsService, "samlDisplayName", SAML_LABEL);
    }

    /**
     * Verifies that entering null, empty, or blank identifiers immediately returns the PASSWORD fallback.
     *
     * @param input the blank or invalid identifier
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    void testGetLoginOptions_NullOrBlankInput_ReturnsPassword(String input) {
        LoginOptionsDTO result = loginOptionsService.getLoginOptions(input);
        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
    }

    /**
     * Verifies that if an internal user is found in the DB by login, we request their password.
     */
    @Test
    void testGetLoginOptions_InternalUserInDb_ByLogin_ReturnsPassword() {
        String login = "internal_user";
        when(userRepository.isInternalUserByLogin(login)).thenReturn(Optional.of(true));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
        verify(userRepository).isInternalUserByLogin(login);
    }

    /**
     * Verifies that if an internal user is found in the DB by email, we request their password.
     */
    @Test
    void testGetLoginOptions_InternalUserInDb_ByEmail_ReturnsPassword() {
        String email = "internal_user@artemis.local";
        when(userRepository.isInternalUserByEmailIgnoreCase(email)).thenReturn(Optional.of(true));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(email);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
        verify(userRepository).isInternalUserByEmailIgnoreCase(email);
    }

    /**
     * Verifies that if an external user exists and OIDC is enabled, we offer the OIDC option.
     */
    @Test
    void testGetLoginOptions_ExternalUserInDb_OidcEnabled_ReturnsOidc() {
        String login = "external_user";
        when(userRepository.isInternalUserByLogin(login)).thenReturn(Optional.of(false));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.OIDC);
        assertThat(result.idpName()).isEqualTo(OIDC_LABEL);
    }

    /**
     * Verifies that if an external user exists, OIDC is disabled, and SAML is enabled, we offer SAML2.
     */
    @Test
    void testGetLoginOptions_ExternalUserInDb_SamlEnabled_ReturnsSaml() {
        ReflectionTestUtils.setField(loginOptionsService, "oidcEnabled", false);
        ReflectionTestUtils.setField(loginOptionsService, "samlEnabled", true);

        String login = "external_user";
        when(userRepository.isInternalUserByLogin(login)).thenReturn(Optional.of(false));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.SAML2);
        assertThat(result.idpName()).isEqualTo(SAML_LABEL);
    }

    /**
     * Verifies that if external identity providers are disabled, we fall back to local password.
     */
    @Test
    void testGetLoginOptions_ExternalUserInDb_BothDisabled_ReturnsPassword() {
        ReflectionTestUtils.setField(loginOptionsService, "oidcEnabled", false);
        ReflectionTestUtils.setField(loginOptionsService, "samlEnabled", false);

        String login = "external_user";
        when(userRepository.isInternalUserByLogin(login)).thenReturn(Optional.of(false));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
    }

    /**
     * Verifies that an identifier this instance has never seen is routed to the external provider, which is where a
     * first-time user gets provisioned.
     */
    @Test
    void testGetLoginOptions_UserNotInDb_ByLogin_ReturnsOidc() {
        String login = "new_student";
        when(userRepository.isInternalUserByLogin(login)).thenReturn(Optional.empty());

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.OIDC);
        assertThat(result.idpName()).isEqualTo(OIDC_LABEL);
        verify(userRepository).isInternalUserByLogin(login);
    }

    /**
     * Same as above for an email identifier.
     */
    @Test
    void testGetLoginOptions_UserNotInDb_ByEmail_ReturnsOidc() {
        String email = "new_student@tum.de";
        when(userRepository.isInternalUserByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(email);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.OIDC);
        assertThat(result.idpName()).isEqualTo(OIDC_LABEL);
        verify(userRepository).isInternalUserByEmailIgnoreCase(email);
    }

    /**
     * The response must not tell an unauthenticated caller whether an identifier is known to this instance. An account
     * that exists as an externally managed user and an identifier that exists nowhere therefore answer identically, so
     * the endpoint cannot be used to sort identifiers into "known" and "unknown".
     */
    @Test
    void testGetLoginOptions_UnknownIdentifierIsIndistinguishableFromExternalUser() {
        when(userRepository.isInternalUserByLogin("known_external_user")).thenReturn(Optional.of(false));
        when(userRepository.isInternalUserByLogin("identifier_that_exists_nowhere")).thenReturn(Optional.empty());

        LoginOptionsDTO known = loginOptionsService.getLoginOptions("known_external_user");
        LoginOptionsDTO unknown = loginOptionsService.getLoginOptions("identifier_that_exists_nowhere");

        assertThat(unknown).isEqualTo(known);
    }

    /**
     * Verifies that with no external provider configured, an unknown identifier falls back to PASSWORD.
     */
    @Test
    void testGetLoginOptions_UserNotInDb_SSODisabled_ReturnsPassword() {
        ReflectionTestUtils.setField(loginOptionsService, "oidcEnabled", false);
        ReflectionTestUtils.setField(loginOptionsService, "samlEnabled", false);

        String login = "new_student";
        when(userRepository.isInternalUserByLogin(login)).thenReturn(Optional.empty());

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
    }

    /**
     * Verifies that if both oidc and saml2 profiles enables, the oidc option is provided
     */
    @Test
    void testGetLoginOptions_whenBothOidcAndSaml2Enabled_prefersOidc() {
        ReflectionTestUtils.setField(loginOptionsService, "oidcEnabled", true);
        ReflectionTestUtils.setField(loginOptionsService, "samlEnabled", true);

        when(userRepository.isInternalUserByLogin(anyString())).thenReturn(Optional.of(false));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions("externalUser");

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.OIDC);
        assertThat(result.idpName()).isEqualTo(OIDC_LABEL);
    }
}

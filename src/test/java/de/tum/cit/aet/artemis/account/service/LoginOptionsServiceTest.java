package de.tum.cit.aet.artemis.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.LoginOptionsDTO;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserService;

/**
 * Unit tests for LoginOptionsService.
 * Verifies that the service correctly determines login options based on DB status and LDAP fallback.
 */
@ExtendWith(MockitoExtension.class)
class LoginOptionsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LdapUserService ldapUserService;

    private LoginOptionsService loginOptionsService;

    private static final String OIDC_LABEL = "TUM Login (OIDC)";

    private static final String SAML_LABEL = "TUM Login (SAML)";

    /**
     * Initializes the service under test with mock dependencies and sets @Value configuration fields.
     */
    @BeforeEach
    void setUp() {
        loginOptionsService = new LoginOptionsService(userRepository, Optional.of(ldapUserService));

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
        User mockUser = mock(User.class);
        when(mockUser.isInternal()).thenReturn(true);
        when(userRepository.findOneByLogin(login)).thenReturn(Optional.of(mockUser));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
        verify(userRepository).findOneByLogin(login);
        verifyNoInteractions(ldapUserService);
    }

    /**
     * Verifies that if an internal user is found in the DB by email, we request their password.
     */
    @Test
    void testGetLoginOptions_InternalUserInDb_ByEmail_ReturnsPassword() {
        String email = "internal_user@artemis.local";
        User mockUser = mock(User.class);
        when(mockUser.isInternal()).thenReturn(true);
        when(userRepository.findOneByEmailIgnoreCase(email)).thenReturn(Optional.of(mockUser));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(email);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
        verify(userRepository).findOneByEmailIgnoreCase(email);
        verifyNoInteractions(ldapUserService);
    }

    /**
     * Verifies that if an external user exists and OIDC is enabled, we offer the OIDC option.
     */
    @Test
    void testGetLoginOptions_ExternalUserInDb_OidcEnabled_ReturnsOidc() {
        String login = "external_user";
        User mockUser = mock(User.class);
        when(mockUser.isInternal()).thenReturn(false);
        when(userRepository.findOneByLogin(login)).thenReturn(Optional.of(mockUser));

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
        User mockUser = mock(User.class);
        when(mockUser.isInternal()).thenReturn(false);
        when(userRepository.findOneByLogin(login)).thenReturn(Optional.of(mockUser));

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
        User mockUser = mock(User.class);
        when(mockUser.isInternal()).thenReturn(false);
        when(userRepository.findOneByLogin(login)).thenReturn(Optional.of(mockUser));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
    }

    /**
     * Verifies that a new user (not in DB) who is found in LDAP by login is directed to OIDC.
     */
    @Test
    void testGetLoginOptions_UserNotInDb_FoundInLdap_ByLogin_ReturnsOidc() {
        String login = "new_student";
        when(userRepository.findOneByLogin(login)).thenReturn(Optional.empty());

        LdapUserDto mockLdapUser = mock(LdapUserDto.class);
        when(ldapUserService.findByLogin(login)).thenReturn(Optional.of(mockLdapUser));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.OIDC);
        assertThat(result.idpName()).isEqualTo(OIDC_LABEL);
        verify(userRepository).findOneByLogin(login);
        verify(ldapUserService).findByLogin(login);
    }

    /**
     * Verifies that a new user (not in DB) who is found in LDAP by email is directed to OIDC.
     */
    @Test
    void testGetLoginOptions_UserNotInDb_FoundInLdap_ByEmail_ReturnsOidc() {
        String email = "new_student@tum.de";
        when(userRepository.findOneByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        LdapUserDto mockLdapUser = mock(LdapUserDto.class);
        when(ldapUserService.findByAnyEmail(email)).thenReturn(Optional.of(mockLdapUser));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(email);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.OIDC);
        assertThat(result.idpName()).isEqualTo(OIDC_LABEL);
        verify(userRepository).findOneByEmailIgnoreCase(email);
        verify(ldapUserService).findByAnyEmail(email);
    }

    /**
     * Verifies that a user not found in the local DB and not found in LDAP falls back to PASSWORD.
     */
    @Test
    void testGetLoginOptions_UserNotInDb_AndNotInLdap_ReturnsPassword() {
        String login = "unknown_user";
        when(userRepository.findOneByLogin(login)).thenReturn(Optional.empty());
        when(ldapUserService.findByLogin(login)).thenReturn(Optional.empty());

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
    }

    /**
     * Verifies that if LDAP service is completely missing/disabled, we fall back to PASSWORD.
     */
    @Test
    void testGetLoginOptions_UserNotInDb_LdapServiceMissing_ReturnsPassword() {
        loginOptionsService = new LoginOptionsService(userRepository, Optional.empty());
        ReflectionTestUtils.setField(loginOptionsService, "oidcEnabled", true);
        ReflectionTestUtils.setField(loginOptionsService, "oidcDisplayName", OIDC_LABEL);

        String login = "new_student";
        when(userRepository.findOneByLogin(login)).thenReturn(Optional.empty());

        LoginOptionsDTO result = loginOptionsService.getLoginOptions(login);

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.PASSWORD);
        assertThat(result.idpName()).isNull();
        verifyNoInteractions(ldapUserService);
    }

    /**
     * Verifies that if both oidc and saml2 profiles enables, the oidc option is provided
     */
    @Test
    void testGetLoginOptions_whenBothOidcAndSaml2Enabled_prefersOidc() {
        ReflectionTestUtils.setField(loginOptionsService, "oidcEnabled", true);
        ReflectionTestUtils.setField(loginOptionsService, "samlEnabled", true);

        User externalUser = new User();
        externalUser.setInternal(false);
        when(userRepository.findOneByLogin(anyString())).thenReturn(Optional.of(externalUser));

        LoginOptionsDTO result = loginOptionsService.getLoginOptions("externalUser");

        assertThat(result.loginMethod()).isEqualTo(LoginOptionsDTO.LoginMethod.OIDC);
        assertThat(result.idpName()).isEqualTo(OIDC_LABEL);
    }
}

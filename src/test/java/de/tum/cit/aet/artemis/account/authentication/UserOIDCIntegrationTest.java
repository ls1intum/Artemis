package de.tum.cit.aet.artemis.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.security.OIDCService;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Integrated business-logic tests for OIDC Authentication and JIT provisioning features.
 * Bypasses network filter restrictions by executing the OIDCService module directly.
 */
@ActiveProfiles(profiles = "oidc", inheritProfiles = true)
@TestPropertySource(properties = { "artemis.user-management.oidc.enabled=true", "artemis.user-management.oidc.mappings.username=preferred_username",
        "artemis.user-management.oidc.mappings.matriculation-number=matriculation_number", "artemis.user-management.oidc.mappings.first-name=given_name",
        "artemis.user-management.oidc.mappings.last-name=family_name", "artemis.user-management.oidc.mappings.email=email" })
class UserOIDCIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    private static final String STUDENT_NAME = "student_oidc_test";

    private static final String STUDENT_PASSWORD = "test1234";

    private static final String STUDENT_REGISTRATION_NUMBER = "12345678";

    @Autowired
    private OIDCService oidcService;

    @Autowired
    private PasswordService passwordService;

    @AfterEach
    void clearTestData() {
        userTestRepository.findOneByLogin(STUDENT_NAME).ifPresent(userTestRepository::delete);
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void testAuthenticationRedirect() throws Exception {
        final String redirectTarget = request.getRedirectTarget("/oauth2/authorization/oidc", HttpStatus.FOUND);
        assertThat(redirectTarget).contains("/idp/profile/oidc/authorize");
    }

    @Test
    void testValidOidcRegistration() {
        assertStudentNotExists();

        oidcService.loadUser(createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName")));

        assertStudentExists();
        assertRegistrationNumber(STUDENT_REGISTRATION_NUMBER);
    }

    @Test
    void testValidOidcLogin() {
        assertStudentNotExists();

        String identifyingEmail = STUDENT_NAME + "@other.domain.invalid";
        createUser(identifyingEmail);
        assertStudentExists();

        oidcService.loadUser(createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName")));

        assertStudentExists();
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getEmail()).as("Email synchronizes with identity provider").isEqualTo(STUDENT_NAME + "@artemis.local");
    }

    @Test
    void testOidcUpdateUserData() {
        assertStudentNotExists();

        String identifyingEmail = STUDENT_NAME + "@other.domain.invalid";
        createUser(identifyingEmail);
        assertStudentExists();

        oidcService.loadUser(createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName")));
        assertStudentExists();
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getFirstName()).isEqualTo("FirstName");
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getLastName()).isEqualTo("LastName");

        oidcService.loadUser(createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "NewFirstName", "NewLastName")));
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getFirstName()).isEqualTo("NewFirstName");
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getLastName()).isEqualTo("NewLastName");
    }

    @Test
    void testPasswordLoginAfterOidcRegistration() throws Exception {
        assertStudentNotExists();

        oidcService.loadUser(createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName")));
        assertStudentExists();

        User student = userTestRepository.findUserWithGroupsAndAuthoritiesByLogin(STUDENT_NAME).orElseThrow();
        student.setPassword(passwordService.hashPassword(STUDENT_PASSWORD));
        userTestRepository.saveAndFlush(student);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");

        request.postWithoutResponseBody("/api/core/public/authenticate", createLoginVM(), HttpStatus.OK, httpHeaders);
    }

    @Test
    void testInvalidAuthenticationOidcLogin() {
        assertStudentNotExists();

        Map<String, Object> brokenClaims = new HashMap<>();
        brokenClaims.put("sub", "1234567890");
        brokenClaims.put("email", "artemis_test_userr@artemis.local");

        OidcUserRequest brokenRequest = createMockUserRequest(brokenClaims);

        assertThatExceptionOfType(OAuth2AuthenticationException.class).isThrownBy(() -> oidcService.loadUser(brokenRequest));

        assertStudentNotExists();
    }

    private OidcUserRequest createMockUserRequest(Map<String, Object> claims) {
        ClientRegistration localRegistration = ClientRegistration.withRegistrationId("oidc").clientId("artemis-oidc-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").tokenUri("http://localhost/token")
                .authorizationUri("http://localhost/authorize").build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "mock-token", Instant.now(), Instant.now().plusSeconds(3600));

        OidcIdToken idToken = new OidcIdToken("mock-raw-id-token-string", Instant.now(), Instant.now().plusSeconds(3600), claims);
        return new OidcUserRequest(localRegistration, accessToken, idToken);
    }

    private Map<String, Object> createClaimsMap(String registrationNumber, String firstName, String lastName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "1234567890");
        claims.put("preferred_username", STUDENT_NAME);
        claims.put("given_name", firstName);
        claims.put("family_name", lastName);
        claims.put("email", STUDENT_NAME + "@artemis.local");
        claims.put("matriculation_number", registrationNumber);
        return claims;
    }

    private LoginVM createLoginVM() {
        LoginVM user = new LoginVM();
        user.setUsername(STUDENT_NAME);
        user.setPassword(STUDENT_PASSWORD);
        user.setRememberMe(true);
        return user;
    }

    private void createUser(String identifyingEmail) {
        User user = new User();
        user.setLogin(STUDENT_NAME);
        user.setActivated(true);
        user.setEmail(identifyingEmail);
        userTestRepository.save(user);
    }

    private void assertStudentNotExists() {
        assertThatIllegalArgumentException().isThrownBy(() -> userUtilService.getUserByLogin(STUDENT_NAME))
                .withMessage("Provided login " + STUDENT_NAME + " does not exist in database");
    }

    private void assertStudentExists() {
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME)).as("User shall exist").isNotNull();
    }

    private void assertRegistrationNumber(String registrationNumber) {
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getRegistrationNumber()).isEqualTo(registrationNumber);
    }
}

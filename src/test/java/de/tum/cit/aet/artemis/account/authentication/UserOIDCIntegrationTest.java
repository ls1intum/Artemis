package de.tum.cit.aet.artemis.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.security.OIDCAuthenticationFailureHandler;
import de.tum.cit.aet.artemis.account.security.OIDCAuthenticationSuccessHandler;
import de.tum.cit.aet.artemis.account.security.OIDCService;
import de.tum.cit.aet.artemis.account.service.ArtemisSuccessfulLoginService;
import de.tum.cit.aet.artemis.account.service.OIDCExchangeCodeService;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.core.exception.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Integrated business-logic tests for OIDC Authentication and JIT provisioning features.
 * Bypasses network filter restrictions by executing the OIDCService module directly.
 */
class UserOIDCIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    private static final String STUDENT_NAME = "student_oidc_test";

    private static final String STUDENT_PASSWORD = "test1234";

    private static final String STUDENT_REGISTRATION_NUMBER = "12345678";

    private static final String OTHER_STUDENT_NAME = "other_student_oidc_test";

    @Autowired
    private UserCreationService userCreationService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JWTCookieService jwtCookieService;

    @Autowired
    private ArtemisSuccessfulLoginService artemisSuccessfulLoginService;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private TokenProvider tokenProvider;

    private OIDCService oidcService;

    private OIDCExchangeCodeService oidcExchangeCodeService;

    private OIDCAuthenticationSuccessHandler successHandler;

    private OIDCAuthenticationFailureHandler failureHandler;

    private LdapUserService ldapUserServiceMock;

    @BeforeEach
    void initManualMocks() {
        ldapUserServiceMock = mock(LdapUserService.class);
        oidcExchangeCodeService = mock(OIDCExchangeCodeService.class);
        oidcService = new OIDCService(userTestRepository, userCreationService, Optional.of(ldapUserServiceMock));

        ReflectionTestUtils.setField(oidcService, "usernameClaimKey", "preferred_username");
        ReflectionTestUtils.setField(oidcService, "matriculationClaimKey", "matriculation_number");
        ReflectionTestUtils.setField(oidcService, "firstNameClaimKey", "given_name");
        ReflectionTestUtils.setField(oidcService, "lastNameClaimKey", "family_name");
        ReflectionTestUtils.setField(oidcService, "emailClaimKey", "email");

        successHandler = new OIDCAuthenticationSuccessHandler(jwtCookieService, userTestRepository, artemisSuccessfulLoginService, oidcExchangeCodeService, templateEngine);
        failureHandler = new OIDCAuthenticationFailureHandler(templateEngine);
        ReflectionTestUtils.setField(successHandler, "usernameClaimKey", "preferred_username");
    }

    @AfterEach
    void clearTestData() {
        userTestRepository.findOneByLogin(STUDENT_NAME).ifPresent(userTestRepository::delete);
        userTestRepository.findOneByLogin(OTHER_STUDENT_NAME).ifPresent(userTestRepository::delete);
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void testValidOidcRegistration() {
        assertStudentNotExists();

        oidcService.loadUser(createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName")));

        assertStudentExists();
        assertRegistrationNumber(STUDENT_REGISTRATION_NUMBER);
    }

    @Test
    void testOidcRegistrationWithLdapFallback() {
        assertStudentNotExists();

        // 1. IdP provided no registration number
        Map<String, Object> claimsWithoutMatriculation = createClaimsMap(null, "FirstName", "LastName");
        claimsWithoutMatriculation.remove("matriculation_number");

        // 2. User which will be returned from LdapService
        LdapUserDto mockLdapUser = new LdapUserDto().login(STUDENT_NAME).registrationNumber(STUDENT_REGISTRATION_NUMBER).firstName("FirstName").lastName("LastName")
                .email(STUDENT_NAME + "@artemis.local");

        when(ldapUserServiceMock.loadUserDetailsFromLdap(STUDENT_NAME)).thenReturn(mockLdapUser);

        // 3. Start authentication
        oidcService.loadUser(createMockUserRequest(claimsWithoutMatriculation));

        // 4. Verify that user is created and registration number is fetched from ldap
        assertStudentExists();
        assertRegistrationNumber(STUDENT_REGISTRATION_NUMBER);

        // 5. Verify ldapUserService was used only 1 time
        verify(ldapUserServiceMock, times(1)).loadUserDetailsFromLdap(STUDENT_NAME);
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
    void testRepeatedOidcLoginWithMixedCaseUsernameClaim() {
        assertStudentNotExists();
        Map<String, Object> mixedCaseClaims = createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName");
        mixedCaseClaims.put("preferred_username", STUDENT_NAME.toUpperCase(Locale.ENGLISH));

        // The claim is stored lowercase, so a second login has to find the account the first one created rather than
        // trying to create it again and failing on the email that is already taken.
        oidcService.loadUser(createMockUserRequest(mixedCaseClaims));
        assertStudentExists();

        // Before the login was canonicalized this second call created the account again and failed on the email that the
        // first one had already taken.
        assertThatCode(() -> oidcService.loadUser(createMockUserRequest(mixedCaseClaims))).doesNotThrowAnyException();

        assertStudentExists();
        assertThat(userTestRepository.findOneByLogin(STUDENT_NAME.toUpperCase(Locale.ENGLISH))).as("no account is stored under the uncanonicalized claim").isEmpty();
    }

    @Test
    void testOidcSuccessHandlerResolvesMixedCaseUsernameClaim() throws Exception {
        Map<String, Object> mixedCaseClaims = createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName");
        mixedCaseClaims.put("preferred_username", "Student_OIDC_Test");

        OidcUser oidcUser = oidcService.loadUser(createMockUserRequest(mixedCaseClaims));
        var authentication = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "oidc");

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNotNull();
    }

    @Test
    void testOidcRegistrationRejectsEmailUsedByAnotherAccount() {
        createOtherUser(STUDENT_NAME + "@artemis.local");

        assertThatExceptionOfType(EmailAlreadyUsedException.class)
                .isThrownBy(() -> oidcService.loadUser(createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName"))));

        assertStudentNotExists();
    }

    @Test
    void testOidcUpdateRejectsEmailUsedByAnotherAccount() {
        String originalEmail = STUDENT_NAME + "@other.domain.invalid";
        createUser(originalEmail);
        createOtherUser(STUDENT_NAME + "@artemis.local");

        assertThatExceptionOfType(EmailAlreadyUsedException.class)
                .isThrownBy(() -> oidcService.loadUser(createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName"))));

        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getEmail()).isEqualTo(originalEmail);
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

        User student = userTestRepository.findOneWithAuthoritiesByLogin(STUDENT_NAME).orElseThrow();
        student.setPassword(passwordService.hashPassword(STUDENT_PASSWORD));
        student.setInternal(true); // OIDC users are external by default; make internal for password auth test
        userTestRepository.saveAndFlush(student);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");

        request.postWithoutResponseBody("/api/core/public/authenticate", createLoginVM(), HttpStatus.OK, httpHeaders);
    }

    @Test
    void testOidcLogin_withRememberMeTrue_issuesLongTermCookie() throws Exception {
        assertStudentNotExists();
        createUser(STUDENT_NAME + "@artemis.local");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("OIDC_REMEMBER_ME", true);
        request.setSession(session);

        var mockUserRequest = createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName"));
        var oidcUser = oidcService.loadUser(mockUserRequest);
        var auth = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "oidc");

        successHandler.onAuthenticationSuccess(request, response, auth);

        String cookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookieHeader).isNotNull();
        assertThat(cookieHeader).contains("Max-Age=" + tokenProvider.getTokenValidity(true) / 1000);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void testOidcLogin_withRememberMeFalse_issuesShortTermCookie() throws Exception {
        assertStudentNotExists();
        createUser(STUDENT_NAME + "@artemis.local");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("OIDC_REMEMBER_ME", false);
        request.setSession(session);

        var mockUserRequest = createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName"));
        var oidcUser = oidcService.loadUser(mockUserRequest);
        var auth = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "oidc");

        successHandler.onAuthenticationSuccess(request, response, auth);

        String cookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookieHeader).isNotNull();
        assertThat(session.isInvalid()).isTrue();
        assertThat(cookieHeader).contains("Max-Age=" + tokenProvider.getTokenValidity(false) / 1000);
    }

    @Test
    void testOidcLogin_withRedirectVsCode_generatesExchangeCodeAndRedirectsToVsCode() throws Exception {
        assertStudentNotExists();
        createUser(STUDENT_NAME + "@artemis.local");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Valid 43-character S256 code challenge (RFC 7636)
        String expectedChallenge = "E9Melhoa2OwvFrGMTJguCH5A_lUhKw6YGzaWzkTCdcM";
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("OIDC_REDIRECT", "vscode");
        session.setAttribute("OIDC_CODE_CHALLENGE", expectedChallenge);
        request.setSession(session);

        String expectedCode = "mock-exchange-code-123";
        when(oidcExchangeCodeService.isValidCodeChallenge(expectedChallenge)).thenReturn(true);
        when(oidcExchangeCodeService.storeJwtAndGenerateCode(anyString(), anyString())).thenReturn(expectedCode);

        // Construct OidcUser directly to keep the handler test focused
        Map<String, Object> claims = createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName");
        OidcIdToken idToken = new OidcIdToken("mock-raw-id-token-string", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser oidcUser = new DefaultOidcUser(Set.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, "preferred_username");
        var auth = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "oidc");

        successHandler.onAuthenticationSuccess(request, response, auth);

        // 1. Verify redirect URL
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("vscode://aet-tum.iris-thaumantias/auth-callback?code=" + expectedCode);

        // 2. Verify exact JWT token and challenge passed to exchange-code service
        ArgumentCaptor<String> jwtCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> challengeCaptor = ArgumentCaptor.forClass(String.class);
        verify(oidcExchangeCodeService, times(1)).storeJwtAndGenerateCode(jwtCaptor.capture(), challengeCaptor.capture());
        String storedJwt = jwtCaptor.getValue();
        assertThat(storedJwt).isNotNull().isNotBlank();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains(storedJwt);
        assertThat(challengeCaptor.getValue()).isEqualTo(expectedChallenge);

        // 3. Verify session invalidation
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void testOidcLogin_withRedirectVsCode_missingCodeChallenge_redirectsWithInvalidRequestError() throws Exception {
        assertStudentNotExists();
        createUser(STUDENT_NAME + "@artemis.local");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("OIDC_REDIRECT", "vscode");
        // No OIDC_CODE_CHALLENGE set in session
        request.setSession(session);

        Map<String, Object> claims = createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName");
        OidcIdToken idToken = new OidcIdToken("mock-raw-id-token-string", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser oidcUser = new DefaultOidcUser(Set.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, "preferred_username");
        var auth = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "oidc");

        successHandler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("vscode://aet-tum.iris-thaumantias/auth-callback?error=invalid_request");
        assertThat(session.isInvalid()).isTrue();
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

    @Test
    void testOidcLogin_failsForDeactivatedUser() {
        assertStudentNotExists();
        createUser(STUDENT_NAME + "@artemis.local");

        // Deactivate user
        User student = userTestRepository.findOneWithAuthoritiesByLogin(STUDENT_NAME).orElseThrow();
        student.setActivated(false);
        userTestRepository.saveAndFlush(student);

        var mockUserRequest = createMockUserRequest(createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName"));

        assertThatExceptionOfType(OAuth2AuthenticationException.class).isThrownBy(() -> oidcService.loadUser(mockUserRequest))
                .matches(ex -> "user_deactivated".equals(ex.getError().getErrorCode()));
    }

    @Test
    void testOidcFailureHandler_withDeactivatedUserException_redirectsToDeactivatedError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(new OAuth2Error("user_deactivated"), "Deactivated");

        failureHandler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl()).isEqualTo("/sign-in?loginError=deactivated");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void testOidcFailureHandler_withGenericException_redirectsToGenericOidcError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(new OAuth2Error("invalid_issuer"), "Wrong Issuer");

        failureHandler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl()).isEqualTo("/sign-in?loginError=oidcFailure");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void testOidcFailureHandler_withRedirectVsCode_deactivatedUser_redirectsToVsCodeWithError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("OIDC_REDIRECT", "vscode");
        request.setSession(session);

        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(new OAuth2Error("user_deactivated"), "Deactivated");

        failureHandler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("vscode://aet-tum.iris-thaumantias/auth-callback?error=deactivated");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void testOidcFailureHandler_withRedirectVsCode_genericError_redirectsToVsCodeWithError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("OIDC_REDIRECT", "vscode");
        request.setSession(session);

        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(new OAuth2Error("invalid_issuer"), "Wrong Issuer");

        failureHandler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("vscode://aet-tum.iris-thaumantias/auth-callback?error=oidcFailure");
        assertThat(session.isInvalid()).isTrue();
    }

    private static final Instant FIXED_TIMESTAMP = Instant.parse("2026-08-15T00:00:00Z");

    private OidcUserRequest createMockUserRequest(Map<String, Object> claims) {
        ClientRegistration localRegistration = ClientRegistration.withRegistrationId("oidc").clientId("artemis-oidc-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").tokenUri("http://localhost/token")
                .authorizationUri("http://localhost/authorize").build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "mock-token", FIXED_TIMESTAMP, FIXED_TIMESTAMP.plusSeconds(3600));

        OidcIdToken idToken = new OidcIdToken("mock-raw-id-token-string", FIXED_TIMESTAMP, FIXED_TIMESTAMP.plusSeconds(3600), claims);
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

    private void createOtherUser(String email) {
        User user = new User();
        user.setLogin(OTHER_STUDENT_NAME);
        user.setActivated(true);
        user.setEmail(email);
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

    @Test
    void testOidcLogin_withRedirectVsCode_storeJwtFails_redirectsWithServerError() throws Exception {
        assertStudentNotExists();
        createUser(STUDENT_NAME + "@artemis.local");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String expectedChallenge = "E9Melhoa2OwvFrGMTJguCH5A_lUhKw6YGzaWzkTCdcM";
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("OIDC_REDIRECT", "vscode");
        session.setAttribute("OIDC_CODE_CHALLENGE", expectedChallenge);
        request.setSession(session);

        when(oidcExchangeCodeService.isValidCodeChallenge(expectedChallenge)).thenReturn(true);
        // Simulate failure in distributed cache store
        when(oidcExchangeCodeService.storeJwtAndGenerateCode(anyString(), anyString())).thenReturn(null);

        Map<String, Object> claims = createClaimsMap(STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName");
        OidcIdToken idToken = new OidcIdToken("mock-raw-id-token-string", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser oidcUser = new DefaultOidcUser(Set.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, "preferred_username");
        var auth = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "oidc");

        successHandler.onAuthenticationSuccess(request, response, auth);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("vscode://aet-tum.iris-thaumantias/auth-callback?error=server_error");
        assertThat(session.isInvalid()).isTrue();
    }
}

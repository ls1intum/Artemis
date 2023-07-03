package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationGitlabCIGitlabSamlTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.open.UserJwtResource;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

/**
 * Tests for {@link UserJwtResource} and {@link SAML2Service}.
 */
class UserSaml2IntegrationTest extends AbstractSpringIntegrationGitlabCIGitlabSamlTest {

    private static final String STUDENT_NAME = "student_saml_test";

    private static final String STUDENT_PASSWORD = "test1234";

    private static final String STUDENT_REGISTRATION_NUMBER = "12345678";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void setup() {
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
    }

    @AfterEach
    void clearExistingUser() {
        userRepository.findOneByLogin(STUDENT_NAME).ifPresent(userRepository::delete);
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void testAuthenticationRedirect() throws Exception {
        request.postWithoutResponseBody("/api/public/saml2", Boolean.FALSE, HttpStatus.UNAUTHORIZED);
        final String redirectTarget = request.getRedirectTarget("/saml2/authenticate", HttpStatus.FOUND);
        assertThat(redirectTarget).endsWith("/login");
    }

    /**
     * This test checks the creation of a new SAML2 authenticated user.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2Registration() throws Exception {
        assertStudentNotExists();

        authenticate(createPrincipal(STUDENT_REGISTRATION_NUMBER));

        assertStudentExists();
        assertRegistrationNumber(STUDENT_REGISTRATION_NUMBER);
    }

    /**
     * This test checks that a new SAMl2 user is created with the extracted registration number.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2RegistrationExtractingRegistrationNumber() throws Exception {
        assertStudentNotExists();

        authenticate(createPrincipal("somePrefix1234someSuffix"));

        assertStudentExists();
        assertRegistrationNumber("1234");
    }

    /**
     * This test checks that a new SAMl2 user is created with the complete attribute value even if no extraction was possible.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2RegistrationNonMatchingRegistrationNumberExtraction() throws Exception {
        assertStudentNotExists();

        authenticate(createPrincipal("nonMatchingRegNum"));

        assertStudentExists();
        assertRegistrationNumber("nonMatchingRegNum");
    }

    /**
     * This test checks that a new SAMl2 user is created with an empty registration number if the attribute is empty.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2RegistrationEmptyRegistrationNumber() throws Exception {
        assertStudentNotExists();

        authenticate(createPrincipal(""));

        assertStudentExists();
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getRegistrationNumber()).isNull();
    }

    /**
     * This test checks the successful login of an existing user via SAML2.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2Login() throws Exception {
        assertStudentNotExists();

        // Other mail than in #createPrincipal for identification of user
        String identifyingEmail = STUDENT_NAME + "@other.domain.invalid";

        // Create User
        createUser(identifyingEmail);
        assertStudentExists();

        authenticate(createPrincipal(STUDENT_REGISTRATION_NUMBER));

        assertStudentExists();
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getEmail()).as("Email identifies already created user").isEqualTo(identifyingEmail);
    }

    /**
     * This test checks the successful login of an existing user via username and password (after creation via SAML2).
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testPasswordLoginAfterShibbolethRegistration() throws Exception {
        assertStudentNotExists();

        // Create user
        mockSAMLAuthentication();
        request.postWithoutResponseBody("/api/public/saml2", Boolean.FALSE, HttpStatus.OK);
        assertStudentExists();

        // Change Password
        User student = userRepository.findUserWithGroupsAndAuthoritiesByLogin(STUDENT_NAME).orElseThrow();
        student.setPassword(passwordService.hashPassword(STUDENT_PASSWORD));
        userRepository.saveAndFlush(student);

        // Try to login ..
        TestSecurityContextHolder.clearContext();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        request.postWithoutResponseBody("/api/public/authenticate", createLoginVM(), HttpStatus.OK, httpHeaders);

        // Check SAML Login afterwards ..

        TestSecurityContextHolder.clearContext();
        // Mock existing SAML2 Auth
        mockSAMLAuthentication();
        // Test whether authorizeSAML2 generates a valid token
        request.postWithoutResponseBody("/api/public/saml2", Boolean.FALSE, HttpStatus.OK);
    }

    /**
     * This tests checks whether the access to the system is restricted if the login is not present.
     *
     * @throws Exception if something went wrong
     */
    @Test
    void testInvalidAuthenticationSaml2Login() throws Exception {
        assertStudentNotExists();
        // Test whether authorizeSAML2 generates a no token
        request.post("/api/public/saml2", Boolean.FALSE, HttpStatus.UNAUTHORIZED);
        assertStudentNotExists();
    }

    private void authenticate(Saml2AuthenticatedPrincipal principal) throws Exception {
        mockSAMLAuthentication(principal);
        request.postWithoutResponseBody("/api/public/saml2", Boolean.FALSE, HttpStatus.OK);
    }

    private void mockSAMLAuthentication() throws Exception {
        mockSAMLAuthentication(createPrincipal(STUDENT_REGISTRATION_NUMBER));
    }

    private void mockSAMLAuthentication(Saml2AuthenticatedPrincipal principal) throws Exception {
        gitlabRequestMockProvider.mockGetUserID(STUDENT_NAME, new org.gitlab4j.api.models.User().withId(1L));

        Authentication authentication = new Saml2Authentication(principal, "Secret Credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);
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
        userRepository.save(user);
    }

    private Saml2AuthenticatedPrincipal createPrincipal(String registrationNumber) {
        Map<String, List<Object>> attributes = new HashMap<>();
        attributes.put("uid", List.of(STUDENT_NAME));
        attributes.put("first_name", List.of("FirstName"));
        attributes.put("last_name", List.of("LastName"));
        attributes.put("email", List.of(STUDENT_NAME + "@invalid"));
        attributes.put("registration_number", List.of(registrationNumber));

        return new DefaultSaml2AuthenticatedPrincipal(STUDENT_NAME, attributes);
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

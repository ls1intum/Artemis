package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationSaml2Test;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.web.rest.UserJWTController;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

/**
 * Tests for {@link UserJWTController} and {@link SAML2Service}.
 *
 * @author Dominik Fuchss
 */
public class UserSaml2IntegrationTest extends AbstractSpringIntegrationSaml2Test {

    private static final String STUDENT_NAME = "student1";

    private static final String STUDENT_PASSWORD = "test123";

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    /**
     * This test checks the creation of a new SAML2 authenticated user.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testValidSaml2Registration() throws Exception {
        assertStudentNotExists();

        // Mock existing SAML2 Auth
        mockSAMLAuthentication();
        // Test whether authorizeSAML2 generates a valid token
        UserJWTController.JWTToken result = request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);

        assertValidToken(result);
        assertStudentExists();
    }

    /**
     * This test checks the successful login of an existing user via SAML2.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testValidSaml2Login() throws Exception {
        assertStudentNotExists();

        // Other mail than in #createPrincipal for identification of user
        String identifyingEmail = STUDENT_NAME + "@other.domain.invalid";

        // Create User
        createUser(identifyingEmail);
        assertStudentExists();

        // Mock existing SAML2 Auth
        mockSAMLAuthentication();
        // Test whether authorizeSAML2 generates a valid token
        UserJWTController.JWTToken result = request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);

        assertValidToken(result);
        assertStudentExists();
        assertThat(this.database.getUserByLogin(STUDENT_NAME).getEmail()).as("Email identifies already created user").isEqualTo(identifyingEmail);
    }

    /**
     * This test checks the successful login of an existing user via username and password (after creation via SAML2).
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testPasswordLoginAfterShibbolethRegistration() throws Exception {
        assertStudentNotExists();

        // Create user
        mockSAMLAuthentication();
        request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);
        assertStudentExists();

        // Change Password
        User student = userRepository.findUserWithGroupsAndAuthoritiesByLogin(STUDENT_NAME).get();
        student.setPassword(passwordService.encryptPassword(STUDENT_PASSWORD));
        userRepository.saveAndFlush(student);

        // Try to login ..
        TestSecurityContextHolder.clearContext();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        // Test whether authorize generates a valid token
        UserJWTController.JWTToken result = request.postWithResponseBody("/api/authenticate", createLoginVM(), UserJWTController.JWTToken.class, HttpStatus.OK, httpHeaders);
        assertValidToken(result);

        // Check SAML Login afterwards ..

        TestSecurityContextHolder.clearContext();
        // Mock existing SAML2 Auth
        mockSAMLAuthentication();
        // Test whether authorizeSAML2 generates a valid token
        result = request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);
        assertValidToken(result);
    }

    /**
     * This tests checks whether the access to the system is restricted if the login is not present.
     *
     * @throws Exception if something went wrong
     */
    @Test
    public void testInvalidAuthenticationSaml2Login() throws Exception {
        assertStudentNotExists();
        // Test whether authorizeSAML2 generates a no token
        request.post("/api/saml2", Boolean.FALSE, HttpStatus.UNAUTHORIZED);
        assertStudentNotExists();
    }

    @AfterEach
    public void clearAuthentication() {
        TestSecurityContextHolder.clearContext();
        this.database.resetDatabase();
    }

    private void mockSAMLAuthentication() {
        Authentication authentication = new Saml2Authentication(createPrincipal(), "Secret Credentials", null);
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

    private Saml2AuthenticatedPrincipal createPrincipal() {
        Map<String, List<Object>> attributes = new HashMap<>();
        attributes.put("uid", List.of(STUDENT_NAME));
        attributes.put("first_name", List.of("FirstName"));
        attributes.put("last_name", List.of("LastName"));
        attributes.put("email", List.of(STUDENT_NAME + "@invalid"));

        return new DefaultSaml2AuthenticatedPrincipal(STUDENT_NAME, attributes);
    }

    private void assertStudentNotExists() {
        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provided login student1 does not exist in database");
    }

    private void assertStudentExists() {
        assertThat(this.database.getUserByLogin(STUDENT_NAME)).as("User shall exist").isNotNull();
    }

    private void assertValidToken(UserJWTController.JWTToken token) {
        assertThat(this.tokenProvider.validateTokenForAuthority(token.getIdToken())).as("JWT Token is Valid").isTrue();
    }
}

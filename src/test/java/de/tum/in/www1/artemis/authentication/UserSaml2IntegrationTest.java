package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;

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

    @Autowired
    TokenProvider tokenProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordService passwordService;

    /**
     * This test checks the creation of a new SAML2 authenticated user.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testValidSaml2Registration() throws Exception {
        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provided login student1 does not exist in database");

        // Mock existing SAML2 Auth
        Authentication authentication = new Saml2Authentication(createPrincipal(STUDENT_NAME), "Secret Credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);

        // Test whether authorizeSAML2 generates a valid token
        UserJWTController.JWTToken result = request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);

        assertThat(this.tokenProvider.validateToken(result.getIdToken())).as("JWT Token is Valid").isTrue();
        assertThat(this.database.getUserByLogin(STUDENT_NAME)).as("User shall exist").isNotNull();
    }

    /**
     * This test checks the successful login of an existing user via SAML2.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testValidSaml2Login() throws Exception {
        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provided login student1 does not exist in database");

        // Other mail than in #createPrincipal for identification of user
        String identifyingEmail = STUDENT_NAME + "@other.domain.invalid";

        // Create User
        User user = new User();
        user.setLogin(STUDENT_NAME);
        user.setActivated(true);
        user.setEmail(identifyingEmail);
        userRepository.save(user);

        assertThat(this.database.getUserByLogin(STUDENT_NAME)).as("User shall exist").isNotNull();

        // Mock existing SAML2 Auth
        Authentication authentication = new Saml2Authentication(createPrincipal(STUDENT_NAME), "Secret Credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);

        // Test whether authorizeSAML2 generates a valid token
        UserJWTController.JWTToken result = request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);

        assertThat(this.tokenProvider.validateToken(result.getIdToken())).as("JWT Token is Valid").isTrue();
        assertThat(this.database.getUserByLogin(STUDENT_NAME)).as("User shall exist").isNotNull();
        assertThat(this.database.getUserByLogin(STUDENT_NAME).getEmail()).as("Email identifies already created user").isEqualTo(identifyingEmail);
    }

    /**
     * This test checks the successful login of an existing user via username and password (after creation via SAML2).
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testPasswordLoginAfterShibbolethRegistration() throws Exception {
        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provided login student1 does not exist in database");

        // Create user
        Authentication authentication = new Saml2Authentication(createPrincipal(STUDENT_NAME), "Secret Credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);
        request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);
        assertThat(this.database.getUserByLogin(STUDENT_NAME)).as("User shall exist").isNotNull();

        // Change Password
        String password = "test123";
        User student = userRepository.findUserWithGroupsAndAuthoritiesByLogin(STUDENT_NAME).get();
        student.setPassword(passwordService.encryptPassword(password));
        userRepository.saveAndFlush(student);

        // Try to login ..
        TestSecurityContextHolder.clearContext();
        LoginVM user = new LoginVM();
        user.setUsername(STUDENT_NAME);
        user.setPassword(password);
        user.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        // Test whether authorize generates a valid token
        UserJWTController.JWTToken result = request.postWithResponseBody("/api/authenticate", user, UserJWTController.JWTToken.class, HttpStatus.OK, httpHeaders);
        assertThat(this.tokenProvider.validateToken(result.getIdToken())).as("JWT Token is Valid").isTrue();

        // Check SAML Login afterwards ..

        TestSecurityContextHolder.clearContext();
        // Mock existing SAML2 Auth
        authentication = new Saml2Authentication(createPrincipal(STUDENT_NAME), "Secret Credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);
        // Test whether authorizeSAML2 generates a valid token
        result = request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);
        assertThat(this.tokenProvider.validateToken(result.getIdToken())).as("JWT Token is Valid").isTrue();
    }

    /**
     * This tests checks whether the access to the system is restricted if the login is not present.
     *
     * @throws Exception if something went wrong
     */
    @Test
    public void testInvalidAuthenticationSaml2Login() throws Exception {
        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provided login student1 does not exist in database");

        // Test whether authorizeSAML2 generates a no token
        request.post("/api/saml2", Boolean.FALSE, HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provided login student1 does not exist in database");
    }

    @AfterEach
    public void clearAuth() {
        TestSecurityContextHolder.clearContext();
        this.database.resetDatabase();
    }

    private Saml2AuthenticatedPrincipal createPrincipal(String username) {
        Saml2AuthenticatedPrincipal principal = new DefaultSaml2AuthenticatedPrincipal(username, new HashMap<>() {

            {
                put("uid", List.of(username));
                put("first_name", List.of("FirstName"));
                put("last_name", List.of("LastName"));
                put("email", List.of(username + "@invalid"));
            }
        });
        return principal;
    }
}

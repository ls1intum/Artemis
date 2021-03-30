package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationSaml2Test;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.web.rest.UserJWTController;

/**
 * Tests for {@link UserJWTController#authorizeSAML2(String)} and {@link SAML2Service}.
 *
 * @author Dominik Fuchss
 */
public class UserSaml2IntegrationTest extends AbstractSpringIntegrationSaml2Test {

    private static final String STUDENT_NAME = "student1";

    @Autowired
    TokenProvider tokenProvider;


    /**
     * This test checks the creation of a new SAML2 authenticated user.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    public void testValidSaml2Login() throws Exception {
        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class).hasMessage("Provided login student1 does not exist in database");

        // Mock existing SAML2 Auth
        Authentication authentication = new Saml2Authentication(createPrincipal(STUDENT_NAME), "Secret Credentials", null);
        TestSecurityContextHolder.setAuthentication(authentication);

        // Test whether authorizeSAML2 generates a valid token
        UserJWTController.JWTToken result = request.postWithResponseBody("/api/saml2", Boolean.FALSE, UserJWTController.JWTToken.class, HttpStatus.OK);

        assertThat(this.tokenProvider.validateToken(result.getIdToken())).as("JWT Token is Valid").isTrue();
        assertThat(this.database.getUserByLogin(STUDENT_NAME)).as("User shall exist").isNotNull();
    }

    /**
     * This tests checks whether the access to the system is restricted if the login is not present.
     *
     * @throws Exception if something went wrong
     */
    @Test
    public void testInvalidAuthenticationSaml2Login() throws Exception {
        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class).hasMessage("Provided login student1 does not exist in database");

        // Test whether authorizeSAML2 generates a no token
        request.post("/api/saml2", Boolean.FALSE, HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> this.database.getUserByLogin(STUDENT_NAME)).isInstanceOf(IllegalArgumentException.class).hasMessage("Provided login student1 does not exist in database");
    }

    @AfterEach
    public void clearAuth() {
        TestSecurityContextHolder.clearContext();
        this.database.resetDatabase();
    }


    private Saml2AuthenticatedPrincipal createPrincipal(String username) {
        Saml2AuthenticatedPrincipal principal = new DefaultSaml2AuthenticatedPrincipal(username, new HashMap<>() {{
            put("uid", List.of(username));
            put("first_name", List.of("FirstName"));
            put("last_name", List.of("LastName"));
            put("email", List.of(username + "@invalid"));
        }});
        return principal;
    }
}

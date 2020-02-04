package de.tum.in.www1.artemis.authentication;

import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.UserJWTController;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

@ActiveProfiles("artemis")
@TestPropertySource(properties = "artemis.user-management.use-external=false")
public class UserJWTIntegrationTest extends AuthenticationIntegrationTest {

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    RequestUtilService request;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithAnonymousUser
    public void testAuthenticateUsingJWT() throws Exception {
        testJWTAuthenticationForUser("student1", USER_PASSWORD);
    }

    private void testJWTAuthenticationForUser(String username, String password) throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(username);
        loginVM.setPassword(password);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        UserJWTController.JWTToken jwtToken = request.postWithResponseBody("/api/authenticate", loginVM, UserJWTController.JWTToken.class, HttpStatus.OK, httpHeaders);
        assertThat(jwtToken.getIdToken()).as("JWT token was issued").isNotNull();
    }
}

package de.tum.in.www1.artemis.authentication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;

public class PersonalAccessTokensTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TOKEN_API_URL = "/api/personal-access-token";

    private static final String ACTIVATE_NAME = "student1";

    private static final String NOT_ACTIVATE_NAME = "student2";

    @Value("${artemis.personal-access-token.max-lifetime-in-days}")
    private long personalAccessTokenMaxLifetimeDays;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        database.addUsers(2, 0, 0, 0);

        User notActivated = userRepository.findOneWithGroupsAndAuthoritiesByLogin(NOT_ACTIVATE_NAME).get();
        notActivated.setActivated(false);
        userRepository.save(notActivated);
    }

    @AfterEach
    public void teardown() {
        database.resetDatabase();
    }

    @Test
    @WithAnonymousUser
    void testNotFullyAuthorized() throws Exception {
        request.post(TOKEN_API_URL, this.personalAccessTokenMaxLifetimeDays, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = NOT_ACTIVATE_NAME, roles = "ADMIN")
    void testNotActivated() throws Exception {
        request.post(TOKEN_API_URL, this.personalAccessTokenMaxLifetimeDays, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = ACTIVATE_NAME, roles = "ADMIN")
    void testFullyAuthorized() throws Exception {
        request.postWithoutLocation(TOKEN_API_URL, this.personalAccessTokenMaxLifetimeDays, HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(username = ACTIVATE_NAME, roles = "ADMIN")
    void testInvalidLifetime() throws Exception {
        request.post(TOKEN_API_URL, this.personalAccessTokenMaxLifetimeDays + 1, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = ACTIVATE_NAME, roles = "ADMIN")
    void testNoLifetime() throws Exception {
        request.post(TOKEN_API_URL, "", HttpStatus.BAD_REQUEST);
    }
}

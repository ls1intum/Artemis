package de.tum.cit.aet.artemis.core.authentication;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.user.util.UserTestService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class UserAccountLocalVCIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "userlvc"; // shorter prefix as user's name is limited to 50 chars

    @Autowired
    private UserTestService userTestService;

    @BeforeEach
    void setUp() throws Exception {
        userTestService.setup(TEST_PREFIX, this);
    }

    @AfterEach
    void teardown() throws Exception {
        userTestService.tearDown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAndCreateParticipationVcsAccessTokenByUser() throws Exception {
        userTestService.getAndCreateParticipationVcsAccessToken();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationVcsAccessTokenByUserForTeamExercise() throws Exception {
        userTestService.getAndCreateParticipationVcsAccessTokenForTeamExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createAndDeleteUserVcsAccessTokenByUser() throws Exception {
        userTestService.createAndDeleteUserVcsAccessToken();
    }
}

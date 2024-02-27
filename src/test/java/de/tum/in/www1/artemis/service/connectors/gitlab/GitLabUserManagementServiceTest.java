package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.time.Duration;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

public class GitLabUserManagementServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "gitlabusermanagementservice";

    @Autowired
    private GitLabUserManagementService gitLabUserManagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    @BeforeEach
    void initTestCase() {
        gitlabRequestMockProvider.enableMockingOfRequests();
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    public void testPersonalAccessTokenRenewalNotNecessary() throws GitLabApiException, JsonProcessingException {
        User user = userRepository.getUser();
        user.setVcsAccessToken("ihdsf89w73rshefi8se892340f");
        userRepository.save(user);

        var gitlabUser = new org.gitlab4j.api.models.User().withId(596423L);

        gitlabRequestMockProvider.mockGetUserID(user.getLogin(), gitlabUser);
        gitlabRequestMockProvider.mockListPersonalAccessTokens(user.getLogin(), gitlabUser);

        gitLabUserManagementService.renewVersionControlAccessTokenIfNecessary(user, Duration.ZERO);

        gitlabRequestMockProvider.verifyMocks();
    }
}

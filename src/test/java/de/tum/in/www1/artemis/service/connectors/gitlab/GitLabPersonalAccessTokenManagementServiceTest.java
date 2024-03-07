package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;

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

class GitLabPersonalAccessTokenManagementServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "gitlabusermanagementservice";

    @Autowired
    private GitLabPersonalAccessTokenManagementService gitLabPersonalAccessTokenManagementService;

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
    void testRenewAccessToken() throws GitLabApiException, JsonProcessingException {
        final long initialTokenId = 46732L;
        final String initialToken = "ihdsf89w73rshefi8se892340f";
        final String newToken = "987z459hrf89w4r9z438rtweo84";
        final Duration newLifetime = Duration.ofDays(365);

        final User user = userRepository.getUser();
        user.setVcsAccessToken(initialToken);
        userRepository.save(user);

        var gitlabUser = new org.gitlab4j.api.models.User().withId(596423L).withUsername(user.getLogin());

        gitlabRequestMockProvider.mockGetUserApi();
        gitlabRequestMockProvider.mockGetUserID(gitlabUser);
        gitlabRequestMockProvider.mockCreatePersonalAccessToken(new HashMap<>() {

            {
                put(gitlabUser.getUsername(), newToken);
                put(gitlabUser.getId(), newToken);
            }
        });
        gitlabRequestMockProvider.mockListPersonalAccessTokens(initialTokenId);

        gitLabPersonalAccessTokenManagementService.renewAccessToken(user, newLifetime);

        gitlabRequestMockProvider.verifyMocks();

        final User updatedUser = userRepository.getUser();
        assertThat(updatedUser.getVcsAccessToken()).isEqualTo(newToken);
    }
}

package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenListResponseDTO;
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

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @CsvSource({ "ihdsf89w73rshefi8se892340f, 365, 596423", "erh8er9fhsrzg8ezfhergrifhr, 333, 6920", "io8949weisjdhfzgr7uejdirri, 94, 345", })
    void testCreateAccessToken(String token, int lifetimeDays, long gitlabUserId) throws GitLabApiException {
        final Duration lifetime = Duration.ofDays(lifetimeDays);

        final User user = userRepository.getUser();
        assertThat(user.getVcsAccessToken()).isNull();
        assertThat(user.getVcsAccessTokenExpiryDate()).isNull();

        var gitlabUser = new org.gitlab4j.api.models.User().withId(gitlabUserId).withUsername(user.getLogin());

        gitlabRequestMockProvider.mockGetUserApi();
        gitlabRequestMockProvider.mockGetUserID(gitlabUser);
        gitlabRequestMockProvider.mockCreatePersonalAccessToken(1, new HashMap<>() {

            {
                put(gitlabUser.getUsername(), token);
                put(gitlabUser.getId(), token);
            }
        });

        gitLabPersonalAccessTokenManagementService.createAccessToken(user, lifetime);

        gitlabRequestMockProvider.verifyMocks();

        final User updatedUser = userRepository.getUser();
        assertThat(updatedUser.getVcsAccessToken()).isEqualTo(token);
        assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isNotNull();
        assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isAfter(ZonedDateTime.now().plusDays(lifetimeDays - 1));
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @CsvSource({ "46732, ihdsf89w73rshefi8se892340f, 987z459hrf89w4r9z438rtweo84, 28, 365, 596423", "947, erh8er9fhsrzg8ezfhergrifhr, 8re9fsh8wewrufrhfgtguwrgr9r, -3, 333, 6920",
            "22, io8949weisjdhfzgr7uejdirri, 94egdvfiszeu3289eoshdurfrir, -250, 94, 345", })
    void testRenewAccessToken(long initialTokenId, String initialToken, String newToken, long initialTokenLifetimeDays, int newLifetimeDays, long gitlabUserId)
            throws GitLabApiException {
        final Duration newLifetime = Duration.ofDays(newLifetimeDays);

        final User user = userRepository.getUser();
        user.setVcsAccessToken(initialToken);
        user.setVcsAccessTokenExpiryDate(ZonedDateTime.now().minusDays(initialTokenLifetimeDays));
        userRepository.save(user);

        var gitlabUser = new org.gitlab4j.api.models.User().withId(gitlabUserId).withUsername(user.getLogin());

        gitlabRequestMockProvider.mockGetUserApi();
        gitlabRequestMockProvider.mockGetUserID(gitlabUser);
        gitlabRequestMockProvider.mockCreatePersonalAccessToken(1, new HashMap<>() {

            {
                put(gitlabUser.getUsername(), newToken);
                put(gitlabUser.getId(), newToken);
            }
        });
        gitlabRequestMockProvider.mockListPersonalAccessTokens(1, new HashMap<>() {

            {
                put(gitlabUser.getId(),
                        new GitLabPersonalAccessTokenListResponseDTO(initialTokenId, Date.from(Instant.now().plusSeconds(initialTokenLifetimeDays * 24 * 60 * 60))));
            }
        });

        gitLabPersonalAccessTokenManagementService.renewAccessToken(user, newLifetime);

        gitlabRequestMockProvider.verifyMocks();

        final User updatedUser = userRepository.getUser();
        assertThat(updatedUser.getVcsAccessToken()).isEqualTo(newToken);
        assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isNotNull();
        assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isAfter(ZonedDateTime.now().plusDays(newLifetimeDays - 1));
    }
}

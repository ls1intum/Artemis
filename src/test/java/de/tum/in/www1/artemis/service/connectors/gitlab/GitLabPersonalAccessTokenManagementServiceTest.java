package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
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

    @BeforeEach
    void setUp() {
        gitlabRequestMockProvider.enableMockingOfRequests();
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "versionControlAccessToken", true);
    }

    @AfterEach
    void teardown() throws Exception {
        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "versionControlAccessToken", false);
        gitlabRequestMockProvider.reset();
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAccessTokenUserWithAccessToken() {
        final User user = userRepository.getUser();
        user.setVcsAccessToken("sdhfisfhse");
        userRepository.save(user);

        assertThatThrownBy(() -> gitLabPersonalAccessTokenManagementService.createAccessToken(user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gitLabPersonalAccessTokenManagementService.createAccessToken(user, Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAccessTokenNoGitLabUser() {
        final User user = userRepository.getUser();
        user.setVcsAccessToken(null);
        userRepository.save(user);

        gitlabRequestMockProvider.mockGetUserApi();

        // Does nothing because user has no associated GitLab user.
        gitLabPersonalAccessTokenManagementService.createAccessToken(user);

        final User updatedUser = userRepository.getUser();
        assertThat(updatedUser.getVcsAccessToken()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAccessTokenCreationFailed() throws GitLabApiException {
        final User user = userRepository.getUser();
        user.setVcsAccessToken(null);
        userRepository.save(user);

        gitlabRequestMockProvider.mockGetUserApi();
        gitlabRequestMockProvider.mockGetUserID(new org.gitlab4j.api.models.User().withId(23L).withUsername(user.getLogin()));
        gitlabRequestMockProvider.mockCreatePersonalAccessTokenError();

        assertThatThrownBy(() -> gitLabPersonalAccessTokenManagementService.createAccessToken(user)).isInstanceOf(GitLabException.class);
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
        gitlabRequestMockProvider.mockListAndRevokePersonalAccessTokens(1, new HashMap<>() {

            {
                put(gitlabUser.getId(), new GitLabPersonalAccessTokenListResponseDTO(initialTokenId));
            }
        });

        gitLabPersonalAccessTokenManagementService.renewAccessToken(user, newLifetime);

        gitlabRequestMockProvider.verifyMocks();

        final User updatedUser = userRepository.getUser();
        assertThat(updatedUser.getVcsAccessToken()).isEqualTo(newToken);
        assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isNotNull();
        assertThat(updatedUser.getVcsAccessTokenExpiryDate()).isAfter(ZonedDateTime.now().plusDays(newLifetimeDays - 1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenewAccessTokenUserWithoutAccessToken() {
        final User user = userRepository.getUser();
        user.setVcsAccessToken(null);
        userRepository.save(user);

        assertThatThrownBy(() -> gitLabPersonalAccessTokenManagementService.renewAccessToken(user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gitLabPersonalAccessTokenManagementService.renewAccessToken(user, Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenewAccessTokenNoGitLabUser() {
        final User user = userRepository.getUser();
        user.setVcsAccessToken("sdhfosef");
        userRepository.save(user);

        gitlabRequestMockProvider.mockGetUserApi();

        assertThatThrownBy(() -> gitLabPersonalAccessTokenManagementService.renewAccessToken(user)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenewAccessTokenRevocationFailed() throws GitLabApiException {
        final User user = userRepository.getUser();
        user.setVcsAccessToken("sdhfosef");
        userRepository.save(user);

        final var gitlabUser = new org.gitlab4j.api.models.User().withId(23L).withUsername(user.getLogin());

        gitlabRequestMockProvider.mockGetUserApi();
        gitlabRequestMockProvider.mockGetUserID(gitlabUser);
        gitlabRequestMockProvider.mockListAndRevokePersonalAccessTokens(1, new HashMap<>() {

            {
                put(gitlabUser.getId(), new GitLabPersonalAccessTokenListResponseDTO(42L));
            }
        });
        RestTemplate restTemplateMock = mock(RestTemplate.class);
        doThrow(new RestClientException("Simulated error")).when(restTemplateMock).delete(anyString());
        doAnswer(invocation -> gitlabRequestMockProvider.getRestTemplate().exchange((String) invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                (ParameterizedTypeReference) invocation.getArgument(3))).when(restTemplateMock)
                .exchange(anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "restTemplate", restTemplateMock);
        assertThatThrownBy(() -> gitLabPersonalAccessTokenManagementService.renewAccessToken(user)).isInstanceOf(GitLabException.class);
        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "restTemplate", gitlabRequestMockProvider.getRestTemplate());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenewAccessTokenListFailed() throws GitLabApiException {
        final User user = userRepository.getUser();
        user.setVcsAccessToken("sdhfosef");
        userRepository.save(user);

        gitlabRequestMockProvider.mockGetUserApi();
        gitlabRequestMockProvider.mockGetUserID(new org.gitlab4j.api.models.User().withId(23L).withUsername(user.getLogin()));
        RestTemplate restTemplateMock = mock(RestTemplate.class);
        when(restTemplateMock.exchange(anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class))).thenThrow(new RestClientException("Simulated error"));

        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "restTemplate", restTemplateMock);
        assertThatThrownBy(() -> gitLabPersonalAccessTokenManagementService.renewAccessToken(user)).isInstanceOf(GitLabException.class);
        ReflectionTestUtils.setField(gitLabPersonalAccessTokenManagementService, "restTemplate", gitlabRequestMockProvider.getRestTemplate());
    }
}

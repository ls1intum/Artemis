package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.UserApi;
import org.gitlab4j.api.models.ImpersonationToken;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenListRequestDTO;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenListResponseDTO;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenRotateRequestDTO;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenRotateResponseDTO;
import de.tum.in.www1.artemis.service.connectors.vcs.VcsTokenManagementService;

@Service
@Profile("gitlab")
public class GitLabPersonalAccessTokenManagementService extends VcsTokenManagementService {

    private static final String PERSONAL_ACCESS_TOKEN_NAME = "Artemis-Automatic-Access-Token";

    private static final Logger log = LoggerFactory.getLogger(GitLabPersonalAccessTokenManagementService.class);

    private final UserRepository userRepository;

    private final GitLabApi gitlabApi;

    protected final RestTemplate restTemplate;

    public GitLabPersonalAccessTokenManagementService(UserRepository userRepository, GitLabApi gitlabApi, @Qualifier("gitlabRestTemplate") RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.gitlabApi = gitlabApi;
        this.restTemplate = restTemplate;
    }

    /**
     * Generate a version control access token and store it in the user object, if it is needed.
     * It is needed if
     * 1. the config option is enabled, and
     * 2. the user does not yet have an access token
     *
     * The GitLab user will be extracted from the Gitlab user API
     *
     * @param user the Artemis user (where the token will be stored)
     */
    @Override
    public void createAccessToken(User user, Duration lifetime) {
        var gitlabUser = getGitLabUserFromUser(user);
        if (gitlabUser != null && user.getVcsAccessToken() == null) {
            ImpersonationToken personalAccessToken = createPersonalAccessToken(gitlabUser.getId(), lifetime);
            savePersonalAccessTokenOfUser(personalAccessToken, user);
        }
    }

    /**
     * Create a personal access token for the user with the given id.
     * The token has scopes "read_repository" and "write_repository".
     *
     * @param userId the id of the user in Gitlab
     * @return the personal access token created for that user
     */
    private ImpersonationToken createPersonalAccessToken(Long userId, Duration lifetime) {
        try {
            return gitlabApi.getUserApi().createPersonalAccessToken(userId, PERSONAL_ACCESS_TOKEN_NAME, getExpiryDateFromLifetime(lifetime),
                    new ImpersonationToken.Scope[] { ImpersonationToken.Scope.READ_REPOSITORY, ImpersonationToken.Scope.WRITE_REPOSITORY });
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while creating personal access token", e);
        }
    }

    private void savePersonalAccessTokenOfUser(ImpersonationToken token, User user) {
        user.setVcsAccessToken(token.getToken());
        user.setVcsAccessTokenExpiryDate(token.getExpiresAt().toInstant().atZone(ZoneId.systemDefault()));
        userRepository.save(user);
    }

    @Override
    public void renewAccessToken(User user, Duration newLifetime) {
        if (user.getVcsAccessToken() == null) {
            throw new IllegalArgumentException("User has no VCS access token to be renewed");
        }

        var gitlabUser = getGitLabUserFromUser(user);
        if (gitlabUser != null) {
            renewVersionControlAccessToken(gitlabUser, user, newLifetime);
        }
    }

    private void renewVersionControlAccessToken(org.gitlab4j.api.models.User gitlabUser, User user, Duration newLifetime) {
        ImpersonationToken renewedPersonalAccessToken = retrieveRenewedPersonalAccessToken(gitlabUser.getId(), newLifetime);
        savePersonalAccessTokenOfUser(renewedPersonalAccessToken, user);
    }

    private ImpersonationToken retrieveRenewedPersonalAccessToken(Long userId, Duration newLifetime) {
        GitLabPersonalAccessTokenListResponseDTO response = fetchPersonalAccessTokenId(userId);
        return rotatePersonalAccessToken(response.getId(), userId, getExpiryDateFromLifetime(newLifetime));
    }

    private GitLabPersonalAccessTokenListResponseDTO fetchPersonalAccessTokenId(Long userId) {
        var body = new GitLabPersonalAccessTokenListRequestDTO(PERSONAL_ACCESS_TOKEN_NAME, userId);
        var entity = new HttpEntity<>(body);

        try {
            var response = restTemplate.exchange(gitlabApi.getGitLabServerUrl() + "/api/v4/personal_access_tokens", HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<GitLabPersonalAccessTokenListResponseDTO>>() {
                    });
            var responseBody = response.getBody();
            if (responseBody == null || responseBody.isEmpty()) {
                log.error("Could not fetch personal access token id for user with id {}, response is null", userId);
                throw new GitLabException("Error while fetching personal access token id");
            }
            return responseBody.get(0);
        }
        catch (HttpClientErrorException e) {
            log.error("Could not fetch personal access token id for user with id {}, response is null", userId);
            throw new GitLabException("Error while fetching personal access token id");
        }
    }

    private ImpersonationToken rotatePersonalAccessToken(Long personalAccessTokenId, Long userId, Date newExpiryDate) {
        var body = new GitLabPersonalAccessTokenRotateRequestDTO(personalAccessTokenId, newExpiryDate);
        var entity = new HttpEntity<>(body);

        GitLabPersonalAccessTokenRotateResponseDTO responseBody;
        try {
            var response = restTemplate.exchange(gitlabApi.getGitLabServerUrl() + "/api/v4/personal_access_tokens/" + personalAccessTokenId + "/rotate", HttpMethod.POST, entity,
                    GitLabPersonalAccessTokenRotateResponseDTO.class);
            responseBody = response.getBody();
            if (responseBody == null || responseBody.getToken() == null) {
                log.error("Could not rotate Gitlab personal access token for user with id {}, response is null", userId);
                throw new GitLabException("Error while creating personal access token");
            }
        }
        catch (HttpClientErrorException e) {
            log.error("Could not rotate Gitlab personal access token for user with id {}, response is null", userId);
            throw new GitLabException("Error while creating personal access token");
        }

        ImpersonationToken result = new ImpersonationToken();
        // Todo: more attributes necessary?
        result.setId(responseBody.getId());
        result.setToken(responseBody.getToken());
        result.setExpiresAt(responseBody.getExpiresAt());
        return result;
    }

    private org.gitlab4j.api.models.User getGitLabUserFromUser(User user) {
        UserApi userApi = gitlabApi.getUserApi();
        try {
            return userApi.getUser(user.getLogin());
        }
        catch (GitLabApiException e) {
            log.error("Could not get GitLab user for the user {}", user.getLogin(), e);
            return null;
        }
    }

    private static Date getExpiryDateFromLifetime(Duration lifetime) {
        return Date.from(LocalDateTime.now().plus(lifetime).atZone(ZoneId.systemDefault()).toInstant());
    }
}

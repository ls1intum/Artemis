package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.UserApi;
import org.gitlab4j.api.models.ImpersonationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenListRequestDTO;
import de.tum.in.www1.artemis.service.connectors.gitlab.dto.GitLabPersonalAccessTokenListResponseDTO;
import de.tum.in.www1.artemis.service.connectors.vcs.VcsTokenManagementService;

/**
 * Provides VCS access token services for GitLab via means of personal access tokens.
 */
@Component
@Profile("gitlab")
public class GitLabPersonalAccessTokenManagementService extends VcsTokenManagementService {

    /**
     * The name of the personal access token used in GitLab.
     */
    private static final String PERSONAL_ACCESS_TOKEN_NAME = "Artemis-Automatic-Access-Token";

    private static final Logger log = LoggerFactory.getLogger(GitLabPersonalAccessTokenManagementService.class);

    private final UserRepository userRepository;

    private final GitLabApi gitlabApi;

    /**
     * Used to send and receive HTTP requests. This is necessary because the {@link GitLabApi} does not currently implement all necessary request types.
     */
    private final RestTemplate restTemplate;

    public GitLabPersonalAccessTokenManagementService(UserRepository userRepository, GitLabApi gitlabApi, @Qualifier("gitlabRestTemplate") RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.gitlabApi = gitlabApi;
        this.restTemplate = restTemplate;
    }

    /**
     * Generates a VCS access token for a given user with a specific lifetime, required that the user does not yet have a VCS access token.
     * This implementation for GitLab requires that there is a GitLab user associated with the user.
     *
     * @param user     the user to create an access token for
     * @param lifetime the lifetime of the created access token
     */
    @Override
    public void createAccessToken(User user, Duration lifetime) {
        if (user.getVcsAccessToken() != null) {
            throw new IllegalArgumentException("User already has an access token");
        }

        var gitlabUser = getGitLabUserFromUser(user);
        if (gitlabUser == null) {
            throw new IllegalStateException("There is no GitLab user associated with the user");
        }

        ImpersonationToken personalAccessToken = createPersonalAccessToken(gitlabUser.getId(), lifetime);
        savePersonalAccessTokenOfUser(personalAccessToken, user);
    }

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

    /**
     * Generates a new VCS access token for a given user with a given lifetime, required that the user already has a VCS access token, which may or may not be valid.
     * This implementation for GitLab requires that there is a GitLab user associated with the user.
     *
     * @param user        the user whose access token is to be renewed
     * @param newLifetime the lifetime for the newly crated access token
     */
    @Override
    public void renewAccessToken(User user, Duration newLifetime) {
        if (user.getVcsAccessToken() == null) {
            throw new IllegalArgumentException("User has no VCS access token to be renewed");
        }

        var gitlabUser = getGitLabUserFromUser(user);
        if (gitlabUser == null) {
            throw new IllegalStateException("There is no GitLab user associated with the user");
        }

        renewVersionControlAccessToken(gitlabUser, user, newLifetime);
    }

    private void renewVersionControlAccessToken(org.gitlab4j.api.models.User gitlabUser, User user, Duration newLifetime) {
        GitLabPersonalAccessTokenListResponseDTO response = fetchPersonalAccessTokenId(gitlabUser.getId());

        try {
            gitlabApi.getUserApi().revokeImpersonationToken(gitlabUser.getId(), response.getId());
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while revoking personal access token", e);
        }

        // Set access token to null for local user object, otherwise createAccessToken does nothing.
        user.setVcsAccessToken(null);
        user.setVcsAccessTokenExpiryDate(null);

        createAccessToken(user, newLifetime);
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
            return responseBody.get(0); // We assume that there exists no other personal access token with name from PERSONAL_ACCESS_TOKEN_NAME.
        }
        catch (HttpClientErrorException e) {
            log.error("Could not fetch personal access token id for user with id {}, response is null", userId);
            throw new GitLabException("Error while fetching personal access token id");
        }
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
        return Date.from(Instant.now().plus(lifetime));
    }
}

package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
@Profile("gitlab")
public class GitLabPersonalAccessTokenRenewalService {

    private static final int MINIMAL_LIFETIME_DAYS = 28;

    private final GitLabUserManagementService gitLabUserManagementService;

    private final UserRepository userRepository;

    public GitLabPersonalAccessTokenRenewalService(GitLabUserManagementService gitLabUserManagementService, UserRepository userRepository) {
        this.gitLabUserManagementService = gitLabUserManagementService;
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "0  0  4 * * SUN") // Every sunday at 4 am
    public void renewAllPersonalAccessTokens() {
        for (User user : userRepository.findAll()) {
            gitLabUserManagementService.renewVersionControlAccessTokenIfNecessary(user, Duration.ofDays(MINIMAL_LIFETIME_DAYS));
        }
    }

}

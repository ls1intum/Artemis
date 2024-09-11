package de.tum.cit.aet.artemis.service.connectors.vcs;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.service.connectors.gitlab.GitLabException;

/**
 * Uses the scheduled task {@link #renewAllVcsAccessTokens} to periodically renew all VCS access tokens that have expired or that are about to expire.
 */
@Service
@Profile(PROFILE_SCHEDULING)
public class VcsTokenRenewalService {

    /**
     * The minimal lifetime that an access token must have, otherwise it will be renewed.
     */
    private static final Duration MINIMAL_LIFETIME = Duration.ofDays(28);

    private static final Logger log = LoggerFactory.getLogger(VcsTokenRenewalService.class);

    /**
     * The active service for managing VCS access tokens. May be empty if the current configuration does not require the use of access tokens.
     */
    private final Optional<VcsTokenManagementService> vcsTokenManagementService;

    private final UserRepository userRepository;

    /**
     * The config parameter for enabling VCS access tokens.
     */
    private final boolean useVersionControlAccessToken;

    // note: we inject the configuration value here to easily test with different ones
    public VcsTokenRenewalService(@Value("${artemis.version-control.use-version-control-access-token:#{false}}") boolean versionControlAccessToken,
            Optional<VcsTokenManagementService> vcsTokenManagementService, UserRepository userRepository) {
        this.useVersionControlAccessToken = versionControlAccessToken;
        this.vcsTokenManagementService = vcsTokenManagementService;
        this.userRepository = userRepository;
    }

    /**
     * Periodically renews all VCS access tokens that have expired or that are about to expire.
     * Additionally, new access tokens for users with missing access tokens are created.
     * This method has no effect if the VCS access token config option is disabled.
     */
    @Scheduled(cron = "0 0 4 * * SUN") // Every sunday at 4 am
    public void renewAllVcsAccessTokens() {
        if (useVersionControlAccessToken && vcsTokenManagementService.isPresent()) {
            log.info("Started scheduled access token renewal");
            int renewedAccessTokenCount = renewExpiringAccessTokens();
            int createdAccessTokenCount = createMissingAccessTokens();
            log.info("Finished scheduled access token renewal: renewed {} and created {}", renewedAccessTokenCount, createdAccessTokenCount);
        }
    }

    private int renewExpiringAccessTokens() {
        Set<User> users = userRepository.getUsersWithAccessTokenExpirationDateBefore(ZonedDateTime.now().plus(MINIMAL_LIFETIME));
        for (User user : users) {
            try {
                vcsTokenManagementService.orElseThrow().renewAccessToken(user);
            }
            catch (GitLabException | IllegalStateException e) {
                log.warn("Failed to renew VCS access token for user {}", user.getLogin(), e);
            }
        }
        return users.size();
    }

    private int createMissingAccessTokens() {
        Set<User> users = userRepository.getUsersWithAccessTokenNull();
        for (User user : users) {
            try {
                vcsTokenManagementService.orElseThrow().createAccessToken(user);
            }
            catch (GitLabException e) {
                log.warn("Failed to create VCS access token for user {}", user.getLogin(), e);
            }
        }
        return users.size();
    }
}

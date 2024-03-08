package de.tum.in.www1.artemis.service.connectors.vcs;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;

/**
 * Uses the scheduled task {@link #renewAllPersonalAccessTokens} to periodically renew all VCS access tokens that have expired or that are about to expire.
 */
@Profile(PROFILE_CORE)
@Service
public class VcsTokenRenewalService {

    /**
     * The minimal lifetime that an access token must have, otherwise it will be renewed.
     */
    private static final Duration MINIMAL_LIFETIME = Duration.ofDays(28);

    /**
     * The active service for managing VCS access tokens. May be empty if the current configuration does not require the use of access tokens.
     */
    private final Optional<VcsTokenManagementService> vcsTokenManagementService;

    private final UserRepository userRepository;

    public VcsTokenRenewalService(Optional<VcsTokenManagementService> vcsTokenManagementService, UserRepository userRepository) {
        this.vcsTokenManagementService = vcsTokenManagementService;
        this.userRepository = userRepository;
    }

    /**
     * Periodically renews all VCS access tokens that have expired or that are about to expire.
     */
    @Scheduled(cron = "0  0  4 * * SUN") // Every sunday at 4 am
    public void renewAllPersonalAccessTokens() {
        if (vcsTokenManagementService.isPresent()) {
            for (User user : userRepository.getUsersWithAccessTokenExpirationDateBefore(ZonedDateTime.now().plus(MINIMAL_LIFETIME))) {
                vcsTokenManagementService.get().renewAccessToken(user);
            }
        }
    }
}

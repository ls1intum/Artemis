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

@Profile(PROFILE_CORE)
@Service
public class VcsTokenRenewalService {

    private static final Duration MINIMAL_LIFETIME = Duration.ofDays(28);

    private final Optional<VcsTokenManagementService> vcsTokenManagementService;

    private final UserRepository userRepository;

    public VcsTokenRenewalService(Optional<VcsTokenManagementService> vcsTokenManagementService, UserRepository userRepository) {
        this.vcsTokenManagementService = vcsTokenManagementService;
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "0  0  4 * * SUN") // Every sunday at 4 am
    public void renewAllPersonalAccessTokens() {
        if (vcsTokenManagementService.isPresent()) {
            for (User user : userRepository.findAll()) {
                if (user.getVcsAccessToken() != null && Duration.between(ZonedDateTime.now(), user.getVcsAccessTokenExpiryDate()).compareTo(MINIMAL_LIFETIME) < 0) {
                    vcsTokenManagementService.get().renewAccessToken(user);
                }
            }
        }
    }

}

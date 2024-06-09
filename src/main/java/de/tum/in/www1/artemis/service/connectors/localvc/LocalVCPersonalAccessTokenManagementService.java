package de.tum.in.www1.artemis.service.connectors.localvc;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.vcs.VcsTokenManagementService;
import de.tum.in.www1.artemis.web.rest.UserResource;

@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCPersonalAccessTokenManagementService extends VcsTokenManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserRepository userRepository;

    @Value("${artemis.version-control.version-control-access-token:#{false}}")
    private Boolean versionControlAccessToken;

    @Value("${artemis.version-control.vc-access-token-max-lifetime-in-days:365}")
    private int vcMaxLifetimeInDays;

    private static final String TOKEN_PREFIX = "vcpat-";

    private static final int RANDOM_STRING_LENGTH = 40;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public LocalVCPersonalAccessTokenManagementService(UserRepository userRepository) {
        super();
        this.userRepository = userRepository;
    }

    @Override
    public void createAccessToken(User user, Duration lifetime) {
        if (versionControlAccessToken) {
            userRepository.updateUserVCAccessToken(user.getId(), generateSecureToken(), ZonedDateTime.now().plus(Duration.ofDays(vcMaxLifetimeInDays)));
            log.info("Created access token for user {}", user.getId());
        }
    }

    @Override
    public void renewAccessToken(User user, Duration newLifetime) {
        if (versionControlAccessToken) {
            if (user.getVcsAccessTokenExpiryDate() != null && user.getVcsAccessTokenExpiryDate().isBefore(ZonedDateTime.now())) {
                // todo create new one here and
                userRepository.updateUserVCAccessToken(user.getId(), user.getVcsAccessToken(), ZonedDateTime.now().plus(Duration.ofDays(vcMaxLifetimeInDays)));
            }
            log.info("Renewed access token for user {}", user.getId());
        }
    }

    public static String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder randomString = new StringBuilder(RANDOM_STRING_LENGTH);

        for (int i = 0; i < RANDOM_STRING_LENGTH; i++) {
            int randomIndex = secureRandom.nextInt(CHARACTERS.length());
            randomString.append(CHARACTERS.charAt(randomIndex));
        }
        return TOKEN_PREFIX + randomString.toString();
    }
}

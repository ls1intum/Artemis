package de.tum.in.www1.artemis.service.connectors.localvc;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCPersonalAccessTokenManagementService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCPersonalAccessTokenManagementService.class);

    @Value("${artemis.version-control.use-localvc-version-control-access-token:false}")
    private boolean useVersionControlAccessToken;

    @Value("${artemis.version-control.vc-access-token-max-lifetime-in-days:365}")
    private int vcMaxLifetimeInDays;

    private static final String TOKEN_PREFIX = "vcpat-";

    private static final int RANDOM_STRING_LENGTH = 40;

    public static final int VCS_ACCESS_TOKEN_LENGTH = TOKEN_PREFIX.length() + RANDOM_STRING_LENGTH;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public static String generateSecureVCSAccessToken() {
        log.debug("Generate secure vcs access token");
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder randomString = new StringBuilder(RANDOM_STRING_LENGTH);

        for (int i = 0; i < RANDOM_STRING_LENGTH; i++) {
            int randomIndex = secureRandom.nextInt(CHARACTERS.length());
            randomString.append(CHARACTERS.charAt(randomIndex));
        }
        return TOKEN_PREFIX + randomString.toString();
    }
}

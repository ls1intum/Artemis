package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.security.SecureRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCPersonalAccessTokenManagementService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCPersonalAccessTokenManagementService.class);

    public static final String TOKEN_PREFIX = "vcpat-";

    public static final int VCS_ACCESS_TOKEN_LENGTH = 50; // database stores token as varchar(50)

    private static final int RANDOM_STRING_LENGTH = VCS_ACCESS_TOKEN_LENGTH - TOKEN_PREFIX.length();

    private static final String[] CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".split("");

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a secure vcs access token
     *
     * @return the token
     */
    public static String generateSecureVCSAccessToken() {
        log.debug("Generate secure vcs access token");
        return TOKEN_PREFIX + RANDOM.ints(RANDOM_STRING_LENGTH, 0, CHARACTERS.length).mapToObj(it -> CHARACTERS[it]).collect(Collectors.joining(""));
    }
}

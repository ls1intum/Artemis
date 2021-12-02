package de.tum.in.www1.artemis.service.util;

import java.security.SecureRandom;

import org.springframework.context.annotation.Profile;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

@Profile(SPRING_PROFILE_DEVELOPMENT)
public class VCSSimulationUtils {

    /**
     * Simulates a commit Hash, the returned string consist out of 40 times the same number
     * @return the simulated commitHash
     */
    public static String simulateCommitHash() {
        SecureRandom secureRandom = new SecureRandom();
        String number = String.valueOf(secureRandom.nextInt(10));
        String commitHash = number;
        for (int i = 0; i < 39; i++) {
            commitHash = commitHash + number;
        }
        return commitHash;
    }
}

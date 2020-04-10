package de.tum.in.www1.artemis.service.util;

import java.util.Random;

public class VCSSimulationUtils {

    /**
     * Simulates a commit Hash, the returned string consist out of 40 times the same number
     * @return the simulated commitHash
     */
    public static String simulateCommitHash() {
        Random random = new Random();
        String number = String.valueOf(random.nextInt(10));
        String commitHash = number;
        for (int i = 0; i < 39; i++) {
            commitHash = commitHash + number;
        }
        return commitHash;
    }
}

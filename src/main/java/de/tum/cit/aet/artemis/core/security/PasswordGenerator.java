package de.tum.cit.aet.artemis.core.security;

import java.security.SecureRandom;

public class PasswordGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_+=<>?";

    private static final int PASSWORD_LENGTH = 32;

    /**
     * <p>
     * USE WITH CAUTION: <b>THIS METHOD SHOULD NOT BE USED FOR GENERATING ACTUALLY USED PASSWORDS OR EVEN LONG-TERM PASSWORDS.</b>
     * </p>
     * Use a dedicated library instead, this method has multiple drawbacks:
     *
     * <ul>
     * <li>low alphabet entropy</li>
     * <li>not hashed</li>
     * <li>rather short with 32 chars</li>
     * </ul>
     */
    public static String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = random.nextInt(CHARACTERS.length());
            password.append(CHARACTERS.charAt(index));
        }

        return password.toString();
    }
}

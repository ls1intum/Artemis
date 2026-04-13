package de.tum.cit.aet.artemis.core.security;

import java.security.SecureRandom;

import org.apache.commons.lang3.RandomStringUtils;

/**
 * Inlined replacement for {@code tech.jhipster.security.RandomUtil}.
 * <p>
 * Utility class for generating random alphanumeric strings used as
 * passwords, activation keys, and reset keys.
 */
public final class RandomUtil {

    private static final int DEF_COUNT = 20;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        SECURE_RANDOM.nextBytes(new byte[64]);
    }

    private RandomUtil() {
    }

    public static String generateRandomAlphanumericString() {
        return RandomStringUtils.random(DEF_COUNT, 0, 0, true, true, null, SECURE_RANDOM);
    }

    public static String generatePassword() {
        return generateRandomAlphanumericString();
    }

    public static String generateActivationKey() {
        return generateRandomAlphanumericString();
    }

    public static String generateResetKey() {
        return generateRandomAlphanumericString();
    }
}

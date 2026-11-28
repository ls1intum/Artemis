package de.tum.cit.aet.artemis.account.security;

import java.security.SecureRandom;

import org.apache.commons.lang3.RandomStringUtils;

/**
 * Inlined replacement for {@code tech.jhipster.security.RandomUtil}.
 * <p>
 * Utility class for generating cryptographically secure random strings and bounded integers.
 * Strings are used as passwords, activation keys, and reset keys.
 */
public final class RandomUtil {

    private static final int DEF_COUNT = 20;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        SECURE_RANDOM.nextBytes(new byte[64]);
    }

    private RandomUtil() {
    }

    /**
     * Draws a bounded integer using the shared, thread-safe secure generator.
     *
     * @param bound the exclusive upper bound, greater than zero
     * @return a value from zero (inclusive) to bound (exclusive)
     */
    public static int nextInt(int bound) {
        return SECURE_RANDOM.nextInt(bound);
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

    public static String generateResetKeyId() {
        return generateRandomAlphanumericString();
    }

    public static String generateResetKeySecret() {
        return generateRandomAlphanumericString();
    }

    public static String generateExchangeCode() {
        return generateRandomAlphanumericString();
    }
}

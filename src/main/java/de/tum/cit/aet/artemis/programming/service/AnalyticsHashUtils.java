package de.tum.cit.aet.artemis.programming.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import de.tum.cit.aet.artemis.programming.domain.ExperimentalGroup;

/**
 * Utility class providing cryptographic hash functions for user ID anonymization
 * and deterministic distribution across experimental groups for A/B testing.
 */
public class AnalyticsHashUtils {

    /**
     * Calculates the HMAC-SHA256 hash for the given data using the provided secret key.
     *
     * @param data the data string to be hashed
     * @param key  the secret configuration key used as the HMAC pepper
     * @return the hexadecimal string representation of the calculated HMAC
     */
    private static String calculateHmac(String data, String key) {
        Objects.requireNonNull(data, "Data for HMAC calculation must not be null");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("VCS analytics secret key must be properly configured and not blank");
        }
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hmacBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        }
        catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 for VCS analytics", e);
        }
    }

    /**
     * Deterministically and uniformly distributes a user into one of the four experimental groups.
     *
     * @param userId    the unique identifier of the user
     * @param secretKey the server-side secret key used to secure the distribution hash
     * @return the assigned {@link ExperimentalGroup} enum constant for the user
     */
    public static ExperimentalGroup getGroup(Long userId, String secretKey) {
        Objects.requireNonNull(userId, "userId must not be null");
        String hash = calculateHmac(String.valueOf(userId), secretKey);
        int groupIndex = Math.abs(hash.hashCode()) % 4;
        return ExperimentalGroup.values()[groupIndex];
    }

    /**
     * Generates a secure, anonymized, course-scoped identifier for a user using HMAC-SHA256.
     *
     * @param userId    the unique identifier of the user
     * @param courseId  the unique identifier of the course context
     * @param secretKey the server-side secret key used to secure the hash against offline brute-force attacks
     * @return a secure hexadecimal string representing the masked user ID
     */
    public static String maskUserId(Long userId, Long courseId, String secretKey) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(courseId, "courseId must not be null");
        String input = userId + "-" + courseId;
        return calculateHmac(input, secretKey);
    }
}

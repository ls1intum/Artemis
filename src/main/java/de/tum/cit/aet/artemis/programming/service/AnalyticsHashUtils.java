package de.tum.cit.aet.artemis.programming.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import de.tum.cit.aet.artemis.programming.domain.ExperimentalGroup;

/*
 * Class that provides hash functions to assist by experiments
 */
public class AnalyticsHashUtils {

    // Distributes npredictably user to one of four groups
    private static final String SALT = "VCPAT_experiment";

    public static ExperimentalGroup getGroup(Long userId) {
        String input = userId + "-" + SALT;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            BigInteger number = new BigInteger(1, hashBytes);
            int groupIndex = number.mod(BigInteger.valueOf(4)).intValue();

            return ExperimentalGroup.values()[groupIndex];
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    // Anonymizes userId for experiments. CategoryId helps to avoid correlation attacks attacks
    public static String maskUserId(Long userId, Long categoryId) {
        String input = userId + "-" + categoryId + "-" + SALT;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashBytes);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}

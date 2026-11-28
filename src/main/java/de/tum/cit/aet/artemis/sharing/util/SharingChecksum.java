// SPDX-License-Identifier: Apache-2.0
// Adapted from the CodeAbility Sharing Plugin Platform API. See META-INF/NOTICE.
package de.tum.cit.aet.artemis.sharing.util;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * The checksum used by the Sharing Platform's browser callback protocol.
 */
public final class SharingChecksum {

    private SharingChecksum() {
    }

    /**
     * Computes the legacy lowercase checksum over the shared secret followed by the values in parameter-name order.
     *
     * @param parameters callback parameters included in the checksum
     * @param secretKey  shared secret
     * @return the checksum in lowercase hexadecimal
     */
    public static String calculateChecksum(Map<String, String> parameters, String secretKey) {
        try {
            // The Sharing Platform protocol fixes MD5 and the JVM's default charset.
            MessageDigest digest = MessageDigest.getInstance("MD5");
            Charset charset = Charset.defaultCharset();
            digest.update(secretKey.getBytes(charset));
            for (String value : new TreeMap<>(parameters).values()) {
                digest.update(value.getBytes(charset));
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The MD5 algorithm required by the Sharing Platform is unavailable", e);
        }
    }

    /**
     * Checks the supplied checksum against the legacy calculation.
     *
     * @param parameters callback parameters included in the checksum
     * @param secretKey  shared secret
     * @param checksum   supplied checksum
     * @return whether the supplied value matches exactly
     */
    public static boolean checkChecksum(Map<String, String> parameters, String secretKey, String checksum) {
        return calculateChecksum(parameters, secretKey).equals(checksum);
    }
}

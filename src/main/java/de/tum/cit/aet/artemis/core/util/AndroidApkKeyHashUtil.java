package de.tum.cit.aet.artemis.core.util;

public class AndroidApkKeyHashUtil {

    /**
     * Generates the Android APK key hash from the provided SHA-256 certificate fingerprint.
     * See <a href="https://developer.android.com/identity/sign-in/credential-manager#verify-origin">this section in the Android passkey documentation</a>
     *
     * @param fingerprint The SHA-256 certificate fingerprint in hexadecimal format.
     * @return The Android APK key hash in the format "android:apk-key-hash:<base64-encoded-hash>".
     */
    public static String getHashFromFingerprint(String fingerprint) {
        // Remove all colons from the fingerprint
        String noColon = fingerprint.replace(":", "");
        int len = noColon.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(noColon.charAt(i), 16) << 4) + Character.digit(noColon.charAt(i + 1), 16));
        }

        // Use the URL-safe Base64 encoder without padding
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return "android:apk-key-hash:" + encoded;
    }

}

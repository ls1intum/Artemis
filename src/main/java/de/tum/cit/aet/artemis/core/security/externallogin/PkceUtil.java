package de.tum.cit.aet.artemis.core.security.externallogin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * PKCE (RFC 7636) S256 helper for the external-login code exchange.
 */
public final class PkceUtil {

    private PkceUtil() {
    }

    /**
     * Computes the S256 code challenge for a verifier: {@code base64url(SHA-256(verifier))} without padding.
     *
     * @param codeVerifier the PKCE code verifier
     * @return the S256 code challenge
     */
    public static String s256Challenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Constant-time comparison of the challenge derived from the verifier against the expected challenge.
     *
     * @param codeVerifier          the PKCE code verifier presented at exchange
     * @param expectedCodeChallenge the stored code challenge
     * @return {@code true} if the verifier matches the challenge
     */
    public static boolean matches(String codeVerifier, String expectedCodeChallenge) {
        if (codeVerifier == null || expectedCodeChallenge == null) {
            return false;
        }
        String actual = s256Challenge(codeVerifier);
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), expectedCodeChallenge.getBytes(StandardCharsets.US_ASCII));
    }
}

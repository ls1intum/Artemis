package de.tum.cit.aet.artemis.core.security.externallogin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * PKCE (RFC 7636) S256 helper for the external-login code exchange.
 */
public final class PkceUtil {

    /**
     * An S256 code challenge is {@code base64url(SHA-256(verifier))} without padding: a SHA-256 digest is 32 bytes,
     * which base64url-encodes to exactly 43 characters from the URL-safe alphabet.
     */
    private static final Pattern S256_CODE_CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    /**
     * RFC 7636 code verifier: 43-128 characters from the unreserved set {@code [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"}.
     */
    private static final Pattern CODE_VERIFIER = Pattern.compile("^[A-Za-z0-9._~-]{43,128}$");

    private PkceUtil() {
    }

    /**
     * Validates that a code challenge has the RFC 7636 S256 shape (43 base64url characters, no padding), so malformed
     * challenges are rejected before a JWT-bearing code is minted and stored.
     *
     * @param codeChallenge the PKCE code challenge to validate
     * @return {@code true} if the challenge is a well-formed S256 challenge
     */
    public static boolean isValidS256Challenge(@Nullable String codeChallenge) {
        return codeChallenge != null && S256_CODE_CHALLENGE.matcher(codeChallenge).matches();
    }

    /**
     * Validates that a code verifier has the RFC 7636 shape (43-128 unreserved characters), so blank, overlong or
     * otherwise malformed verifiers are rejected before any hashing or repository work.
     *
     * @param codeVerifier the PKCE code verifier to validate
     * @return {@code true} if the verifier is a well-formed RFC 7636 verifier
     */
    public static boolean isValidCodeVerifier(@Nullable String codeVerifier) {
        return codeVerifier != null && CODE_VERIFIER.matcher(codeVerifier).matches();
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

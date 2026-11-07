package de.tum.cit.aet.artemis.localvc.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;

/**
 * Reads and compares the credentials a git client presents with HTTP Basic authentication.
 * <p>
 * Shared by the services that authenticate or attribute a git request, so that all of them read the header the same way.
 */
final class LocalVCCredentials {

    /** A class of static helpers that is never instantiated. */
    private LocalVCCredentials() {
    }

    /**
     * The credentials of a Basic authorization header.
     *
     * @param username the user name, which may be empty
     * @param password the password or token, which may be empty
     */
    record UsernameAndPassword(@NonNull String username, @NonNull String password) {
    }

    /**
     * Extracts the username and password from a Basic Authorization header.
     *
     * @param authorizationHeader the authorization header containing Basic credentials
     * @return a {@link UsernameAndPassword} object with the extracted username and password
     * @throws LocalVCAuthException if the header is missing, invalid, or improperly formatted
     */
    static UsernameAndPassword extractUsernameAndPassword(String authorizationHeader) throws LocalVCAuthException {
        if (authorizationHeader == null) {
            throw new LocalVCAuthException("No authorization header provided", true);
        }
        String[] basicAuthCredentialsEncoded = authorizationHeader.split(" ");

        if (basicAuthCredentialsEncoded.length < 2 || !"Basic".equalsIgnoreCase(basicAuthCredentialsEncoded[0])) {
            throw new LocalVCAuthException("Invalid authorization header format");
        }

        // Decode the Base64-encoded credentials (username:password). The scheme name above is case-insensitive and the credentials are UTF-8, whatever the default charset of the
        // JVM is.
        String basicAuthCredentials;
        try {
            basicAuthCredentials = new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException _) {
            throw new LocalVCAuthException("Invalid Base64 encoding in authorization header");
        }

        int separatorIndex = basicAuthCredentials.indexOf(":");

        if (separatorIndex == -1) {
            throw new LocalVCAuthException("Missing colon separator in Basic auth credentials");
        }
        String username = basicAuthCredentials.substring(0, separatorIndex);
        String password = basicAuthCredentials.substring(separatorIndex + 1);

        return new UsernameAndPassword(username, password);
    }

    /**
     * Returns whether the provided secret matches the expected one. This method is in comparison to
     * {@link Objects#equals(Object, Object)} is based on {@link MessageDigest#isEqual(byte[], byte[])} to
     * guarantee nearly time-constant comparison.
     *
     * @param expectedSecret expected secret. May be null to allow for nullable types to be used with this.
     *                           Since {@code providedSecret} is never {@code null}, this will result in {@code false.}
     * @param providedSecret the value that was provided for the secret.
     * @return the result of {@code Objects.equals(expectedSecret, providedSecret)} but with a time-constant comparison.
     * @implNote The expected secret is allowed to be null to be compatible with {@link Objects#equals(Object, Object)}.
     *           Normally, a missing secret should raise some warning. However, the current usage of this method never passes
     *           {@code null} for {@code providedSecret}. Therefore, the result for such a case is always {@code false}.
     *           To reaffirm this, the {@code providedSecret} is expected to be non-null, making it obvious,
     *           that a {@code expectedSecret == null} will always result in {@code false}.
     */
    static boolean secretMatches(@Nullable String expectedSecret, @NonNull String providedSecret) {
        if (expectedSecret == null) {
            return false;
        }
        final var expectedBytes = expectedSecret.getBytes(StandardCharsets.UTF_8);
        final var actualBytes = providedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}

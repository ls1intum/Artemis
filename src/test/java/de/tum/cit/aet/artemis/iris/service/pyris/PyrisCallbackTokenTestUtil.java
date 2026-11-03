package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;

import javax.crypto.SecretKey;

import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import io.jsonwebtoken.security.Keys;

/**
 * Builds a real {@link PyrisCallbackTokenService} for unit tests, without a Spring context.
 */
public final class PyrisCallbackTokenTestUtil {

    private PyrisCallbackTokenTestUtil() {
    }

    /**
     * @return a token service signing with a fresh random key
     */
    public static PyrisCallbackTokenService tokenService() {
        byte[] keyBytes = new byte[64];
        new SecureRandom().nextBytes(keyBytes);
        return tokenService(Keys.hmacShaKeyFor(keyBytes));
    }

    /**
     * @param key the key the service signs and verifies with
     * @return a token service using that key and the default validities
     */
    public static PyrisCallbackTokenService tokenService(SecretKey key) {
        TokenProvider tokenProvider = mock(TokenProvider.class);
        when(tokenProvider.deriveSigningKey(anyString())).thenReturn(key);
        return new PyrisCallbackTokenService(tokenProvider, 12, 10800);
    }
}

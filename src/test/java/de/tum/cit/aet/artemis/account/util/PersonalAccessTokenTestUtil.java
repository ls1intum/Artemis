package de.tum.cit.aet.artemis.account.util;

import static de.tum.cit.aet.artemis.core.config.Constants.PERSONAL_ACCESS_TOKEN_PREFIX;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.account.domain.PersonalAccessToken;
import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;

/**
 * Stores personal access tokens with a known secret, so that tests can call the REST API with them.
 */
public final class PersonalAccessTokenTestUtil {

    private PersonalAccessTokenTestUtil() {
    }

    /**
     * A stored token together with its secret, which the database does not keep.
     */
    public record SavedToken(PersonalAccessToken token, String secret) {

        /**
         * @return the value of the Authorization header that authenticates with this token
         */
        public String bearer() {
            return "Bearer " + secret;
        }
    }

    /**
     * Stores a token.
     *
     * @param repository where tokens are stored
     * @param userId     the owner
     * @param scopes     the scopes the token holds
     * @param lifetime   how long the token is valid from now; negative for one that has already expired
     * @return the stored token and its secret
     */
    public static SavedToken saveToken(PersonalAccessTokenRepository repository, long userId, Set<AgentScope> scopes, Duration lifetime) {
        String secret = PERSONAL_ACCESS_TOKEN_PREFIX + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        String joinedScopes = scopes.stream().map(AgentScope::getValue).collect(Collectors.joining(","));
        var token = new PersonalAccessToken(userId, "test token", secret.substring(0, PERSONAL_ACCESS_TOKEN_PREFIX.length() + 4), sha256(secret), joinedScopes, now,
                now.plus(lifetime));
        return new SavedToken(repository.save(token), secret);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

package de.tum.cit.aet.artemis.account.service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.config.OIDCEnabled;
import de.tum.cit.aet.artemis.account.security.RandomUtil;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/**
 * Service to store and exchange single-use OIDC codes for JWT tokens bound to PKCE S256 code challenges.
 */
@Service
@Lazy
@Conditional(OIDCEnabled.class)
public class OIDCExchangeCodeService {

    // RFC 7636 Section 4.1: code_verifier = 43-128 characters from [A-Za-z0-9-._~]
    private static final Pattern CODE_VERIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-._~]{43,128}$");

    // RFC 7636 Section 4.2: S256 code_challenge is 32 bytes SHA-256 in Base64URL without padding = exactly 43 chars
    private static final Pattern CODE_CHALLENGE_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{43}$");

    // The jwt token is stored in cache for only 5 minutes till collected
    private static final Duration EXCHANGE_CODE_TIME_TO_LIVE = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(OIDCExchangeCodeService.class);

    public record ExchangeCodeEntry(String jwtToken, String codeChallenge) implements Serializable {
    }

    private final DistributedDataProvider distributedDataProvider;

    private DistributedMap<String, ExchangeCodeEntry> codeToEntryMap;

    public OIDCExchangeCodeService(DistributedDataProvider distributedDataProvider) {
        this.distributedDataProvider = distributedDataProvider;
    }

    @PostConstruct
    public void init() {
        this.codeToEntryMap = distributedDataProvider.getExpiringMap("oidcExchangeCodes", EXCHANGE_CODE_TIME_TO_LIVE);
    }

    /**
     * Checks whether the provided code challenge matches the RFC 7636 format requirements.
     *
     * @param codeChallenge the code challenge string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCodeChallenge(String codeChallenge) {
        return codeChallenge != null && CODE_CHALLENGE_PATTERN.matcher(codeChallenge).matches();
    }

    /**
     * Validates and stores the JWT token bound to the PKCE S256 code challenge in a single entry.
     *
     * @param jwtToken      The JWT token to store.
     * @param codeChallenge The PKCE S256 code challenge sent by the client.
     * @return A random single-use exchange code, or null if inputs are invalid.
     */
    public String storeJwtAndGenerateCode(String jwtToken, String codeChallenge) {
        if (jwtToken == null || jwtToken.isBlank()) {
            log.warn("Cannot store OIDC exchange code: JWT token is null or blank");
            return null;
        }
        if (!isValidCodeChallenge(codeChallenge)) {
            log.warn("Cannot store OIDC exchange code: Code challenge format does not meet RFC 7636 requirements");
            return null;
        }
        try {
            String code = RandomUtil.generateResetKey();
            codeToEntryMap.put(code, new ExchangeCodeEntry(jwtToken, codeChallenge), EXCHANGE_CODE_TIME_TO_LIVE);
            return code;
        }
        catch (Exception e) {
            log.error("Failed to store OIDC exchange code in distributed cache", e);
            return null;
        }
    }

    /**
     * Atomically validates the PKCE code_verifier against the stored code_challenge and consumes the code entry.
     *
     * @param code         The exchange code.
     * @param codeVerifier The PKCE code verifier string from the client.
     * @return The JWT token if the code and verifier are valid, or null otherwise.
     */
    public String redeemCode(String code, String codeVerifier) {
        if (code == null || code.isBlank()) {
            log.warn("Cannot redeem OIDC exchange code: Code is null or blank");
            return null;
        }
        if (codeVerifier == null || !CODE_VERIFIER_PATTERN.matcher(codeVerifier).matches()) {
            log.warn("Cannot redeem OIDC exchange code: Code verifier format does not meet RFC 7636 requirements");
            return null;
        }
        try {
            ExchangeCodeEntry entry = codeToEntryMap.get(code);
            if (entry == null) {
                log.warn("Cannot redeem OIDC exchange code: Code not found or already expired");
                return null;
            }
            if (entry.codeChallenge() == null || entry.jwtToken() == null) {
                log.warn("Cannot redeem OIDC exchange code: Corrupted cache entry");
                return null;
            }
            String computedChallenge = computeSHA256Challenge(codeVerifier);
            // Constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(entry.codeChallenge().getBytes(StandardCharsets.US_ASCII), computedChallenge.getBytes(StandardCharsets.US_ASCII))) {
                log.warn("Cannot redeem OIDC exchange code: PKCE code verifier does not match challenge");
                return null;
            }

            // Atomically remove the entry (CAS) to ensure single-use
            if (codeToEntryMap.remove(code, entry)) {
                return entry.jwtToken();
            }
            else {
                log.warn("Cannot redeem OIDC exchange code: Code was already redeemed");
                return null;
            }
        }
        catch (Exception e) {
            log.error("Failed to redeem OIDC exchange code from distributed cache", e);
            return null;
        }
    }

    /**
     * Computes the S256 code_challenge from the code_verifier according to RFC 7636:
     * code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier))) without padding.
     *
     * @param codeVerifier The PKCE code verifier string.
     * @return Base64Url-encoded SHA-256 digest string.
     */
    public static String computeSHA256Challenge(String codeVerifier) {
        if (codeVerifier == null) {
            throw new IllegalArgumentException("codeVerifier cannot be null");
        }
        try {
            byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}

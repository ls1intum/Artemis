package de.tum.cit.aet.artemis.iris.service.pyris;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJobType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/**
 * Issues and verifies the tokens Pyris presents when it reports back on a run.
 * <p>
 * The run id names a run and is not a secret: it is part of callback URLs, logs and messages to the client. The token is
 * the credential. It is a JWT signed by Artemis that names one run and its kind and expires, so Artemis needs to store
 * nothing to check it, and a token for one run is useless for any other run.
 * <p>
 * The key is derived from the session key for this purpose alone, so a session token never verifies as a callback token
 * and the reverse. It is not the secret shared with Pyris, because Pyris could then issue tokens itself.
 * <p>
 * Neither the issued token nor a presented one may be logged. This class is excluded from the debug logging of
 * arguments and return values in {@link de.tum.cit.aet.artemis.core.config.LoggingAspect} for that reason.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisCallbackTokenService {

    static final String KEY_PURPOSE = "artemis:iris:pyris-callback-token:v1";

    static final String ISSUER = "artemis";

    static final String AUDIENCE = "pyris-callback";

    static final String JOB_TYPE_CLAIM = "job";

    private static final String ALGORITHM = Jwts.SIG.HS512.getId();

    /**
     * Interactive runs keep their job entry alive only while status updates arrive, so the entry is what ends them. The
     * expiry only bounds how long a token that leaked from Pyris could be used.
     */
    static final Duration INTERACTIVE_VALIDITY = Duration.ofHours(6);

    private final TokenProvider tokenProvider;

    private final Duration ingestionValidity;

    @Nullable
    private SecretKey key;

    /**
     * @param tokenProvider                 derives the signing key from the session key
     * @param ingestionAbsoluteTimeoutHours the longest an ingestion run may take before it is failed
     * @param ingestionJobTimeoutSeconds    how long an ingestion job stays in the job map
     */
    public PyrisCallbackTokenService(TokenProvider tokenProvider, @Value("${artemis.iris.ingestion.absolute-timeout-hours:12}") int ingestionAbsoluteTimeoutHours,
            @Value("${artemis.iris.jobs.ingestion.timeout:10800}") int ingestionJobTimeoutSeconds) {
        this.tokenProvider = tokenProvider;
        // A lecture ingestion callback is still accepted from the processing state until the absolute timeout fails the run, which can be long after the
        // job map entry expired. The token must outlive both, or a legitimate late callback would be rejected.
        Duration longestRun = Duration.ofHours(ingestionAbsoluteTimeoutHours).compareTo(Duration.ofSeconds(ingestionJobTimeoutSeconds)) >= 0
                ? Duration.ofHours(ingestionAbsoluteTimeoutHours)
                : Duration.ofSeconds(ingestionJobTimeoutSeconds);
        this.ingestionValidity = longestRun.plusHours(1);
    }

    private SecretKey getKey() {
        if (key == null) {
            key = tokenProvider.deriveSigningKey(KEY_PURPOSE);
        }
        return key;
    }

    /**
     * @param type the kind of run
     * @return how long a token for this kind of run stays valid
     */
    Duration validity(PyrisJobType type) {
        return type.isIngestion() ? ingestionValidity : INTERACTIVE_VALIDITY;
    }

    /**
     * Issues the token for a registered run.
     *
     * @param job the run, as stored in the job map; null when no such run is registered, which fails
     * @return the signed token
     */
    public String issue(@Nullable PyrisJob job) {
        return issue(job, null);
    }

    /**
     * Issues the token for a registered run. The issue and expiry times are fixed before {@code beforeSigning} runs and the
     * token is signed with exactly these times, so a caller that has to keep something alive at least as long as the token
     * can do so before the token exists, and no pause in between can make the token outlive it.
     *
     * @param job           the run, as stored in the job map; null when no such run is registered, which fails
     * @param beforeSigning receives the expiry the token will carry, before it is signed; may be null
     * @return the signed token
     * @throws IllegalStateException if no run is given
     */
    public String issue(@Nullable PyrisJob job, @Nullable Consumer<Instant> beforeSigning) {
        if (job == null) {
            throw new IllegalStateException("Cannot issue a callback token for a run that is not registered");
        }
        PyrisJobType type = PyrisJobType.of(job);
        // A JWT carries times in whole seconds; truncating here keeps the expiry handed out equal to the one in the token.
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(validity(type));
        if (beforeSigning != null) {
            beforeSigning.accept(expiresAt);
        }
        // @formatter:off
        return Jwts.builder()
            .issuer(ISSUER)
            .audience().add(AUDIENCE).and()
            .subject(job.jobId())
            .claim(JOB_TYPE_CLAIM, type.claimValue())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(getKey(), Jwts.SIG.HS512)
            .compact();
        // @formatter:on
    }

    /**
     * Verifies a token presented by Pyris: signature with the callback key and algorithm, issuer, audience, expiry, and that
     * it names a run and a known kind of run.
     *
     * @param token the presented token
     * @return the run and kind the token was issued for, or empty if the token is not valid
     */
    public Optional<VerifiedCallback> verify(String token) {
        Jws<Claims> jws;
        try {
            jws = Jwts.parser().verifyWith(getKey()).requireIssuer(ISSUER).requireAudience(AUDIENCE).build().parseSignedClaims(token);
        }
        catch (JwtException | IllegalArgumentException e) {
            // Deliberately without the token and without the exception, whose message can quote token content.
            return Optional.empty();
        }
        if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
            return Optional.empty();
        }
        Claims claims = jws.getPayload();
        String runId = claims.getSubject();
        Object typeClaim = claims.get(JOB_TYPE_CLAIM);
        if (runId == null || runId.isBlank() || !(typeClaim instanceof String typeValue) || claims.getExpiration() == null) {
            return Optional.empty();
        }
        return PyrisJobType.fromClaimValue(typeValue).map(type -> new VerifiedCallback(runId, type));
    }

    /**
     * What a valid callback token proves.
     *
     * @param runId the run the token was issued for
     * @param type  the kind of that run
     */
    public record VerifiedCallback(String runId, PyrisJobType type) {
    }
}

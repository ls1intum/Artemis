package de.tum.cit.aet.artemis.iris.service.pyris;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

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
import io.jsonwebtoken.JwtBuilder;
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

    static final String INVOCATION_CLAIM = "inv";

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
     * @param ingestionAbsoluteTimeoutHours the longest one phase of an ingestion run (transcription or ingestion) may take before it is failed
     * @param ingestionJobTimeoutSeconds    how long an ingestion job stays in the job map
     */
    public PyrisCallbackTokenService(TokenProvider tokenProvider, @Value("${artemis.iris.ingestion.absolute-timeout-hours:12}") int ingestionAbsoluteTimeoutHours,
            @Value("${artemis.iris.jobs.ingestion.timeout:10800}") int ingestionJobTimeoutSeconds) {
        this.tokenProvider = tokenProvider;
        // A lecture ingestion callback is still accepted from the processing state until the absolute timeout fails the run, which can be long after the
        // job map entry expired. The token must outlive both, or a legitimate late callback would be rejected. A run keeps its token from transcription
        // into ingestion, and that transition restarts the absolute timeout, so one run can take the timeout twice.
        Duration bothPhases = Duration.ofHours(ingestionAbsoluteTimeoutHours).multipliedBy(2);
        Duration jobMapTimeout = Duration.ofSeconds(ingestionJobTimeoutSeconds);
        Duration longestRun = bothPhases.compareTo(jobMapTimeout) >= 0 ? bothPhases : jobMapTimeout;
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
     * Issues the token for a registered run. It names the run, its kind and, if the run has one, its invocation.
     *
     * @param job the run, as stored in the job map; null when no such run is registered, which fails
     * @return the signed token
     * @throws IllegalStateException if no run is given
     */
    public String issue(@Nullable PyrisJob job) {
        if (job == null) {
            throw new IllegalStateException("Cannot issue a callback token for a run that is not registered");
        }
        PyrisJobType type = PyrisJobType.of(job);
        Instant issuedAt = Instant.now();
        // @formatter:off
        JwtBuilder builder = Jwts.builder()
            .issuer(ISSUER)
            .audience().add(AUDIENCE).and()
            .subject(job.jobId())
            .claim(JOB_TYPE_CLAIM, type.claimValue())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plus(validity(type))));
        // @formatter:on
        if (job.invocationId() != null) {
            builder.claim(INVOCATION_CLAIM, job.invocationId());
        }
        return builder.signWith(getKey(), Jwts.SIG.HS512).compact();
    }

    /**
     * Verifies a token presented by Pyris: signature with the callback key and algorithm, issuer, audience, issue time,
     * expiry, and that it names a run and a known kind of run.
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
        Object invocationClaim = claims.get(INVOCATION_CLAIM);
        if (runId == null || runId.isBlank() || !(typeClaim instanceof String typeValue) || claims.getIssuedAt() == null || claims.getExpiration() == null
                || (invocationClaim != null && !(invocationClaim instanceof String))) {
            return Optional.empty();
        }
        return PyrisJobType.fromClaimValue(typeValue).map(type -> new VerifiedCallback(runId, type, (String) invocationClaim));
    }

    /**
     * What a valid callback token proves.
     *
     * @param runId        the run the token was issued for
     * @param type         the kind of that run
     * @param invocationId the invocation of that run, or null for a run without one
     */
    public record VerifiedCallback(String runId, PyrisJobType type, @Nullable String invocationId) {
    }
}

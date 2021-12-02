package de.tum.in.www1.artemis.security.jwt;

import java.security.Key;
import java.util.Date;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_ATHENE;

/**
 * This component is used to create a jwt token for the tutor-assessment tracking.
 * <p>
 * The token is used to verify, that the tracking request belongs to the corresponding result that
 * is assessed
 */
@Component
@Profile(SPRING_PROFILE_ATHENE)
public class AtheneTrackingTokenProvider {

    private final Logger log = LoggerFactory.getLogger(AtheneTrackingTokenProvider.class);

    @Value("${artemis.athene.base64-secret}")
    private String BASE64_SECRET;

    private Key key;

    @Value("${artemis.athene.token-validity-in-seconds}")
    private int TOKEN_VALIDITY_IN_SECONDS;

    private int tokenValidityInMilliseconds;

    /**
     * initializes the token provider based on the yml config file
     */
    @PostConstruct
    private void init() {
        byte[] keyBytes = Decoders.BASE64.decode(BASE64_SECRET);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.tokenValidityInMilliseconds = 1000 * TOKEN_VALIDITY_IN_SECONDS;
    }

    /**
     * Create JWT Token to communicate with the Athene Tracking Component for a given Result.
     *
     * @param result Result Object
     * @return JWT Token
     */
    public String createToken(Result result) {
        log.trace("Create Athene Tracking Token for Result {}", result);

        long now = (new Date()).getTime();
        Date validity = new Date(now + this.tokenValidityInMilliseconds);

        return Jwts.builder().claim("result_id", result.getId()).signWith(key, SignatureAlgorithm.HS256).setExpiration(validity).compact();
    }

    /**
     * Create JWT Token to communicate with the Athene Tracking Component and add it as HTTP
     * Header.
     *
     * @param bodyBuilder HttpResponse BodyBuilder
     * @param result Result Object
     */
    public void addTokenToResponseEntity(ResponseEntity.BodyBuilder bodyBuilder, Result result) {
        bodyBuilder.header("X-Athene-Tracking-Authorization", createToken(result));
    }
}

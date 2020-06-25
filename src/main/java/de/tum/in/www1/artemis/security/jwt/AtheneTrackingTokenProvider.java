package de.tum.in.www1.artemis.security.jwt;

import java.security.Key;
import java.util.Date;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.TextSubmission;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
@Profile("athene")
public class AtheneTrackingTokenProvider {

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
     * Create JWT Token to communicate with the Athene Tracking Component for a given Submission.
     * @param submission TextSubmission Object
     * @return JWT Token
     */
    public String createToken(TextSubmission submission) {
        long now = (new Date()).getTime();
        Date validity = new Date(now + this.tokenValidityInMilliseconds);

        return Jwts.builder().claim("submission_id", submission.getId()).signWith(key, SignatureAlgorithm.HS256).setExpiration(validity).compact();
    }

    /**
     * Create JWT Token to communicate with the Athene Tracking Component and add it as HTTP Header.
     *
     * @param bodyBuilder HttpResponse BodyBuilder
     * @param submission TextSubmission Object
     */
    public void addTokenToResponseEntity(ResponseEntity.BodyBuilder bodyBuilder, TextSubmission submission) {
        bodyBuilder.header("X-Athene-Tracking-Authorization", createToken(submission));
    }
}

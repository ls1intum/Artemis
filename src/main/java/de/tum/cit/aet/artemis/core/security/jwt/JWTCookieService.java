package de.tum.cit.aet.artemis.core.security.jwt;

import static de.tum.cit.aet.artemis.core.config.Constants.JWT_COOKIE_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class JWTCookieService {

    private static final Logger log = LoggerFactory.getLogger(JWTCookieService.class);

    private static final String DEVELOPMENT_PROFILE = "dev";

    private final TokenProvider tokenProvider;

    /**
     * Whether the JWT cookie carries the {@code Secure} attribute, so browsers only send it over HTTPS.
     * <p>
     * Secure by default outside the development profile. {@code jhipster.security.authentication.jwt.cookie-secure}
     * overrides that, which a deployment reached over plain HTTP needs (a {@code kubectl port-forward}, for example),
     * because a browser silently drops a {@code Secure} cookie on such a connection and nobody can stay logged in.
     */
    private final boolean secureCookie;

    /**
     * Absolute ceiling on a session, measured from the login. Read from the same property as
     * {@link de.tum.cit.aet.artemis.core.config.SecurityConfiguration}, which rejects an unusable value at startup, so
     * this copy is known good by the time any cookie is built.
     */
    private final long maxSessionLifetimeInSeconds;

    public JWTCookieService(TokenProvider tokenProvider, Environment environment, ArtemisProperties artemisProperties,
            @Value("${artemis.user-management.max-session-lifetime-in-seconds:2592000}") long maxSessionLifetimeInSeconds) {
        this.tokenProvider = tokenProvider;
        this.maxSessionLifetimeInSeconds = maxSessionLifetimeInSeconds;

        Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        Boolean configuredSecureCookie = artemisProperties.getSecurity().getAuthentication().getJwt().getCookieSecure();
        this.secureCookie = configuredSecureCookie != null ? configuredSecureCookie : !activeProfiles.contains(DEVELOPMENT_PROFILE);
        if (!this.secureCookie && !activeProfiles.contains(DEVELOPMENT_PROFILE)) {
            log.warn("JWT cookies are issued without the Secure attribute because jhipster.security.authentication.jwt.cookie-secure is false. "
                    + "Browsers will then send the session cookie over plain HTTP, so only use this for a local deployment that is not reachable over the network.");
        }
    }

    /**
     * Builds the cookie containing the jwt for a login
     *
     * @param rememberMe boolean used to determine the duration of the jwt.
     * @return the login ResponseCookie containing the JWT
     */
    public ResponseCookie buildLoginCookie(boolean rememberMe) {
        return buildLoginCookie(rememberMe, null);
    }

    /**
     * Builds the cookie containing the jwt for a login
     *
     * @param rememberMe boolean used to determine the duration of the jwt.
     * @param tool       the tool claim in the jwt
     * @return the login ResponseCookie containing the JWT
     */
    public ResponseCookie buildLoginCookie(boolean rememberMe, @Nullable ToolTokenType tool) {
        // The flag is passed on rather than only converted into a duration: it is what marks the session extendable, and
        // dropping it here would leave every production login without the claim, so no session would ever be extended.
        //
        // Clipped to the absolute ceiling here, not only while rotating. JWTFilter caps a rotated token at
        // issuedAt + ceiling, but refusing a rotation cannot shorten the token already in the browser: with a
        // remember-me validity of seven days and a ceiling of one day, the login handed out a seven-day token and the
        // first refused rotation left it usable for the remaining six.
        long duration = Math.min(tokenProvider.getTokenValidity(rememberMe), Math.multiplyExact(maxSessionLifetimeInSeconds, 1000));
        String jwt = tokenProvider.createToken(SecurityContextHolder.getContext().getAuthentication(), duration, tool, rememberMe);
        return buildJWTCookie(jwt, Duration.of(duration, ChronoUnit.MILLIS));
    }

    /**
     * Builds a cookie with the tool claim in the jwt
     *
     * @param duration the duration of the cookie in milliseconds and the jwt
     * @param tool     the tool claim in the jwt
     * @return the login ResponseCookie containing the JWT
     */
    public ResponseCookie buildLoginCookie(long duration, @Nullable ToolTokenType tool) {
        String jwt = tokenProvider.createToken(SecurityContextHolder.getContext().getAuthentication(), duration, tool);
        return buildJWTCookie(jwt, Duration.of(duration, ChronoUnit.MILLIS));
    }

    /**
     * Builds the cookie containing the jwt for a login
     *
     * @param rotatedJwtToken        with the updated values
     * @param durationInMilliseconds of the cookie in milliseconds and the jwt
     * @return the login {@link ResponseCookie} containing the JWT
     */
    public ResponseCookie buildRotatedCookie(String rotatedJwtToken, long durationInMilliseconds) {
        return buildJWTCookie(rotatedJwtToken, Duration.of(durationInMilliseconds, ChronoUnit.MILLIS));
    }

    /**
     * Builds the cookie containing the jwt for a logout and sets it in the response
     *
     * @return the logout ResponseCookie
     */
    public ResponseCookie buildLogoutCookie() {
        return buildJWTCookie("", Duration.ZERO);
    }

    /**
     * Builds the cookie containing the jwt
     *
     * @param jwt      the token that will be used as the cookie's value
     * @param duration the validity of the cookie
     * @return the response cookie that should be set containing the jwt
     */
    private ResponseCookie buildJWTCookie(String jwt, Duration duration) {
        return ResponseCookie.from(JWT_COOKIE_NAME, jwt).httpOnly(true) // Must be httpOnly
                .sameSite("Lax") // Must be Lax to allow navigation links to Artemis to work
                .secure(secureCookie) // Secure unless explicitly disabled for a plain-HTTP local deployment
                .path("/") // Must be "/" to be sent in ALL request
                .maxAge(duration) // Duration should match the duration of the jwt
                .build(); // Build cookie
    }
}

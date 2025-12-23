package de.tum.cit.aet.artemis.core.security.jwt;

import static de.tum.cit.aet.artemis.core.config.Constants.JWT_COOKIE_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class JWTCookieService {

    private static final String DEVELOPMENT_PROFILE = "dev";

    private final TokenProvider tokenProvider;

    private final Environment environment;

    public JWTCookieService(TokenProvider tokenProvider, Environment environment) {
        this.tokenProvider = tokenProvider;
        this.environment = environment;
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
        return buildLoginCookie(tokenProvider.getTokenValidity(rememberMe), tool);
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

        Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isSecure = !activeProfiles.contains(DEVELOPMENT_PROFILE);

        return ResponseCookie.from(JWT_COOKIE_NAME, jwt).httpOnly(true) // Must be httpOnly
                .sameSite("Lax") // Must be Lax to allow navigation links to Artemis to work
                .secure(isSecure) // Must be secure
                .path("/") // Must be "/" to be sent in ALL request
                .maxAge(duration) // Duration should match the duration of the jwt
                .build(); // Build cookie
    }
}

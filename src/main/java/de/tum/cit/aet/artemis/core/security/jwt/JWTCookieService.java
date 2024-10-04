package de.tum.cit.aet.artemis.core.security.jwt;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.jwt.JWTFilter.JWT_COOKIE_NAME;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Profile(PROFILE_CORE)
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
        String jwt = tokenProvider.createToken(SecurityContextHolder.getContext().getAuthentication(), rememberMe);
        Duration duration = Duration.of(tokenProvider.getTokenValidity(rememberMe), ChronoUnit.MILLIS);
        return buildJWTCookie(jwt, duration);
    }

    /**
     * Builds the cookie with Theia flag
     *
     * @param duration the duration of the cookie and the jwt
     * @return the login ResponseCookie containing the JWT
     */
    public ResponseCookie buildTheiaCookie(long duration) {
        String jwt = tokenProvider.createToken(SecurityContextHolder.getContext().getAuthentication(), duration, "THEIA");
        return buildJWTCookie(jwt, Duration.of(duration, ChronoUnit.MILLIS));
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

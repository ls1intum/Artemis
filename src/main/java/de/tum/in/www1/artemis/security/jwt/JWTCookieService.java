package de.tum.in.www1.artemis.security.jwt;

import static de.tum.in.www1.artemis.security.jwt.JWTFilter.JWT_COOKIE_NAME;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;

import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class JWTCookieService {

    private static final String CYPRESS_PROFILE = "cypress";

    private final TokenProvider tokenProvider;

    private final Environment environment;

    public JWTCookieService(TokenProvider tokenProvider, Environment environment) {
        this.tokenProvider = tokenProvider;
        this.environment = environment;
    }

    /**
     * Builds the cookie containing the jwt for a login
     * @param rememberMe boolean used to determine the duration of the jwt.
     * @return the login ResponseCookie contaning the JWT
     */
    public ResponseCookie buildLoginCookie(boolean rememberMe) {
        String jwt = tokenProvider.createToken(SecurityContextHolder.getContext().getAuthentication(), rememberMe);
        Duration duration = Duration.of(tokenProvider.getTokenValidity(rememberMe), ChronoUnit.MILLIS);
        return buildJWTCookie(jwt, duration);
    }

    /**
     * Builds the cookie containing the jwt for a logout and sets it in the response
     * @return the logout ResponseCookie
     */
    public ResponseCookie buildLogoutCookie() {
        return buildJWTCookie("", Duration.ZERO);
    }

    /**
     * Builds the cookie containing the jwt
     * @param jwt      the token that will be used as the cookie's value
     * @param duration the validity of the cookie
     * @return         the response cookie that should be set containing the jwt
     */
    private ResponseCookie buildJWTCookie(String jwt, Duration duration) {

        Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean isCypress = activeProfiles.contains(CYPRESS_PROFILE);

        return ResponseCookie.from(JWT_COOKIE_NAME, jwt).httpOnly(true) // Must be httpOnly
                .sameSite("Lax") // Must be Lax to allow navigation links to Artemis to work
                .secure(!isCypress) // Must be secure - TODO - Remove cypress workaround once cypress uses https
                .path("/") // Must be "/" to be sent in ALL request
                .maxAge(duration) // Duration should match the duration of the jwt
                .build(); // Build cookie
    }
}

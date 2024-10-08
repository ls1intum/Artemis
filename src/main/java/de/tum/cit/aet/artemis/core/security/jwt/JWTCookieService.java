package de.tum.cit.aet.artemis.core.security.jwt;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.jwt.JWTFilter.JWT_COOKIE_NAME;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Profile(PROFILE_CORE)
@Service
public class JWTCookieService {

    private final TokenProvider tokenProvider;

    public JWTCookieService(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
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
                .sameSite("None") // Must be None to allow cross-site requests to Artemis from the VS Code plugin
                .secure(true) // Must be secure to allow sameSite=None
                .path("/") // Must be "/" to be sent in ALL request
                .maxAge(duration) // Duration should match the duration of the jwt
                .build(); // Build cookie
    }
}

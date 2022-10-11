package de.tum.in.www1.artemis.web.rest;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.UserNotActivatedException;
import de.tum.in.www1.artemis.security.jwt.JWTFilter;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.CaptchaRequiredException;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

/**
 * Controller to authenticate users.
 */
@RestController
@RequestMapping("/api")
public class UserJWTController {

    private static final Logger log = LoggerFactory.getLogger(UserJWTController.class);

    private final TokenProvider tokenProvider;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final Optional<SAML2Service> saml2Service;

    public UserJWTController(TokenProvider tokenProvider, AuthenticationManagerBuilder authenticationManagerBuilder, Optional<SAML2Service> saml2Service) {
        this.tokenProvider = tokenProvider;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.saml2Service = saml2Service;
    }

    /**
     * Authorizes a User
     * @param loginVM       user credentials View Mode
     * @param userAgent     User Agent
     * @param response      HTTP response
     * @return the ResponseEntity with status 200 (ok), 401 (unauthorized) or 403 (Captcha required)
     */
    @PostMapping("/authenticate")
    public ResponseEntity<Void> authorize(@Valid @RequestBody LoginVM loginVM, @RequestHeader("User-Agent") String userAgent, HttpServletResponse response) {

        var username = loginVM.getUsername();
        var password = loginVM.getPassword();
        SecurityUtils.checkUsernameAndPasswordValidity(username, password);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

        try {
            authenticationToken.setDetails(Pair.of("userAgent", userAgent));
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            boolean rememberMe = loginVM.isRememberMe() != null && loginVM.isRememberMe();

            buildAndSetCookie(response, authentication, rememberMe);
            return ResponseEntity.ok().build();
        }
        catch (CaptchaRequiredException ex) {
            log.warn("CAPTCHA required in JIRA during login for user {}", loginVM.getUsername());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", ex.getMessage()).build();
        }
    }

    /**
     * Authorizes a User logged in with SAML2
     *
     * @param body      the body of the request. "true" to remember the user.
     * @param response  HTTP response
     * @return the ResponseEntity with status 200 (ok), 401 (unauthorized) or 403 (user not activated)
     */
    @PostMapping("/saml2")
    public ResponseEntity<Void> authorizeSAML2(@RequestBody final String body, HttpServletResponse response) { // TODO: Test
        if (saml2Service.isEmpty()) {
            throw new AccessForbiddenException("SAML2 is disabled");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof final Saml2AuthenticatedPrincipal principal)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        log.debug("SAML2 authentication: {}", authentication);

        try {
            authentication = saml2Service.get().handleAuthentication(principal);
        }
        catch (UserNotActivatedException e) {
            // If the exception is not caught a 401 is returned.
            // That does not match the actual reason and would trigger authentication in the client
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", e.getMessage()).build();
        }

        final boolean rememberMe = Boolean.parseBoolean(body);
        buildAndSetCookie(response, authentication, rememberMe);
        return ResponseEntity.ok().build();
    }

    /**
     * Removes the cookie containing the jwt
     * @param response  HTTP response
     */
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        ResponseCookie responseCookie = ResponseCookie.from(JWTFilter.JWT_COOKIE_NAME, "").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }

    /**
     * Builds the cookie containing the jwt and sets it in the response
     * @param response the body of the request. "true" to remember the user.
     * @param authentication the authentication object for the current user used to create the jwt.
     * @param rememberMe boolean used to create the jwt.
     */
    private void buildAndSetCookie(HttpServletResponse response, Authentication authentication, boolean rememberMe) {
        String jwt = tokenProvider.createToken(authentication, rememberMe);
        // @formatter:off
        ResponseCookie responseCookie = ResponseCookie.from(JWTFilter.JWT_COOKIE_NAME, jwt)
            .httpOnly(true)
            .sameSite("Strict")
            .secure(true)
            //.path("/") management endpoints are not accessible without path "/", but with path "/" logout can't unset cookie anymore because it's not the same path
            //           can we move the management endpoints under api like api/management/**? would fix the issue - or is there a better solution?
            .maxAge(Duration.of(tokenProvider.getTokenValidity(rememberMe), ChronoUnit.MILLIS)) // set max age / expiration? maybe equal to token expiration? how would/does the refresh work?
            .build();
        // @formatter:on
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }
}

package de.tum.in.www1.artemis.web.rest;

import java.util.Optional;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.security.UserNotActivatedException;
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
     * @param loginVM user credentials View Mode
     * @param userAgent User Agent
     */
    @PostMapping("/authenticate")
    public void authorize(@Valid @RequestBody LoginVM loginVM, @RequestHeader("User-Agent") String userAgent, HttpServletResponse response) {

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
        }
        catch (CaptchaRequiredException ex) {
            log.warn("CAPTCHA required in JIRA during login for user {}", loginVM.getUsername());
            // return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", ex.getMessage()).build(); TODO: Throw exception instead
        }
    }

    /**
     * Authorizes a User logged in with SAML2
     *
     * @param body the body of the request. "true" to remember the user.
     */
    @PostMapping("/saml2")
    public void authorizeSAML2(@RequestBody final String body, HttpServletResponse response) { // TODO: Test
        if (saml2Service.isEmpty()) {
            throw new AccessForbiddenException("SAML2 is disabled");
        }

        final boolean rememberMe = Boolean.parseBoolean(body);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof final Saml2AuthenticatedPrincipal principal)) {
            return; // new ResponseEntity<>(HttpStatus.UNAUTHORIZED); TODO throw exception
        }

        log.debug("SAML2 authentication: {}", authentication);

        try {
            authentication = saml2Service.get().handleAuthentication(principal);
        }
        catch (UserNotActivatedException e) {
            // If the exception is not caught a 401 is returned.
            // That does not match the actual reason and would trigger authentication in the client
            return; // ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", e.getMessage()).build(); TODO: Throw exception
        }

        buildAndSetCookie(response, authentication, rememberMe);
    }

    /**
     * Removes the cookie containing the jwt
     */
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        ResponseCookie responseCookie = ResponseCookie.from("jwt", "").maxAge(0).build();
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
        ResponseCookie responseCookie = ResponseCookie.from("jwt", jwt).httpOnly(true).sameSite("Strict").secure(true) // might cause issues in dev - why is this currently being
                                                                                                                       // ignored by the browser?
                // .path("/") // set "/" or not set and leave default ("/api")? "/management" would not be covered, think those are the only non "/api" routes
                // .maxAge() set max age / expiration? maybe equal to token expiration? how would/does the refresh work?
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }
}

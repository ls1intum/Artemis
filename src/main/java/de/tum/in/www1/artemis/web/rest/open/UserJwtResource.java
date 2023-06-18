package de.tum.in.www1.artemis.web.rest.open;

import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
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
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.security.jwt.JWTCookieService;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.CaptchaRequiredException;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

/**
 * REST controller to authenticate users.
 */
@RestController
@RequestMapping("api/public/")
public class UserJwtResource {

    private final Logger log = LoggerFactory.getLogger(UserJwtResource.class);

    private final JWTCookieService jwtCookieService;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final Optional<SAML2Service> saml2Service;

    public UserJwtResource(JWTCookieService jwtCookieService, AuthenticationManagerBuilder authenticationManagerBuilder, Optional<SAML2Service> saml2Service) {
        this.jwtCookieService = jwtCookieService;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.saml2Service = saml2Service;
    }

    /**
     * Authorizes a User
     *
     * @param loginVM   user credentials View Mode
     * @param userAgent User Agent
     * @param response  HTTP response
     * @return the ResponseEntity with status 200 (ok), 401 (unauthorized) or 403 (Captcha required)
     */
    @PostMapping("authenticate")
    @EnforceNothing
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

            ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

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
     * @param body     the body of the request. "true" to remember the user.
     * @param response HTTP response
     * @return the ResponseEntity with status 200 (ok), 401 (unauthorized) or 403 (user not activated)
     */
    @PostMapping("saml2")
    @EnforceNothing
    public ResponseEntity<Void> authorizeSAML2(@RequestBody final String body, HttpServletResponse response) {
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
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        catch (UserNotActivatedException e) {
            // If the exception is not caught a 401 is returned.
            // That does not match the actual reason and would trigger authentication in the client
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", e.getMessage()).build();
        }

        final boolean rememberMe = Boolean.parseBoolean(body);
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        return ResponseEntity.ok().build();
    }

    /**
     * Removes the cookie containing the jwt
     * Is public to make sure a logout can even occur when there is some issue with the authentication
     *
     * @param request  HTTP request
     * @param response HTTP response
     */
    @PostMapping("logout")
    @EnforceNothing
    public void logout(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        request.logout();
        // Logout needs to build the same cookie (secure, httpOnly and sameSite='Lax') or browsers will ignore the header and not unset the cookie
        ResponseCookie responseCookie = jwtCookieService.buildLogoutCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
    }
}

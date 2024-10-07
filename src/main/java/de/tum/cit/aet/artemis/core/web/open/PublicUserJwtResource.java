package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Optional;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.UserNotActivatedException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.service.connectors.SAML2Service;

/**
 * REST controller to authenticate users.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/public/")
public class PublicUserJwtResource {

    private static final Logger log = LoggerFactory.getLogger(PublicUserJwtResource.class);

    private final JWTCookieService jwtCookieService;

    private final AuthenticationManager authenticationManager;

    private final Optional<SAML2Service> saml2Service;

    public PublicUserJwtResource(JWTCookieService jwtCookieService, AuthenticationManager authenticationManager, Optional<SAML2Service> saml2Service) {
        this.jwtCookieService = jwtCookieService;
        this.authenticationManager = authenticationManager;
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
    public ResponseEntity<Map<String, String>> authorize(@Valid @RequestBody LoginVM loginVM, @RequestHeader("User-Agent") String userAgent, HttpServletResponse response) {

        var username = loginVM.getUsername();
        var password = loginVM.getPassword();
        SecurityUtils.checkUsernameAndPasswordValidity(username, password);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

        try {
            authenticationToken.setDetails(Pair.of("userAgent", userAgent));
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            boolean rememberMe = loginVM.isRememberMe() != null && loginVM.isRememberMe();

            ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe);
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

            return ResponseEntity.ok(Map.of("access_token", responseCookie.getValue()));
        }
        catch (BadCredentialsException ex) {
            log.warn("Wrong credentials during login for user {}", loginVM.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
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
            authentication = saml2Service.get().handleAuthentication(authentication, principal);
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
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("logout")
    @EnforceNothing
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        request.logout();
        // Logout needs to build the same cookie (secure, httpOnly and sameSite='Lax') or browsers will ignore the header and not unset the cookie
        ResponseCookie responseCookie = jwtCookieService.buildLogoutCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        return ResponseEntity.ok().build();
    }
}

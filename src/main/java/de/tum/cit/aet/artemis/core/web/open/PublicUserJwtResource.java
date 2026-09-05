package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Optional;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.dto.OIDCCodeExchangeDTO;
import de.tum.cit.aet.artemis.account.exception.UserNotActivatedException;
import de.tum.cit.aet.artemis.account.security.SAML2Service;
import de.tum.cit.aet.artemis.account.service.ArtemisSuccessfulLoginService;
import de.tum.cit.aet.artemis.account.service.OIDCExchangeCodeService;
import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.util.HttpRequestUtils;

/**
 * REST controller to authenticate users.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("authentication/jwt-tokens")
@RestController
@RequestMapping("api/core/public/")
public class PublicUserJwtResource {

    private static final Logger log = LoggerFactory.getLogger(PublicUserJwtResource.class);

    private final JWTCookieService jwtCookieService;

    private final Optional<OIDCExchangeCodeService> oidcExchangeCodeService;

    private final AuthenticationManager authenticationManager;

    private final ArtemisSuccessfulLoginService artemisSuccessfulLoginService;

    private final Optional<SAML2Service> saml2Service;

    public PublicUserJwtResource(JWTCookieService jwtCookieService, AuthenticationManager authenticationManager, ArtemisSuccessfulLoginService artemisSuccessfulLoginService,
            Optional<SAML2Service> saml2Service, Optional<OIDCExchangeCodeService> oidcExchangeCodeService) {
        this.jwtCookieService = jwtCookieService;
        this.oidcExchangeCodeService = oidcExchangeCodeService;
        this.authenticationManager = authenticationManager;
        this.artemisSuccessfulLoginService = artemisSuccessfulLoginService;
        this.saml2Service = saml2Service;
    }

    /**
     * Authenticate a User with username and password. This method is used for the login of users via the Artemis web application.
     *
     * @param loginVM   user credentials View Mode
     * @param userAgent User Agent string from the request header, used to identify the client environment
     * @param tool      optional Tool Token Type to define the scope of the token
     * @param request   HTTP request object, used to get the client environment information
     * @param response  HTTP response object, used to set the JWT cookie
     * @return if successful a map with the access_token information and status 200 (ok). Every credential that the
     *         authentication manager refuses answers an empty body with status 401 (unauthorized) - the same status for all
     *         of them, so that the response does not say which check refused. A malformed request still answers 400 and an
     *         exhausted rate limit 429, both before the credentials are looked at, and a system problem behind
     *         authentication (an unreachable directory, for instance) stays a 500.
     */
    @PostMapping("authenticate")
    @EnforceNothing
    @LimitRequestsPerMinute(type = RateLimitType.AUTHENTICATION)
    public ResponseEntity<Map<String, String>> authenticate(@Valid @RequestBody LoginVM loginVM, @RequestHeader(HttpHeaders.USER_AGENT) String userAgent,
            @RequestParam(name = "tool", required = false) @Nullable ToolTokenType tool, HttpServletRequest request, HttpServletResponse response) {

        var username = loginVM.getUsername();
        var password = loginVM.getPassword();
        SecurityUtils.checkUsernameAndPasswordValidity(username, password);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

        try {
            authenticationToken.setDetails(Pair.of("userAgent", userAgent));
            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            boolean rememberMe = loginVM.isRememberMe() != null && loginVM.isRememberMe();

            ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe, tool);
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
            artemisSuccessfulLoginService.sendLoginEmail(username, AuthenticationMethod.PASSWORD, HttpRequestUtils.getClientEnvironment(request));

            return ResponseEntity.ok(Map.of("access_token", responseCookie.getValue()));
        }
        catch (BadCredentialsException ex) {
            // The message is logged as well: the providers raise this for more than a wrong password - a bot account is
            // refused with it too, and on an LDAP instance so is a login the directory does not know - and without the
            // message those cases are indistinguishable in the log.
            log.warn("Wrong credentials during login for user {}: {}", loginVM.getUsername(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        catch (UserNotActivatedException | ProviderNotFoundException | AccountStatusException ex) {
            // The other ways a login can be refused. ProviderNotFoundException is what an unknown login ends in, because a
            // provider that does not know the user returns null so that the next one can try, and then none produced a
            // result. UserNotActivatedException and the account-status exceptions speak for themselves.
            //
            // Without this, all of them reached the generic exception handler and the caller got a 500. A refused login is
            // not a server fault, and answering it with a server error sends an operator looking for an outage while telling
            // the client nothing it can act on. The reason is logged; the response says no more than "unauthorized", the
            // same as for a wrong password, so it does not become an oracle for which part refused.
            //
            // AuthenticationServiceException is deliberately not caught here: by its contract it means the request could not
            // be processed - a directory that is unreachable, a repository that failed - and answering that with 401 would
            // hide an outage behind "wrong credentials". It stays a 500, which is what it is.
            log.warn("Login for user {} was refused ({}): {}", loginVM.getUsername(), ex.getClass().getSimpleName(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Authorizes a User logged in with SAML2
     *
     * @param body     the body of the request. "true" to remember the user.
     * @param response HTTP response
     * @param request  HTTP request
     * @return the ResponseEntity with status 200 (ok), 401 (unauthorized) or 403 (user not activated)
     */
    @PostMapping("saml2")
    @EnforceNothing
    public ResponseEntity<Void> authorizeSAML2(@RequestBody final String body, HttpServletResponse response, HttpServletRequest request) {
        if (saml2Service.isEmpty()) {
            throw new AccessForbiddenException("SAML2 is disabled");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof final Saml2AuthenticatedPrincipal principal)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        log.debug("SAML2 authentication: {}", authentication);

        try {
            authentication = saml2Service.get().handleAuthentication(authentication, principal, request);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        catch (UserNotActivatedException e) {
            // If the exception is not caught, a 401 is returned.
            // That does not match the actual reason and would trigger authentication in the client
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", e.getMessage()).build();
        }

        final boolean rememberMe = Boolean.parseBoolean(body);
        ResponseCookie responseCookie = jwtCookieService.buildLoginCookie(rememberMe, null);
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        return ResponseEntity.ok().build();
    }

    /**
     * Exchanges a single-use OIDC code and PKCE code_verifier for a JWT authentication token.
     *
     * @param exchangeDTO Request body containing the single-use exchange code and PKCE code_verifier.
     * @return ResponseEntity with the JWT token as plain text and Cache-Control: no-store, or 404 (Not Found) if verification fails.
     */
    @PostMapping("exchange-code")
    @EnforceNothing
    @LimitRequestsPerMinute(type = RateLimitType.AUTHENTICATION)
    public ResponseEntity<String> exchangeCodeToJwtToken(@RequestBody OIDCCodeExchangeDTO exchangeDTO) {
        if (oidcExchangeCodeService.isEmpty() || exchangeDTO == null || exchangeDTO.code() == null || exchangeDTO.codeVerifier() == null) {
            return ResponseEntity.notFound().build();
        }
        String jwtToken = oidcExchangeCodeService.get().redeemCode(exchangeDTO.code(), exchangeDTO.codeVerifier());
        if (jwtToken == null || jwtToken.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.PRAGMA, "no-cache").body(jwtToken);
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
        // Logout needs to build the same cookie (secure, httpOnly and sameSite='Lax'), or browsers will ignore the header and not unset the cookie
        ResponseCookie responseCookie = jwtCookieService.buildLogoutCookie();
        response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        return ResponseEntity.ok().build();
    }
}

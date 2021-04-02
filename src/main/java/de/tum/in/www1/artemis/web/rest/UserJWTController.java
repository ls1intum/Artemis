package de.tum.in.www1.artemis.web.rest;

import java.util.Optional;

import javax.validation.Valid;

import org.slf4j.*;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.security.jwt.*;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.web.rest.errors.*;
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
     * Authorizes an User
     * @param loginVM user credentials View Mode
     * @param userAgent User Agent
     * @return a JWT Token if the authorization is successful
     */
    @PostMapping("/authenticate")
    public ResponseEntity<JWTToken> authorize(@Valid @RequestBody LoginVM loginVM, @RequestHeader("User-Agent") String userAgent) {

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginVM.getUsername(), loginVM.getPassword());

        try {
            authenticationToken.setDetails(Pair.of("userAgent", userAgent));
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            boolean rememberMe = loginVM.isRememberMe() != null && loginVM.isRememberMe();
            String jwt = tokenProvider.createToken(authentication, rememberMe);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
            return new ResponseEntity<>(new JWTToken(jwt), httpHeaders, HttpStatus.OK);
        }
        catch (CaptchaRequiredException ex) {
            log.warn("CAPTCHA required in JIRA during login for user " + loginVM.getUsername());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).header("X-artemisApp-error", ex.getMessage()).build();
        }
    }

    /**
     * Authorizes an User logged in with SAML2
     *
     * @param body the body of the request. "true" to remember the user.
     * @return a JWT Token if the authorization is successful
     */
    @PostMapping("/saml2")
    public ResponseEntity<JWTToken> authorizeSAML2(@RequestBody final String body) {
        if (saml2Service.isEmpty()) {
            throw new AccessForbiddenException("SAML2 is disabled");
        }

        final boolean rememberMe = Boolean.parseBoolean(body);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        log.debug("SAML2 authentication: {}", authentication);

        final Saml2AuthenticatedPrincipal principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();
        authentication = saml2Service.get().handleAuthentication(principal);

        final String jwt = tokenProvider.createToken(authentication, rememberMe);
        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(JWTFilter.AUTHORIZATION_HEADER, "Bearer " + jwt);
        return new ResponseEntity<>(new JWTToken(jwt), httpHeaders, HttpStatus.OK);
    }

    /**
     * Object to return as body in JWT Authentication.
     */
    public static class JWTToken {

        private String idToken;

        JWTToken() {
        }

        JWTToken(String idToken) {
            this.idToken = idToken;
        }

        @JsonProperty("id_token")
        public String getIdToken() {
            return idToken;
        }

        void setIdToken(String idToken) {
            this.idToken = idToken;
        }
    }
}

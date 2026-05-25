package de.tum.cit.aet.artemis.core.security.saml2;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;
import de.tum.cit.aet.artemis.core.security.UserNotActivatedException;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.connectors.SAML2Service;

/**
 * Authentication success handler for SAML2 that supports external client redirect.
 * <p>
 * If a nonce is found in RelayState, the handler looks up the validated redirect_uri from
 * Hazelcast, mints a JWT, and redirects to the external client URI with the token.
 * If no nonce is present, it falls back to the default behavior (redirect to "/").
 * <p>
 * Accepted residual risk: the JWT is delivered as a query parameter on a custom-scheme URI.
 * A malicious app registered for the same scheme on the user's machine could intercept it, and
 * the JWT may appear in non-default reverse-proxy logs. The feature is therefore opt-in and
 * disabled by default, and http/https schemes are always rejected. See the admin documentation.
 */
public class SAML2ExternalClientAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(SAML2ExternalClientAuthenticationSuccessHandler.class);

    private final HazelcastSaml2RedirectUriRepository redirectUriRepository;

    private final SAML2Service saml2Service;

    private final TokenProvider tokenProvider;

    private final AuditEventRepository auditEventRepository;

    private final SAML2RedirectUriValidator redirectUriValidator;

    private final Duration tokenTtl;

    /**
     * Constructs the handler.
     *
     * @param redirectUriRepository Hazelcast nonce store
     * @param saml2Service          SAML2 user handling service
     * @param tokenProvider         JWT token provider
     * @param auditEventRepository  audit event repository
     * @param redirectUriValidator  validator used to re-check the stored redirect URI before use
     * @param tokenTtl              lifetime of the JWT minted for the external client; kept short
     *                                  because the token travels through a custom-scheme URI
     */
    public SAML2ExternalClientAuthenticationSuccessHandler(HazelcastSaml2RedirectUriRepository redirectUriRepository, SAML2Service saml2Service, TokenProvider tokenProvider,
            AuditEventRepository auditEventRepository, SAML2RedirectUriValidator redirectUriValidator, Duration tokenTtl) {
        super("/");
        setAlwaysUseDefaultTargetUrl(true);
        this.redirectUriRepository = redirectUriRepository;
        this.saml2Service = saml2Service;
        this.tokenProvider = tokenProvider;
        this.auditEventRepository = auditEventRepository;
        this.redirectUriValidator = redirectUriValidator;
        this.tokenTtl = tokenTtl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String relayState = request.getParameter("RelayState");

        if (relayState == null || relayState.isBlank()) {
            // No nonce — standard web flow: redirect to "/" and let SPA handle JWT exchange
            log.debug("No RelayState nonce, falling back to default SAML2 redirect");
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        // External client flow: consume nonce from Hazelcast
        String redirectUri = redirectUriRepository.consumeAndRemove(relayState);
        if (redirectUri == null) {
            log.warn("SAML2 redirect nonce not found or expired");
            auditFailure(authentication, "invalid or expired redirect nonce");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or expired redirect nonce");
            return;
        }

        // Defense-in-depth: re-validate the stored redirect_uri before any user processing or
        // token creation, in case the allowlist changed while the nonce was stored.
        Optional<String> rejection = redirectUriValidator.validate(redirectUri);
        if (rejection.isPresent()) {
            log.warn("SAML2 stored redirect_uri failed re-validation: {}", rejection.get());
            auditFailure(authentication, "redirect_uri failed re-validation");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid redirect URI");
            return;
        }

        // Extract principal from Saml2Authentication
        if (!(authentication instanceof Saml2Authentication saml2Auth) || !(saml2Auth.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal)) {
            log.error("SAML2 authentication success but principal is not Saml2AuthenticatedPrincipal");
            auditFailure(authentication, "unexpected authentication type");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected authentication type");
            return;
        }

        // Reuse SAML2Service for user creation/update, audit logging, login email
        Authentication processedAuth;
        try {
            processedAuth = saml2Service.handleAuthentication(authentication, principal, request);
        }
        catch (UserNotActivatedException e) {
            log.debug("SAML2 external redirect denied: user not activated");
            auditFailure(authentication, "user not activated");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
        }

        // Short-lived JWT: tokens delivered via custom-scheme URI accept a higher interception risk
        // than browser-session JWTs, so we cap their lifetime independently of the default JWT TTL.
        String jwt = tokenProvider.createToken(processedAuth, tokenTtl.toMillis(), null);

        // Build redirect URI with JWT parameter, replacing any pre-existing jwt parameter
        String targetUri = UriComponentsBuilder.fromUriString(redirectUri).replaceQueryParam("jwt", jwt).build().toUriString();

        // Audit log (scheme only, never the JWT). The URI passed re-validation above, so URI.create is safe.
        String scheme = URI.create(redirectUri).getScheme();
        auditEventRepository.add(new AuditEvent(Instant.now(), processedAuth.getName(), AuditEventConstants.SAML2_EXTERNAL_REDIRECT_SUCCESS, Map.of("redirectScheme", scheme)));

        log.info("SAML2 external redirect for user '{}' to scheme '{}'", processedAuth.getName(), scheme);

        response.sendRedirect(targetUri);
    }

    /**
     * Writes a failure audit event. The reason is a fixed, non-sensitive string; the redirect URI
     * itself is never included to avoid leaking attacker-controlled data into the audit log.
     *
     * @param authentication the (pre-processing) authentication, used for the principal name
     * @param reason         a short, fixed reason describing the failure
     */
    private void auditFailure(Authentication authentication, String reason) {
        String principalName = authentication != null ? authentication.getName() : null;
        String username = principalName != null && !principalName.isBlank() ? principalName : "unknown";
        auditEventRepository.add(new AuditEvent(Instant.now(), username, AuditEventConstants.SAML2_EXTERNAL_REDIRECT_FAILURE, Map.of("reason", reason)));
    }
}

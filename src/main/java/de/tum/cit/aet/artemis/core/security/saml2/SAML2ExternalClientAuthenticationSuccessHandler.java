package de.tum.cit.aet.artemis.core.security.saml2;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

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
 */
public class SAML2ExternalClientAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(SAML2ExternalClientAuthenticationSuccessHandler.class);

    private final HazelcastSaml2RedirectUriRepository redirectUriRepository;

    private final SAML2Service saml2Service;

    private final TokenProvider tokenProvider;

    private final AuditEventRepository auditEventRepository;

    private final boolean externalTokenRememberMe;

    /**
     * Constructs the handler.
     *
     * @param redirectUriRepository   Hazelcast nonce store
     * @param saml2Service            SAML2 user handling service
     * @param tokenProvider           JWT token provider
     * @param auditEventRepository    audit event repository
     * @param externalTokenRememberMe whether to use long-lived tokens for external clients
     */
    public SAML2ExternalClientAuthenticationSuccessHandler(HazelcastSaml2RedirectUriRepository redirectUriRepository, SAML2Service saml2Service, TokenProvider tokenProvider,
            AuditEventRepository auditEventRepository, boolean externalTokenRememberMe) {
        super("/");
        setAlwaysUseDefaultTargetUrl(true);
        this.redirectUriRepository = redirectUriRepository;
        this.saml2Service = saml2Service;
        this.tokenProvider = tokenProvider;
        this.auditEventRepository = auditEventRepository;
        this.externalTokenRememberMe = externalTokenRememberMe;
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
            log.warn("SAML2 redirect nonce not found or expired: {}", relayState);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or expired redirect nonce");
            return;
        }

        // Extract principal from Saml2Authentication
        if (!(authentication instanceof Saml2Authentication saml2Auth) || !(saml2Auth.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal)) {
            log.error("SAML2 authentication success but principal is not Saml2AuthenticatedPrincipal");
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
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
        }

        // Generate JWT from the processed authentication (UsernamePasswordAuthenticationToken)
        String jwt = tokenProvider.createToken(processedAuth, externalTokenRememberMe);

        // Build redirect URI with JWT parameter
        String targetUri = UriComponentsBuilder.fromUriString(redirectUri).queryParam("jwt", jwt).build().toUriString();

        // Audit log (without JWT in URI)
        String scheme = URI.create(redirectUri).getScheme();
        auditEventRepository.add(new AuditEvent(Instant.now(), processedAuth.getName(), "SAML2_EXTERNAL_REDIRECT_SUCCESS", Map.of("redirectScheme", scheme)));

        log.info("SAML2 external redirect for user '{}' to scheme '{}'", processedAuth.getName(), scheme);

        response.sendRedirect(targetUri);
    }
}

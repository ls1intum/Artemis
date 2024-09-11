package de.tum.cit.aet.artemis.core.security.filter;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.LtiEmailAlreadyInUseException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;
import de.tum.cit.aet.artemis.lti.dto.Claims;
import de.tum.cit.aet.artemis.lti.dto.LtiAuthenticationResponse;
import de.tum.cit.aet.artemis.lti.service.Lti13Service;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.authentication.OidcAuthenticationToken;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OAuth2LoginAuthenticationFilter;

/**
 * Filter for processing an LTI 1.3 Authorization Request response.
 * It listens for LTI login attempts {@see CustomLti13Configurer#LTI13_LOGIN_PATH} and processes them.
 * Step 3. of OpenID Connect Third Party Initiated Login is handled solely by spring-security-lti13
 * OAuth2LoginAuthenticationFilter.
 */
@Profile("lti")
public class Lti13LaunchFilter extends OncePerRequestFilter {

    private final OAuth2LoginAuthenticationFilter defaultFilter;

    private final Lti13Service lti13Service;

    private final AntPathRequestMatcher requestMatcher;

    private static final Logger log = LoggerFactory.getLogger(Lti13LaunchFilter.class);

    public Lti13LaunchFilter(OAuth2LoginAuthenticationFilter defaultFilter, String filterProcessingUrl, Lti13Service lti13Service) {
        this.defaultFilter = defaultFilter;
        this.lti13Service = lti13Service;
        this.requestMatcher = new AntPathRequestMatcher(filterProcessingUrl);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!this.requestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.info("LTI 1.3 Launch request received for url {}", this.requestMatcher.getPattern());

        try {
            // Login using the distributed authorization request repository
            OidcAuthenticationToken authToken = finishOidcFlow(request, response);
            OidcIdToken ltiIdToken = ((OidcUser) authToken.getPrincipal()).getIdToken();
            String targetLink = ltiIdToken.getClaim(Claims.TARGET_LINK_URI).toString();

            try {
                // here we need to check if this is a deep-linking request or a launch request
                if (CustomLti13Configurer.LTI13_DEEPLINK_MESSAGE_REQUEST.equals(ltiIdToken.getClaim(Claims.MESSAGE_TYPE))) {
                    // Manually setting the deep linking path is required due to Moodle and edX's inconsistent deep linking implementation.
                    // Unlike standard GET request-based methods, these platforms do not guarantee a uniform approach, necessitating
                    // manual configuration to ensure reliable navigation and resource access compatibility.
                    targetLink = CustomLti13Configurer.LTI13_DEEPLINK_SELECT_COURSE_PATH;
                    lti13Service.startDeepLinking(ltiIdToken, authToken.getAuthorizedClientRegistrationId());
                }
                else {
                    lti13Service.performLaunch(ltiIdToken, authToken.getAuthorizedClientRegistrationId());
                }
            }
            catch (LtiEmailAlreadyInUseException ex) {
                // LtiEmailAlreadyInUseException is thrown in case of user who has email address in use is not authenticated after targetLink is set
                // We need targetLink to redirect user on the client-side after successful authentication
                log.error("LTI 1.3 launch failed due to email already in use: {}", ex.getMessage(), ex);
                handleLtiEmailAlreadyInUseException(response, ltiIdToken);
            }

            writeResponse(targetLink, ltiIdToken, authToken.getAuthorizedClientRegistrationId(), response);
        }
        catch (HttpClientErrorException | OAuth2AuthenticationException | IllegalStateException ex) {
            log.error("Error during LTI 1.3 launch request: {}", ex.getMessage(), ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "LTI 1.3 Launch failed");
        }
    }

    private void handleLtiEmailAlreadyInUseException(HttpServletResponse response, OidcIdToken ltiIdToken) {
        this.lti13Service.buildLtiEmailInUseResponse(response, ltiIdToken);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private OidcAuthenticationToken finishOidcFlow(HttpServletRequest request, HttpServletResponse response) {
        OidcAuthenticationToken ltiAuthToken;
        try {
            // call spring-security-lti13 authentication filter to finish the OpenID Connect Third Party Initiated Login
            ltiAuthToken = (OidcAuthenticationToken) defaultFilter.attemptAuthentication(request, response);
            if (ltiAuthToken == null) {
                throw new IllegalStateException("No authentication was returned");
            }
        }
        catch (OAuth2AuthenticationException | IllegalStateException ex) {
            throw new IllegalStateException("Failed to attempt LTI 1.3 login authentication: " + ex.getMessage(), ex);
        }

        return ltiAuthToken;
    }

    private void writeResponse(String targetLinkUri, OidcIdToken ltiIdToken, String clientRegistrationId, HttpServletResponse response) throws IOException {
        PrintWriter writer = response.getWriter();

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(targetLinkUri);
        if (SecurityUtils.isAuthenticated()) {
            log.info("User is authenticated, building LTI response");
            lti13Service.buildLtiResponse(uriBuilder, response);
        }
        LtiAuthenticationResponse jsonResponse = new LtiAuthenticationResponse(uriBuilder.build().toUriString(), ltiIdToken.getTokenValue(), clientRegistrationId);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        writer.print(new ObjectMapper().writeValueAsString(jsonResponse));
        writer.flush();
    }
}

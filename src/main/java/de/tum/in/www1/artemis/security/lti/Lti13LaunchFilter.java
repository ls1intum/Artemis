package de.tum.in.www1.artemis.security.lti;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.in.www1.artemis.domain.lti.Lti13LaunchRequest;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.Lti13Service;
import net.minidev.json.JSONObject;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.authentication.OidcAuthenticationToken;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OAuth2LoginAuthenticationFilter;

/**
 * Processes an LTI 1.3 Authorization Request response.
 * Step 3. of OpenID Connect Third Party Initiated Login is handled solely by spring-security-lti13
 * OAuth2LoginAuthenticationFilter.
 *
 */
public class Lti13LaunchFilter extends OncePerRequestFilter {

    private final OAuth2LoginAuthenticationFilter defaultFilter;

    private final Lti13Service lti13Service;

    private final AntPathRequestMatcher requestMatcher;

    private final Logger log = LoggerFactory.getLogger(Lti13LaunchFilter.class);

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

        // we do not permit anonymous launches
        if (!SecurityUtils.isAuthenticated()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        try {
            OidcAuthenticationToken authToken = finishOidcFlow(request, response);

            Lti13LaunchRequest launchRequest = launchRequestFrom(authToken);

            lti13Service.performLaunch(launchRequest);

            writeResponse(launchRequest, response);
        }
        catch (IOException ex) {
            throw ex;
        }
        catch (Exception ex) {
            log.error(ex.getMessage());
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage());
        }
    }

    private OidcAuthenticationToken finishOidcFlow(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        OidcAuthenticationToken ltiAuthToken;
        try {
            // call spring-security-lti13 authentication filter to finish the OpenID Connect Third Party Initiated Login
            ltiAuthToken = (OidcAuthenticationToken) defaultFilter.attemptAuthentication(request, response);
            if (ltiAuthToken == null) {
                throw new IllegalStateException("No authentication was returned");
            }
        }
        catch (OAuth2AuthenticationException | IllegalStateException ex) {
            throw new RuntimeException("Failed to attempt LTI 1.3 login authentication: " + ex.getMessage());
        }

        return ltiAuthToken;
    }

    private Lti13LaunchRequest launchRequestFrom(OidcAuthenticationToken authToken) {
        try {
            var idToken = ((OidcUser) authToken.getPrincipal()).getIdToken();
            var clientRegistrationId = authToken.getAuthorizedClientRegistrationId();
            return new Lti13LaunchRequest(idToken, clientRegistrationId);
        }
        catch (IllegalArgumentException ex) {
            throw new RuntimeException("Could not create LTI 1.3 launch request with provided idToken: " + ex.getMessage());
        }
    }

    private void writeResponse(Lti13LaunchRequest launchRequest, HttpServletResponse response) throws IOException {
        PrintWriter writer = response.getWriter();

        JSONObject json = new JSONObject();
        json.put("targetLinkUri", launchRequest.getTargetLinkUri());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        writer.print(json);
        writer.flush();
    }
}

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lti.Claims;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
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

    private final TokenProvider tokenProvider;

    private final UserRepository userRepository;

    private final AntPathRequestMatcher requestMatcher;

    private final Logger log = LoggerFactory.getLogger(Lti13LaunchFilter.class);

    public Lti13LaunchFilter(OAuth2LoginAuthenticationFilter defaultFilter, String filterProcessingUrl, Lti13Service lti13Service, TokenProvider tokenProvider,
            UserRepository userRepository) {
        this.defaultFilter = defaultFilter;
        this.lti13Service = lti13Service;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.requestMatcher = new AntPathRequestMatcher(filterProcessingUrl);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!this.requestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            OidcAuthenticationToken authToken = finishOidcFlow(request, response);

            OidcIdToken ltiIdToken = ((OidcUser) authToken.getPrincipal()).getIdToken();
            lti13Service.performLaunch(ltiIdToken, authToken.getAuthorizedClientRegistrationId());

            User user = userRepository.getUserWithGroupsAndAuthorities();
            writeResponse(user, ltiIdToken.getClaim(Claims.TARGET_LINK_URI), response);
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

    private void writeResponse(User user, String targetLinkUri, HttpServletResponse response) throws IOException {
        PrintWriter writer = response.getWriter();

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(targetLinkUri);
        if (!user.getActivated()) {
            uriBuilder.queryParam("initialize", "");

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String jwt = tokenProvider.createToken(authentication, true);
            log.debug("created jwt token: {}", jwt);
            uriBuilder.queryParam("jwt", jwt);
        }
        else {
            uriBuilder.queryParam("jwt", "");
            uriBuilder.queryParam("ltiSuccessLoginRequired", user.getLogin());
        }

        JSONObject json = new JSONObject();
        json.put("targetLinkUri", uriBuilder.build().toUriString());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        writer.print(json);
        writer.flush();
    }
}

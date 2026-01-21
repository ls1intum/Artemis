package de.tum.cit.aet.artemis.lti.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.LTI_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer.LTI13_DEEPLINK_REDIRECT;
import static de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.nimbusds.jwt.SignedJWT;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;

/**
 * REST controller for receiving LTI requests.
 */
@ConditionalOnProperty(value = LTI_ENABLED_PROPERTY_NAME, havingValue = "true")
@Lazy
@RestController
public class PublicLtiResource {

    private static final Logger log = LoggerFactory.getLogger(PublicLtiResource.class);

    public static final String LOGIN_REDIRECT_CLIENT_PATH = "/lti/launch";

    /**
     * POST lti13/auth-callback Redirects an LTI 1.3 Authorization Request Response to the client
     * POST lti13/deep-link: Redirects an LTI 1.3 Deep Linking Request Response to the client
     * <p>
     * Consolidates handling for both 'auth-callback' and 'deep-link' endpoints to simplify client interactions.
     * This approach ensures consistent processing and user experience for authentication and deep linking flows.
     *
     * @param request  HTTP request
     * @param response HTTP response
     * @return the ResponseEntity with status 200 (OK)
     * @throws IOException If an input or output exception occurs
     */
    @PostMapping({ LTI13_LOGIN_REDIRECT_PROXY, LTI13_DEEPLINK_REDIRECT })
    @EnforceNothing
    public ResponseEntity<Void> lti13LaunchRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String state = request.getParameter("state");
        if (state == null) {
            errorOnMissingParameter(response, "state");
            return ResponseEntity.ok().build();
        }

        String idToken = request.getParameter("id_token");
        if (idToken == null) {
            errorOnMissingParameter(response, "id_token");
            return ResponseEntity.ok().build();
        }

        if (!isValidJwtIgnoreSignature(idToken)) {
            errorOnIllegalParameter(response);
            return ResponseEntity.ok().build();
        }

        UriComponentsBuilder uriBuilder = buildRedirect(request);
        uriBuilder.path(LOGIN_REDIRECT_CLIENT_PATH);
        uriBuilder.queryParam("state", URLEncoder.encode(state, StandardCharsets.UTF_8));
        uriBuilder.queryParam("id_token", URLEncoder.encode(idToken, StandardCharsets.UTF_8));
        String redirectUrl = uriBuilder.build().toString();
        log.info("redirect to url: {}", redirectUrl);
        response.sendRedirect(redirectUrl); // Redirect using user-provided values is safe because user-provided values are used in the query parameters, not the url itself
        return ResponseEntity.ok().build();
    }

    /**
     * Strips the signature from a potential JWT and makes sure the rest is valid.
     *
     * @param token The potential token
     * @return Whether the token is valid or not
     */
    private boolean isValidJwtIgnoreSignature(String token) {
        try {
            SignedJWT parsedToken = SignedJWT.parse(token);
            return !parsedToken.getJWTClaimsSet().getExpirationTime().before(Date.from(Instant.now()));
        }
        catch (ParseException e) {
            log.info("LTI request: JWT token is invalid: {}", token, e);
            return false;
        }
    }

    private void errorOnMissingParameter(HttpServletResponse response, String missingParamName) throws IOException {
        String message = "Missing parameter on oauth2 authorization response: " + missingParamName;
        log.error(message);
        response.sendError(HttpStatus.BAD_REQUEST.value(), message);
    }

    private void errorOnIllegalParameter(HttpServletResponse response) throws IOException {
        String message = "Illegal parameter on oauth2 authorization response: id_token";
        log.error(message);
        response.sendError(HttpStatus.BAD_REQUEST.value(), message);
    }

    private UriComponentsBuilder buildRedirect(HttpServletRequest request) {
        UriComponentsBuilder redirectUrlComponentsBuilder = UriComponentsBuilder.newInstance().scheme(request.getScheme()).host(request.getServerName());
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            redirectUrlComponentsBuilder.port(request.getServerPort());
        }
        return redirectUrlComponentsBuilder;
    }
}

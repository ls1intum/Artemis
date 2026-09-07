package de.tum.cit.aet.artemis.lti.web.open;

import static de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer.LTI13_DEEPLINK_REDIRECT;
import static de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.nimbusds.jwt.SignedJWT;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;

/**
 * REST controller for receiving LTI requests.
 */
@Conditional(LtiEnabled.class)
@Lazy
@FeatureUsage("lti/launch")
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
     * @return the response containing the application-local redirect location
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

        URI redirectUrl = UriComponentsBuilder.fromPath(LOGIN_REDIRECT_CLIENT_PATH).queryParam("state", state).queryParam("id_token", idToken).build().encode().toUri();
        log.info("LTI redirect generated for client path {}", LOGIN_REDIRECT_CLIENT_PATH);
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build();
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
            log.info("LTI request contains an invalid JWT token", e);
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
}

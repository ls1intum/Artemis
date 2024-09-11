package de.tum.cit.aet.artemis.lti.web.open;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import de.tum.cit.aet.artemis.core.security.OAuth2JWKSService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;

/**
 * REST controller to serve the public JWKSet related to all OAuth2 clients.
 */
@Profile("lti")
// TODO: should we adapt the mapping based on the profile?
@RestController
public class PublicOAuth2JWKSResource {

    private static final Logger log = LoggerFactory.getLogger(PublicOAuth2JWKSResource.class);

    private final OAuth2JWKSService jwksService;

    public PublicOAuth2JWKSResource(OAuth2JWKSService jwksService) {
        this.jwksService = jwksService;
    }

    /**
     * GET JWKS: Retrieves the JSON Web Key Set (JWKS).
     *
     * @return ResponseEntity containing the JWKS as a JSON string with status 200 (OK). If an error occurs, returns null.
     */
    @GetMapping(".well-known/jwks.json")
    @EnforceNothing
    @ManualConfig
    public ResponseEntity<String> getJwkSet() {
        String keysAsJson = null;
        try {
            keysAsJson = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(jwksService.getJwkSet().toPublicJWKSet().toJSONObject());
        }
        catch (JsonProcessingException exception) {
            log.debug("Error occurred parsing jwkSet: {}", exception.getMessage());
        }
        return new ResponseEntity<>(keysAsJson, HttpStatus.OK);
    }
}

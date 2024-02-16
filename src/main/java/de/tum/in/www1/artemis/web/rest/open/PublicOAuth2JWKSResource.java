package de.tum.in.www1.artemis.web.rest.open;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.GsonBuilder;

import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.security.annotations.ManualConfig;

/**
 * REST controller to serve the public JWKSet related to all OAuth2 clients.
 */
@RestController
@Profile("lti")
public class PublicOAuth2JWKSResource {

    private final OAuth2JWKSService jwksService;

    public PublicOAuth2JWKSResource(OAuth2JWKSService jwksService) {
        this.jwksService = jwksService;
    }

    @GetMapping(".well-known/jwks.json")
    @EnforceNothing
    @ManualConfig
    public ResponseEntity<String> getJwkSet() {
        String keysAsJson = new GsonBuilder().setPrettyPrinting().create().toJson(jwksService.getJwkSet().toPublicJWKSet().toJSONObject());
        return new ResponseEntity<>(keysAsJson, HttpStatus.OK);
    }
}

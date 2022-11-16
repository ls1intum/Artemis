package de.tum.in.www1.artemis.web.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.GsonBuilder;

import de.tum.in.www1.artemis.security.OAuth2JWKSService;

/**
 * REST controller to serve the public JWKSet related to all OAuth2 clients.
 */
@RestController
public class OAuth2JWKSResource {

    private final OAuth2JWKSService jwksService;

    public OAuth2JWKSResource(OAuth2JWKSService jwksService) {
        this.jwksService = jwksService;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getJwkSet() {
        String keysAsJson = new GsonBuilder().setPrettyPrinting().create().toJson(jwksService.getJwkSet().toPublicJWKSet().toJSONObject());
        return new ResponseEntity<>(keysAsJson, HttpStatus.OK);
    }
}

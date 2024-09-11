package de.tum.cit.aet.artemis.domain.lti;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the LTI 1.3 Platform Configuration, encapsulating various endpoints and keys for LTI platform communication.
 *
 * @param issuer                The unique identifier for the issuer of the configuration.
 * @param tokenEndpoint         The endpoint URL for obtaining tokens.
 * @param authorizationEndpoint The endpoint URL for authorization.
 * @param jwksUri               The URI for the JSON Web Key Set (JWKS).
 * @param registrationEndpoint  The endpoint URL for registration.
 */
public record Lti13PlatformConfiguration(@JsonProperty("issuer") String issuer, @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("authorization_endpoint") String authorizationEndpoint, @JsonProperty("jwks_uri") String jwksUri,
        @JsonProperty("registration_endpoint") String registrationEndpoint) {
}

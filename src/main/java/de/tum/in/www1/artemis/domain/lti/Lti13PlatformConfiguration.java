package de.tum.in.www1.artemis.domain.lti;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Lti13PlatformConfiguration {

    private String issuer;

    @JsonProperty("token_endpoint")
    private String tokenEndpoint;

    @JsonProperty("authorization_endpoint")
    private String authorizationEndpoint;

    @JsonProperty("jwks_uri")
    private String jwksUri;

    @JsonProperty("registration_endpoint")
    private String registrationEndpoint;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getRegistrationEndpoint() {
        return registrationEndpoint;
    }

    public void setRegistrationEndpoint(String registrationEndpoint) {
        this.registrationEndpoint = registrationEndpoint;
    }
}

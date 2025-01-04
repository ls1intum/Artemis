package de.tum.cit.aet.artemis.lti.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the client registration details for an LTI 1.3 integration.
 * This class encapsulates information required for LTI 1.3 client registration,
 * including response types, grant types, redirect URIs, and tool configuration.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Lti13ClientRegistration(@JsonProperty("client_id") String clientId, @JsonProperty("response_types") List<String> responseTypes,
        @JsonProperty("grant_types") List<String> grantTypes, @JsonProperty("initiate_login_uri") String initiateLoginUri, @JsonProperty("redirect_uris") List<String> redirectUris,
        @JsonProperty("client_name") String clientName, @JsonProperty("jwks_uri") String jwksUri, @JsonProperty("logo_uri") String logoUri,
        @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod, String scope,
        @JsonProperty("https://purl.imsglobal.org/spec/lti-tool-configuration") Lti13ToolConfiguration lti13ToolConfiguration) {

}

package de.tum.cit.aet.artemis.domain.lti;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;

/**
 * Represents the client registration details for an LTI 1.3 integration.
 * This class encapsulates information required for LTI 1.3 client registration,
 * including response types, grant types, redirect URIs, and tool configuration.
 */
public class Lti13ClientRegistration {

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("response_types")
    private List<String> responseTypes;

    @JsonProperty("grant_types")
    private List<String> grantTypes;

    @JsonProperty("initiate_login_uri")
    private String initiateLoginUri;

    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("jwks_uri")
    private String jwksUri;

    @JsonProperty("logo_uri")
    private String logoUri;

    @JsonProperty("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;

    private String scope;

    @JsonProperty("https://purl.imsglobal.org/spec/lti-tool-configuration")
    private Lti13ToolConfiguration lti13ToolConfiguration;

    /**
     * Default constructor necessary for conversion.
     */
    public Lti13ClientRegistration() { // Necessary for conversion
    }

    /**
     * Constructs a new Lti13ClientRegistration with specified server URL and client registration ID.
     * Initializes various properties such as grant types, response types, and tool configurations.
     *
     * @param serverUrl            The server URL for LTI configuration.
     * @param clientRegistrationId The client registration ID for LTI configuration.
     */
    public Lti13ClientRegistration(String serverUrl, String clientRegistrationId) {
        this.setGrantTypes(Arrays.asList(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue(), AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        this.setResponseTypes(List.of("id_token"));
        this.setClientName("Artemis - " + serverUrl);
        this.setTokenEndpointAuthMethod("private_key_jwt");
        this.setScope(String.join(" ", List.of(Scopes.AGS_SCORE, Scopes.AGS_RESULT)));
        this.setRedirectUris(List.of(serverUrl + "/" + CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH));
        this.setInitiateLoginUri(serverUrl + "/" + CustomLti13Configurer.LTI13_LOGIN_INITIATION_PATH + "/" + clientRegistrationId);
        this.setJwksUri(serverUrl + "/.well-known/jwks.json");
        this.setLogoUri(serverUrl + "/public/images/logo.png");

        Lti13ToolConfiguration toolConfiguration = getLti13ToolConfiguration(serverUrl);
        this.setLti13ToolConfiguration(toolConfiguration);
    }

    private static Lti13ToolConfiguration getLti13ToolConfiguration(String serverUrl) {
        Lti13ToolConfiguration toolConfiguration = new Lti13ToolConfiguration();

        // Extracting the domain from the server URL
        String[] urlParts = serverUrl.split("://");
        String domain = "";
        if (urlParts.length >= 1) {
            domain = urlParts[1]; // Domain cannot include protocol
        }
        toolConfiguration.setDomain(domain);
        toolConfiguration.setTargetLinkUri(serverUrl + "/courses");
        toolConfiguration.setDescription("Artemis: Interactive Learning with Individual Feedback");
        toolConfiguration.setClaims(Arrays.asList("iss", "email", "sub", "name", "given_name", "family_name"));
        Message deepLinkingMessage = new Message(CustomLti13Configurer.LTI13_DEEPLINK_MESSAGE_REQUEST, serverUrl + "/" + CustomLti13Configurer.LTI13_DEEPLINK_REDIRECT_PATH);
        toolConfiguration.setMessages(List.of(deepLinkingMessage));
        return toolConfiguration;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public List<String> getResponseTypes() {
        return responseTypes;
    }

    public void setResponseTypes(List<String> responseTypes) {
        this.responseTypes = responseTypes;
    }

    public List<String> getGrantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(List<String> grantTypes) {
        this.grantTypes = grantTypes;
    }

    public String getInitiateLoginUri() {
        return initiateLoginUri;
    }

    public void setInitiateLoginUri(String initiateLoginUri) {
        this.initiateLoginUri = initiateLoginUri;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getLogoUri() {
        return logoUri;
    }

    public void setLogoUri(String logoUri) {
        this.logoUri = logoUri;
    }

    public String getTokenEndpointAuthMethod() {
        return tokenEndpointAuthMethod;
    }

    public void setTokenEndpointAuthMethod(String tokenEndpointAuthMethod) {
        this.tokenEndpointAuthMethod = tokenEndpointAuthMethod;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Lti13ToolConfiguration getLti13ToolConfiguration() {
        return lti13ToolConfiguration;
    }

    public void setLti13ToolConfiguration(Lti13ToolConfiguration lti13ToolConfiguration) {
        this.lti13ToolConfiguration = lti13ToolConfiguration;
    }

    /**
     * Inner class representing the LTI 1.3 tool configuration.
     */
    public static class Lti13ToolConfiguration {

        private String domain;

        @JsonProperty("target_link_uri")
        private String targetLinkUri;

        private String description;

        private List<Message> messages;

        private List<String> claims;

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getTargetLinkUri() {
            return targetLinkUri;
        }

        public void setTargetLinkUri(String targetLinkUri) {
            this.targetLinkUri = targetLinkUri;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }

        public List<String> getClaims() {
            return claims;
        }

        public void setClaims(List<String> claims) {
            this.claims = claims;
        }
    }

    /**
     * Inner class representing a message in LTI 1.3 tool configuration.
     */
    public static class Message {

        private String type;

        @JsonProperty("target_link_uri")
        private String targetLinkUri;

        public Message() {// Necessary for conversion
        }

        public Message(String type, String targetLinkUri) {
            this.type = type;
            this.targetLinkUri = targetLinkUri;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTargetLinkUri() {
            return targetLinkUri;
        }

        public void setTargetLinkUri(String targetLinkUri) {
            this.targetLinkUri = targetLinkUri;
        }
    }
}

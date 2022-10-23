package de.tum.in.www1.artemis.domain.lti;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.Course;

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

    @JsonProperty("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;

    private String scope;

    @JsonProperty("https://purl.imsglobal.org/spec/lti-tool-configuration")
    private Lti13ToolConfiguration lti13ToolConfiguration;

    public Lti13ClientRegistration() {
    }

    public Lti13ClientRegistration(Course course, String clientRegistrationId) {
        this.setGrantTypes(Arrays.asList(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue(), AuthorizationGrantType.IMPLICIT.getValue()));
        this.setResponseTypes(List.of("id_token"));
        this.setRedirectUris(List.of("http://localhost:9000/api/lti13/auth-callback"));
        this.setInitiateLoginUri("http://localhost:9000/api/lti13/initiate-login/" + clientRegistrationId);
        this.setClientName("Artemis - " + course.getShortName());
        this.setJwksUri("http://host.docker.internal:8080/.well-known/jwks.json");
        this.setTokenEndpointAuthMethod("private_key_jwt");
        this.setScope(String.join(" ", List.of(Scopes.AGS_SCORE)));

        Lti13ToolConfiguration toolConfiguration = new Lti13ToolConfiguration();
        toolConfiguration.setDomain("localhost:9000");
        toolConfiguration.setTargetLinkUri("http://localhost:9000/courses/" + course.getId());
        toolConfiguration.setClaims(Arrays.asList("iss", "email", "sub", "name", "given_name", "family_name"));
        toolConfiguration.setMessages(List.of(new Message("LtiDeepLinkingRequest", "test")));
        this.setLti13ToolConfiguration(toolConfiguration);
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

    public static class Lti13ToolConfiguration {

        private String domain;

        @JsonProperty("target_link_uri")
        private String targetLinkUri;

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

    public static class Message {

        private String type;

        @JsonProperty("target_link_uri")
        private String targetLinkUri;

        public Message() {
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

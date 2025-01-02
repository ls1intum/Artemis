package de.tum.cit.aet.artemis.lti.dto;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

import de.tum.cit.aet.artemis.lti.config.CustomLti13Configurer;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.LTIAuthorizationGrantType;

public class Lti13ClientRegistrationFactory {

    /**
     * Constructs a new Lti13ClientRegistration with specified server URL and client registration ID.
     * Initializes various properties such as grant types, response types, and tool configurations.
     *
     * @param serverUrl            The server URL for LTI configuration.
     * @param clientRegistrationId The client registration ID for LTI configuration.
     * @return A new Lti13ClientRegistration object with certain default values for Artemis
     */
    public static Lti13ClientRegistration createRegistration(String serverUrl, String clientRegistrationId) {
        var grantTypes = Arrays.asList(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue(), LTIAuthorizationGrantType.IMPLICIT.getValue());
        var responseTypes = List.of("id_token");
        var clientName = "Artemis - " + serverUrl;
        var tokenEndpointAuthMethod = "private_key_jwt";
        var scope = String.join(" ", List.of(Scopes.AGS_SCORE, Scopes.AGS_RESULT));
        var redirectUris = List.of(serverUrl + "/" + CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH);
        var initiateLoginUri = serverUrl + "/" + CustomLti13Configurer.LTI13_LOGIN_INITIATION_PATH + "/" + clientRegistrationId;
        var jwksUri = serverUrl + "/.well-known/jwks.json";
        var logoUri = serverUrl + "/public/images/logo.png";

        return new Lti13ClientRegistration(clientRegistrationId, responseTypes, grantTypes, initiateLoginUri, redirectUris, clientName, jwksUri, logoUri, tokenEndpointAuthMethod,
                scope, createLti13ToolConfiguration(serverUrl));
    }

    private static Lti13ToolConfiguration createLti13ToolConfiguration(String serverUrl) {

        // Extracting the domain from the server URL
        String[] urlParts = serverUrl.split("://");
        String domain = "";
        if (urlParts.length >= 1) {
            domain = urlParts[1]; // Domain cannot include protocol
        }
        var claims = Arrays.asList("iss", "email", "sub", "name", "given_name", "family_name");
        var deepLinkingMessage = new Lti13Message(CustomLti13Configurer.LTI13_DEEPLINK_MESSAGE_REQUEST, serverUrl + "/" + CustomLti13Configurer.LTI13_DEEPLINK_REDIRECT_PATH);
        return new Lti13ToolConfiguration(domain, serverUrl + "/courses", "Artemis: Interactive Learning with Individual Feedback", List.of(deepLinkingMessage), claims);
    }
}

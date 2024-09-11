package de.tum.cit.aet.artemis.domain.lti;

import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents an LTI 1.3 Launch Request, encapsulating the necessary information
 * from an OIDC ID token and additional parameters required for launching an LTI tool.
 *
 * @param iss                  The issuer identifier from the OIDC ID token, representing the platform.
 * @param sub                  The subject identifier from the OIDC ID token, representing the user.
 * @param deploymentId         The deployment ID from the OIDC ID token, unique to the tool and platform pair.
 * @param resourceLinkId       The resource link ID, identifying the specific resource from the launch request.
 * @param targetLinkUri        The target link URI from the OIDC ID token, where the tool is expected to send the user.
 * @param agsClaim             An optional {@link Lti13AgsClaim} representing the Assignment and Grade Services claim, if present.
 * @param clientRegistrationId The client registration ID, identifying the tool registration with the platform.
 */
public record Lti13LaunchRequest(String iss, String sub, String deploymentId, String resourceLinkId, String targetLinkUri, Lti13AgsClaim agsClaim, String clientRegistrationId) {

    /**
     * Factory method to create an instance of Lti13LaunchRequest from an OIDC ID token and client registration ID.
     * Validates required fields and extracts information from the ID token.
     *
     * @param ltiIdToken           the OIDC ID token containing the claims.
     * @param clientRegistrationId the client registration ID for the request.
     * @return an instance of Lti13LaunchRequest.
     * @throws IllegalArgumentException if required fields are missing in the ID token.
     */
    public static Lti13LaunchRequest from(OidcIdToken ltiIdToken, String clientRegistrationId) {
        String iss = ltiIdToken.getClaim(IdTokenClaimNames.ISS);
        String sub = ltiIdToken.getClaim(IdTokenClaimNames.SUB);
        String deploymentId = ltiIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID);
        String resourceLinkId = extractResourceLinkId(ltiIdToken);
        String targetLinkUri = ltiIdToken.getClaim(Claims.TARGET_LINK_URI);
        Lti13AgsClaim agsClaim = Lti13AgsClaim.from(ltiIdToken).orElse(null);

        validateRequiredFields(iss, sub, deploymentId, resourceLinkId, targetLinkUri, clientRegistrationId);

        return new Lti13LaunchRequest(iss, sub, deploymentId, resourceLinkId, targetLinkUri, agsClaim, clientRegistrationId);
    }

    private static String extractResourceLinkId(OidcIdToken ltiIdToken) {
        Object resourceLinkClaim = ltiIdToken.getClaim(Claims.RESOURCE_LINK);
        if (resourceLinkClaim != null) {
            JsonNode resourceLinkJson = new ObjectMapper().convertValue(resourceLinkClaim, JsonNode.class);
            JsonNode idNode = resourceLinkJson.get("id");
            return idNode != null ? idNode.asText() : null;
        }
        return null;
    }

    private static void validateRequiredFields(String iss, String sub, String deploymentId, String resourceLinkId, String targetLinkUri, String clientRegistrationId) {
        Assert.notNull(iss, "Iss must not be empty in LTI 1.3 launch request");
        Assert.notNull(sub, "Sub must not be empty in LTI 1.3 launch request");
        Assert.notNull(deploymentId, "DeploymentId must not be empty in LTI 1.3 launch request");
        Assert.notNull(resourceLinkId, "ResourceLinkId must not be empty in LTI 1.3 launch request");
        Assert.notNull(targetLinkUri, "TargetLinkUri must not be empty in LTI 1.3 launch request");
        Assert.notNull(clientRegistrationId, "ClientRegistrationId must not be empty in LTI 1.3 launch request");
    }
}

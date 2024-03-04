package de.tum.in.www1.artemis.domain.lti;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Represents the LTI 1.3 Deep Linking Response.
 * It encapsulates the necessary information to construct a valid deep linking response according to the LTI 1.3 specification.
 * For more details, refer to <a href="https://www.imsglobal.org/spec/lti-dl/v2p0#deep-linking-response-message">LTI 1.3 Deep Linking Response Specification</a>
 *
 * @param aud                  The 'aud' (Audience) field is used to specify the intended recipient of the response
 *                                 In the context of LTI Deep Linking, this field is set to the 'iss' (Issuer) value from the original request.
 *                                 This indicates that the response is specifically intended for the issuer of the original request.
 * @param iss                  * The 'iss' (Issuer) field identifies the principal that issued the response.
 *                                 For LTI Deep Linking, this field is set to the 'aud' (Audience) value from the original request.
 *                                 This reversal signifies that the tool (originally the audience) is now the issuer of the response.
 * @param exp                  Expiration time of the response.
 * @param iat                  Issued at time of the response.
 * @param nonce                A string value used to associate a Client session with an ID Token.
 * @param message              A message included in the deep linking response.
 * @param deploymentId         The deployment ID from the LTI request.
 * @param messageType          The type of LTI message, for deep linking this is "LtiDeepLinkingResponse".
 * @param ltiVersion           The LTI version, for deep linking responses this is typically "1.3.0".
 * @param contentItems         The actual content items being linked.
 * @param deepLinkingSettings  A JSON object containing deep linking settings.
 * @param clientRegistrationId The client registration ID.
 * @param returnUrl            The URL to return to after deep linking is completed.
 */
public record Lti13DeepLinkingResponse(@JsonProperty("aud") String aud, @JsonProperty("iss") String iss, @JsonProperty("exp") String exp, @JsonProperty("iat") String iat,
        @JsonProperty("nonce") String nonce, @JsonProperty(Claims.MSG) String message, @JsonProperty(Claims.LTI_DEPLOYMENT_ID) String deploymentId,
        @JsonProperty(Claims.MESSAGE_TYPE) String messageType, @JsonProperty(Claims.LTI_VERSION) String ltiVersion, @JsonProperty(Claims.CONTENT_ITEMS) String contentItems,
        JsonObject deepLinkingSettings, String clientRegistrationId, String returnUrl) {

    /**
     * Constructs an Lti13DeepLinkingResponse from an OIDC ID token and client registration ID.
     * The 'aud' and 'iss' fields are reversed in the response compared to the request. This is a specific requirement in LTI 1.3 Deep Linking:
     * - The 'iss' (Issuer) claim in the Deep Linking Request becomes the 'aud' (Audience) claim in the Response.
     * - The 'aud' claim in the Request becomes the 'iss' claim in the Response.
     * This reversal ensures that the response is directed to and validated by the correct entity, adhering to the OIDC and LTI 1.3 protocols.
     *
     * @param ltiIdToken           the OIDC ID token
     * @param clientRegistrationId the client registration ID
     */
    public static Lti13DeepLinkingResponse from(OidcIdToken ltiIdToken, String clientRegistrationId) {
        validateClaims(ltiIdToken);
        JsonObject deepLinkingSettingsJson = new Gson().toJsonTree(ltiIdToken.getClaim(Claims.DEEP_LINKING_SETTINGS)).getAsJsonObject();
        String returnUrl = deepLinkingSettingsJson.get("deep_link_return_url").getAsString();

        return new Lti13DeepLinkingResponse(ltiIdToken.getIssuer().toString(), ltiIdToken.getAudience().toString().replace("[", "").replace("]", ""),
                ltiIdToken.getExpiresAt().toString(), ltiIdToken.getIssuedAt().toString(), ltiIdToken.getClaimAsString("nonce"), "Content successfully linked",
                ltiIdToken.getClaimAsString(Claims.LTI_DEPLOYMENT_ID), "LtiDeepLinkingResponse", "1.3.0", null, // ContentItems needs to be set separately
                deepLinkingSettingsJson, clientRegistrationId, returnUrl);
    }

    private static void validateClaims(OidcIdToken ltiIdToken) {
        if (ltiIdToken == null) {
            throw new IllegalArgumentException("The OIDC ID token must not be null.");
        }

        Object deepLinkingSettingsElement = ltiIdToken.getClaim(Claims.DEEP_LINKING_SETTINGS);
        if (deepLinkingSettingsElement == null) {
            throw new IllegalArgumentException("Missing or invalid deep linking settings in ID token.");
        }

        ensureClaimPresent(ltiIdToken, "iss");
        ensureClaimPresent(ltiIdToken, "aud");
        ensureClaimPresent(ltiIdToken, "exp");
        ensureClaimPresent(ltiIdToken, "iat");
        ensureClaimPresent(ltiIdToken, "nonce");
        ensureClaimPresent(ltiIdToken, Claims.LTI_DEPLOYMENT_ID);
    }

    private static void ensureClaimPresent(OidcIdToken ltiIdToken, String claimName) {
        Object claimValue = ltiIdToken.getClaim(claimName);
        if (claimValue == null) {
            throw new IllegalArgumentException("Missing claim: " + claimName);
        }
    }

    /**
     * Retrieves a map of claims to be included in the ID token.
     *
     * @return a map of claims
     */
    public Map<String, Object> getClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("aud", aud());
        claims.put("iss", iss());
        claims.put("exp", exp());
        claims.put("iat", iat());
        claims.put("nonce", nonce());
        claims.put(Claims.MSG, message());
        claims.put(Claims.LTI_DEPLOYMENT_ID, deploymentId());
        claims.put(Claims.MESSAGE_TYPE, messageType());
        claims.put(Claims.LTI_VERSION, ltiVersion());
        claims.put(Claims.CONTENT_ITEMS, contentItems());

        return claims;
    }

    /**
     * Returns a new Lti13DeepLinkingResponse instance with updated contentItems.
     *
     * @param contentItems The new contentItems value.
     * @return A new Lti13DeepLinkingResponse instance.
     */
    public Lti13DeepLinkingResponse setContentItems(String contentItems) {
        return new Lti13DeepLinkingResponse(this.aud, this.iss, this.exp, this.iat, this.nonce, this.message, this.deploymentId, this.messageType, this.ltiVersion, contentItems,
                this.deepLinkingSettings, this.clientRegistrationId, this.returnUrl);
    }
}

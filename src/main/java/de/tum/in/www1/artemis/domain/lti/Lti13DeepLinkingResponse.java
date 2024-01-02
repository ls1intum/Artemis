package de.tum.in.www1.artemis.domain.lti;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Represents the LTI 1.3 Deep Linking Response.
 * It encapsulates the necessary information to construct a valid deep linking response according to the LTI 1.3 specification.
 * For more details, refer to <a href="https://www.imsglobal.org/spec/lti-dl/v2p0#deep-linking-response-message">LTI 1.3 Deep Linking Response Specification</a>
 */
public class Lti13DeepLinkingResponse {

    /**
     * The 'aud' (Audience) field is used to specify the intended recipient of the response
     * In the context of LTI Deep Linking, this field is set to the 'iss' (Issuer) value from the original request.
     * This indicates that the response is specifically intended for the issuer of the original request.
     */
    @JsonProperty("aud")
    private String aud;

    /**
     * The 'iss' (Issuer) field identifies the principal that issued the response.
     * For LTI Deep Linking, this field is set to the 'aud' (Audience) value from the original request.
     * This reversal signifies that the tool (originally the audience) is now the issuer of the response.
     */
    @JsonProperty("iss")
    private String iss;

    @JsonProperty("exp")
    private String exp;

    @JsonProperty("iat")
    private String iat;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty(Claims.MSG)
    private String message;

    @JsonProperty(Claims.LTI_DEPLOYMENT_ID)
    private String deploymentId;

    @JsonProperty(Claims.MESSAGE_TYPE)
    private String messageType;

    @JsonProperty(Claims.LTI_VERSION)
    private String ltiVersion;

    @JsonProperty(Claims.CONTENT_ITEMS)
    private String contentItems;

    private JsonObject deepLinkingSettings;

    private String clientRegistrationId;

    private String returnUrl;

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
    public Lti13DeepLinkingResponse(OidcIdToken ltiIdToken, String clientRegistrationId) {
        validateClaims(ltiIdToken);

        Map<String, Object> deepLinkingSettings = ltiIdToken.getClaim(Claims.DEEP_LINKING_SETTINGS);
        // convert the map to json
        this.deepLinkingSettings = new GsonBuilder().setPrettyPrinting().create().toJsonTree(deepLinkingSettings).getAsJsonObject();
        this.setReturnUrl(this.deepLinkingSettings.get("deep_link_return_url").getAsString());
        this.clientRegistrationId = clientRegistrationId;

        // the issuer claim in the deep linking request becomes the audience claim in the response
        this.setAud(ltiIdToken.getClaim("iss").toString());
        // the audience claim in the request becomes the issuer claim in the response
        this.setIss(ltiIdToken.getClaim("aud").toString().replace("[", "").replace("]", ""));

        this.setExp(ltiIdToken.getClaim("exp").toString());
        this.setIat(ltiIdToken.getClaim("iat").toString());
        this.setNonce(ltiIdToken.getClaim("nonce").toString());
        this.setMessage("Content successfully linked");
        this.setDeploymentId(ltiIdToken.getClaim(Claims.LTI_DEPLOYMENT_ID).toString());
        this.setMessageType("LtiDeepLinkingResponse");
        this.setLtiVersion("1.3.0");
    }

    /**
     * Retrieves a map of claims to be included in the ID token.
     *
     * @return a map of claims
     */
    public Map<String, Object> getClaims() {
        Map<String, Object> claims = new HashMap<>();

        claims.put("aud", aud);
        claims.put("iss", iss);
        claims.put("exp", exp);
        claims.put("iat", iat);
        claims.put("nonce", nonce);
        claims.put(Claims.MSG, message);
        claims.put(Claims.LTI_DEPLOYMENT_ID, deploymentId);
        claims.put(Claims.MESSAGE_TYPE, messageType);
        claims.put(Claims.LTI_VERSION, ltiVersion);
        claims.put(Claims.CONTENT_ITEMS, contentItems);

        return claims;
    }

    private void validateClaims(OidcIdToken ltiIdToken) {
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

    private void ensureClaimPresent(OidcIdToken ltiIdToken, String claimName) {
        Object claimValue = ltiIdToken.getClaim(claimName);
        if (claimValue == null) {
            throw new IllegalArgumentException("Missing claim: " + claimName);
        }
    }

    public void setAud(String aud) {
        this.aud = aud;
    }

    public String getIss() {
        return iss;
    }

    public void setIss(String iss) {
        this.iss = iss;
    }

    public String getExp() {
        return exp;
    }

    public void setExp(String exp) {
        this.exp = exp;
    }

    public String getIat() {
        return iat;
    }

    public void setIat(String iat) {
        this.iat = iat;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getLtiVersion() {
        return ltiVersion;
    }

    public void setLtiVersion(String ltiVersion) {
        this.ltiVersion = ltiVersion;
    }

    public String getContentItems() {
        return contentItems;
    }

    public void setContentItems(String contentItems) {
        this.contentItems = contentItems;
    }

    public JsonObject getDeepLinkingSettings() {
        return deepLinkingSettings;
    }

    public void setDeepLinkingSettings(JsonObject deepLinkingSettings) {
        this.deepLinkingSettings = deepLinkingSettings;
    }

    public String getClientRegistrationId() {
        return clientRegistrationId;
    }

    public void setClientRegistrationId(String clientRegistrationId) {
        this.clientRegistrationId = clientRegistrationId;
    }

    public String getAud() {
        return aud;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }
}

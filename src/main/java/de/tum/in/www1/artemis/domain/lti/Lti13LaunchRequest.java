package de.tum.in.www1.artemis.domain.lti;

import org.json.simple.JSONObject;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.util.Assert;

public class Lti13LaunchRequest {

    private final String iss;

    private final String sub;

    private final String deploymentId;

    private final String resourceLinkId;

    private final Lti13AgsClaim agsClaim;

    public Lti13LaunchRequest(OidcIdToken ltiIdToken, String clientRegistrationId) {
        this.iss = ltiIdToken.getClaim("iss");
        this.sub = ltiIdToken.getClaim("sub");
        this.deploymentId = ltiIdToken.getClaim(ArtemisLtiClaims.LTI_DEPLOYMENT_ID);
        this.resourceLinkId = ltiIdToken.getClaim(ArtemisLtiClaims.RESOURCE_LINK) != null ? (String) new JSONObject(ltiIdToken.getClaim(ArtemisLtiClaims.RESOURCE_LINK)).get("id")
                : null;
        this.agsClaim = Lti13AgsClaim.from(ltiIdToken).orElse(null);

        Assert.notNull(iss, "Iss must not be empty in LTI 1.3 launch request");
        Assert.notNull(sub, "Sub must not be empty in LTI 1.3 launch request");
        Assert.notNull(deploymentId, "DeploymentId must not be empty in LTI 1.3 launch request");
        Assert.notNull(resourceLinkId, "ResourceLinkId must not be empty in LTI 1.3 launch request");
        Assert.notNull(clientRegistrationId, "ClientRegistrationId must not be empty in LTI 1.3 launch request");
    }

    public String getIss() {
        return iss;
    }

    public String getSub() {
        return sub;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public String getResourceLinkId() {
        return resourceLinkId;
    }

    public Lti13AgsClaim getAgsClaim() {
        return agsClaim;
    }
}

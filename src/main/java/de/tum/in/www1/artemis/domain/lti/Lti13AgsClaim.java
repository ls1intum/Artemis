package de.tum.in.www1.artemis.domain.lti;

import java.util.*;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import com.google.gson.*;

/**
 * A wrapper class for an LTI 1.3 Assignment and Grading Services Claim. We support the Score Publishing Service in order to transmit scores.
 */
public class Lti13AgsClaim {

    private List<String> scope;

    private String lineItem;

    /**
     * Returns an Ags-Claim representation if the provided idToken contains any.
     *
     * @param idToken to be parsed
     * @return an Ags-Claim if one was present in idToken.
     */
    public static Optional<Lti13AgsClaim> from(OidcIdToken idToken) {
        if (idToken.getClaim(Claims.AGS_CLAIM) == null) {
            return Optional.empty();
        }

        JsonObject agsClaimJson = JsonParser.parseString(idToken.getClaim(Claims.AGS_CLAIM).toString()).getAsJsonObject();

        Lti13AgsClaim agsClaim = new Lti13AgsClaim();
        JsonArray scopes = agsClaimJson.get("scope").getAsJsonArray();

        if (scopes == null) {
            return Optional.empty();
        }

        if (scopes.contains(new JsonPrimitive(Scopes.AGS_SCORE))) {
            agsClaim.setScope(Collections.singletonList(Scopes.AGS_SCORE));
        }

        agsClaim.setLineItem(agsClaimJson.get("lineitem").getAsString());

        return Optional.of(agsClaim);
    }

    public List<String> getScope() {
        return scope;
    }

    private void setScope(List<String> scope) {
        this.scope = scope;
    }

    public String getLineItem() {
        return lineItem;
    }

    private void setLineItem(String lineItem) {
        this.lineItem = lineItem;
    }
}

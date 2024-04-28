package de.tum.in.www1.artemis.domain.lti;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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
     * @throws IllegalStateException if the AGS claim is present but malformed or cannot be processed.
     */
    public static Optional<Lti13AgsClaim> from(OidcIdToken idToken) {
        if (idToken.getClaim(Claims.AGS_CLAIM) == null) {
            return Optional.empty();
        }

        try {
            JsonObject agsClaimJson = new Gson().toJsonTree(idToken.getClaim(Claims.AGS_CLAIM)).getAsJsonObject();
            Lti13AgsClaim agsClaim = new Lti13AgsClaim();
            JsonArray scopes = agsClaimJson.get("scope").getAsJsonArray();

            if (scopes == null) {
                return Optional.empty();
            }

            if (scopes.contains(new JsonPrimitive(Scopes.AGS_SCORE))) {
                agsClaim.setScope(Collections.singletonList(Scopes.AGS_SCORE));
            }

            // For moodle lineItem is stored in lineitem claim, for edX it is in lineitems
            JsonElement lineItem;
            if (agsClaimJson.get("lineitem") == null) {
                lineItem = agsClaimJson.get("lineitems");
            }
            else {
                lineItem = agsClaimJson.get("lineitem");
            }

            if (lineItem != null) {
                agsClaim.setLineItem(lineItem.getAsString());
            }
            else {
                agsClaim.setLineItem(null);
            }
            return Optional.of(agsClaim);
        }
        catch (IllegalStateException | ClassCastException ex) {
            throw new IllegalStateException("Failed to parse LTI 1.3 ags claim.", ex);
        }
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

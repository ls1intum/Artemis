package de.tum.cit.aet.artemis.lti.dto;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A wrapper record for an LTI 1.3 Assignment and Grading Services Claim. We support the Score Publishing Service in order to transmit scores.
 */
public record Lti13AgsClaim(List<String> scope, String lineItem) {

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
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode agsClaimJson = objectMapper.convertValue(idToken.getClaim(Claims.AGS_CLAIM), JsonNode.class);

            JsonNode scopes = agsClaimJson.get("scope");
            List<String> scopeList = null;
            if (scopes != null && scopes.isArray() && scopes.has(Scopes.AGS_SCORE)) {
                scopeList = Collections.singletonList(Scopes.AGS_SCORE);
            }

            // For moodle lineItem is stored in lineitem claim, for edX it is in lineitems
            JsonNode lineItemNode;
            if (agsClaimJson.get("lineitem") == null) {
                lineItemNode = agsClaimJson.get("lineitems");
            }
            else {
                lineItemNode = agsClaimJson.get("lineitem");
            }

            String lineItem = lineItemNode != null ? lineItemNode.asText() : null;
            return Optional.of(new Lti13AgsClaim(scopeList, lineItem));
        }
        catch (IllegalStateException | ClassCastException ex) {
            throw new IllegalStateException("Failed to parse LTI 1.3 ags claim.", ex);
        }
    }
}

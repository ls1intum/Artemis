package de.tum.cit.aet.artemis.lti.dto;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A wrapper record for an LTI 1.3 Assignment and Grading Services Claim. We support the Score Publishing Service in order to transmit scores.
 *
 * @param lineItem the URL of the line item that scores are published to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Lti13AgsClaim(String lineItem) {

    /**
     * Returns an Ags-Claim representation if the provided idToken contains any.
     *
     * @param idToken to be parsed
     * @return an Ags-Claim if one was present in idToken.
     */
    public static Optional<Lti13AgsClaim> from(OidcIdToken idToken) {
        Object agsClaim = idToken.getClaim(Claims.AGS_CLAIM);
        if (agsClaim == null) {
            return Optional.empty();
        }

        // The claim comes from a third-party LMS: a claim or a line item of an unexpected shape means no line item, not a failed launch.
        if (!(agsClaim instanceof Map<?, ?> endpoint)) {
            return Optional.of(new Lti13AgsClaim(null));
        }

        // For moodle lineItem is stored in lineitem claim, for edX it is in lineitems
        Object lineItem = endpoint.get("lineitem") != null ? endpoint.get("lineitem") : endpoint.get("lineitems");
        return Optional.of(new Lti13AgsClaim(lineItem instanceof String url ? url : null));
    }
}

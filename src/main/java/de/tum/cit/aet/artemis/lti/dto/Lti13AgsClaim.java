package de.tum.cit.aet.artemis.lti.dto;

import java.util.Optional;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * A wrapper record for an LTI 1.3 Assignment and Grading Services Claim. We support the Score Publishing Service in order to transmit scores.
 *
 * @param lineItem the URL of the line item that scores are published to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Lti13AgsClaim(String lineItem) {

    /**
     * The part of the AGS endpoint claim of an LTI 1.3 ID token that Artemis reads.
     *
     * @param lineitem  the line item URL as sent by Moodle
     * @param lineitems the line item URL as sent by edX
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AgsEndpointDTO(String lineitem, String lineitems) {
    }

    /**
     * Returns an Ags-Claim representation if the provided idToken contains any.
     *
     * @param idToken to be parsed
     * @return an Ags-Claim if one was present in idToken.
     * @throws IllegalStateException if the AGS claim is present but malformed or cannot be processed.
     */
    public static Optional<Lti13AgsClaim> from(OidcIdToken idToken) {
        Object agsClaim = idToken.getClaim(Claims.AGS_CLAIM);
        if (agsClaim == null) {
            return Optional.empty();
        }

        try {
            AgsEndpointDTO endpoint = JsonObjectMapper.get().convertValue(agsClaim, AgsEndpointDTO.class);

            // For moodle lineItem is stored in lineitem claim, for edX it is in lineitems
            String lineItem = endpoint.lineitem() != null ? endpoint.lineitem() : endpoint.lineitems();
            return Optional.of(new Lti13AgsClaim(lineItem));
        }
        catch (JacksonException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to parse LTI 1.3 ags claim.", ex);
        }
    }
}

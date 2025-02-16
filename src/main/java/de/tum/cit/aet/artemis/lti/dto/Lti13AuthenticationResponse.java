package de.tum.cit.aet.artemis.lti.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Holds LTI authentication response details.
 *
 * @param targetLinkUri        URI targeted in the LTI process.
 * @param ltiIdToken           LTI service provided ID token.
 * @param clientRegistrationId Client's registration ID with LTI service.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Lti13AuthenticationResponse(String targetLinkUri, String ltiIdToken, String clientRegistrationId) {
}

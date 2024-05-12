package de.tum.in.www1.artemis.domain.lti;

/**
 * Holds LTI authentication response details.
 *
 * @param targetLinkUri        URI targeted in the LTI process.
 * @param ltiIdToken           LTI service provided ID token.
 * @param clientRegistrationId Client's registration ID with LTI service.
 */
public record LtiAuthenticationResponse(String targetLinkUri, String ltiIdToken, String clientRegistrationId) {
}

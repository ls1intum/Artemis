package de.tum.cit.aet.artemis.core.dto;

import de.tum.cit.aet.artemis.core.dto.WebAuthnCredential.ClientExtensionResults;

public record AuthenticateDTO(String authenticatorAttachment, ClientExtensionResults clientExtensionResults, String id, String rawId, AuthenticatorAssertionResponse response,
        String type) {

    public static record AuthenticatorAssertionResponse(String authenticatorData, String clientDataJSON, String signature, String userHandle) {
    }
}

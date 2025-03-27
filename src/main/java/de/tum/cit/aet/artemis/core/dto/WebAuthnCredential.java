package de.tum.cit.aet.artemis.core.dto;

import java.util.Set;

public record WebAuthnCredential(String authenticatorAttachment, ClientExtensionResults clientExtensionResults, String id, String rawId, AuthenticatorAttestationResponse response,
        String type) {

    public record ClientExtensionResults(CredProps credProps) {
    }

    public record CredProps(boolean rk) {
    }

    public record AuthenticatorAttestationResponse(String attestationObject, String authenticatorData, String clientDataJSON, String publicKey, int publicKeyAlgorithm,
            Set<String> transports) {
    }
}

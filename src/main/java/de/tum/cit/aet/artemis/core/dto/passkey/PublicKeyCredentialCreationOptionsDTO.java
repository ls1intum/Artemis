package de.tum.cit.aet.artemis.core.dto.passkey;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

import org.springframework.security.web.webauthn.api.AuthenticationExtensionsClientInputs;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;

public record PublicKeyCredentialCreationOptionsDTO(Bytes challenge, PublicKeyCredentialUserEntity user, ArtemisAttestationConveyancePreferenceDTO attestation,
        ArtemisPublicKeyCredentialRpEntityDTO rp, List<ArtemisPublicKeyCredentialParametersDTO> pubKeyCredParams, ArtemisAuthenticatorSelectionCriteriaDTO authenticatorSelection,
        List<PublicKeyCredentialDescriptor> excludeCredentials, AuthenticationExtensionsClientInputs extensions, Duration timeout) implements Serializable {

    public PublicKeyCredentialCreationOptions toPublicKeyCredentialCreationOptions() {
        //@formatter:off
        return PublicKeyCredentialCreationOptions.builder()
            .challenge(challenge())
            .user(user())
            .attestation(attestation().toAttestationConveyancePreference())
            .rp(rp().toPublicKeyCredentialRpEntity())
            .pubKeyCredParams(ArtemisPublicKeyCredentialParametersDTO.convertToPublicKeyCredentialParameters(pubKeyCredParams()))
            .authenticatorSelection(authenticatorSelection().toAuthenticatorSelectionCriteria())
            .excludeCredentials(excludeCredentials())
            .extensions(extensions())
            .timeout(timeout())
            .build();
        //@formatter:on
    }
}

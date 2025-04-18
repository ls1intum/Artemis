package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

import org.springframework.security.web.webauthn.api.AttestationConveyancePreference;
import org.springframework.security.web.webauthn.api.AuthenticationExtensionsClientInputs;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;

public record PublicKeyCredentialCreationOptionsDTO(Bytes challenge, PublicKeyCredentialUserEntity user, ArtemisAttestationConveyancePreferenceDTO attestation,
        ArtemisPublicKeyCredentialRpEntityDTO rp, List<ArtemisPublicKeyCredentialParametersDTO> pubKeyCredParams, ArtemisAuthenticatorSelectionCriteriaDTO authenticatorSelection,
        List<PublicKeyCredentialDescriptor> excludeCredentials, AuthenticationExtensionsClientInputs extensions, Duration timeout) implements Serializable {

    public PublicKeyCredentialCreationOptions toPublicKeyCredentialCreationOptions() {
        //@formatter:off
        return PublicKeyCredentialCreationOptions.builder()
            .challenge(challenge())
            .user(user())
            .attestation(
                AttestationConveyancePreference.valueOf(attestation().value())
            )
            .rp(PublicKeyCredentialRpEntity.builder()
                .name(rp().name())
                .id(rp().id())
                .build()
            )
             .pubKeyCredParams(ArtemisPublicKeyCredentialParametersDTO.convertToPublicKeyCredentialParameters(pubKeyCredParams()))
             .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                 .authenticatorAttachment(authenticatorSelection().authenticatorAttachment())
                 .residentKey(ResidentKeyRequirement.valueOf(authenticatorSelection().residentKey()))
                 .userVerification(authenticatorSelection().userVerification())
                 .build())
            .excludeCredentials(excludeCredentials())
            .extensions(extensions())
            .timeout(timeout())
            .build();
        //@formatter:on
    }
}

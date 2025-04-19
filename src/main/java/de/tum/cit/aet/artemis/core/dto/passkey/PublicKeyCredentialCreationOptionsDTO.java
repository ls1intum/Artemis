package de.tum.cit.aet.artemis.core.dto.passkey;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

import org.springframework.security.web.webauthn.api.AuthenticationExtensionsClientInputs;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;

/**
 * {@link PublicKeyCredentialCreationOptions} is not serializable, which is a problem for synchronizing via hazelcast across multiple nodes.
 * We synchronize {@link PublicKeyCredentialCreationOptionsDTO} instead and serialize/deserialize it to/from {@link PublicKeyCredentialCreationOptions}.
 */
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

    public static PublicKeyCredentialCreationOptionsDTO publicKeyCredentialCreationOptionsToDTO(PublicKeyCredentialCreationOptions options) {
        //@formatter:off
        return new PublicKeyCredentialCreationOptionsDTO(
            options.getChallenge(),
            options.getUser(),
            new ArtemisAttestationConveyancePreferenceDTO(options.getAttestation().getValue()),
            new ArtemisPublicKeyCredentialRpEntityDTO(options.getRp().getName(), options.getRp().getId()),
            options.getPubKeyCredParams().stream()
                .map(param -> new ArtemisPublicKeyCredentialParametersDTO(param.getType(), param.getAlg().getValue()))
                .toList(),
            new ArtemisAuthenticatorSelectionCriteriaDTO(
                options.getAuthenticatorSelection().getAuthenticatorAttachment(),
                options.getAuthenticatorSelection().getResidentKey().toString(),
                options.getAuthenticatorSelection().getUserVerification()),
            options.getExcludeCredentials(),
            options.getExtensions(),
            options.getTimeout()
        );
        //@formatter:on
    }
}

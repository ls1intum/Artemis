package de.tum.cit.aet.artemis.core.dto.passkey;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.repository.webauthn.COSEAlgorithm;

/**
 * We cannot directly use the SpringSecurity object as it is not serializable.
 *
 * @see org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ArtemisPublicKeyCredentialParametersDTO(PublicKeyCredentialType type, long coseAlgorithmIdentifier) implements Serializable {

    public PublicKeyCredentialParameters toPublicKeyCredentialParameters() {
        if (!Objects.equals(type.getValue(), PublicKeyCredentialType.PUBLIC_KEY.getValue())) {
            throw new IllegalArgumentException("Only PUBLIC_KEY credential type is supported, got " + type);
        }
        return COSEAlgorithm.fromValue((int) coseAlgorithmIdentifier).getCredentialParameters();
    }

    public static List<PublicKeyCredentialParameters> convertToPublicKeyCredentialParameters(List<ArtemisPublicKeyCredentialParametersDTO> artemisParams) {
        return Arrays.asList(artemisParams.stream().map(ArtemisPublicKeyCredentialParametersDTO::toPublicKeyCredentialParameters).toArray(PublicKeyCredentialParameters[]::new));
    }
}

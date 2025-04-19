package de.tum.cit.aet.artemis.core.dto.passkey;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.security.web.webauthn.api.COSEAlgorithmIdentifier;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * We cannot directly use the SpringSecurity object as it is not serializable.
 *
 * @see org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ArtemisPublicKeyCredentialParametersDTO(PublicKeyCredentialType type, long coseAlgorithmIdentifier) implements Serializable {

    private enum COSEAlgorithm {

        EDDSA((int) COSEAlgorithmIdentifier.EdDSA.getValue(), PublicKeyCredentialParameters.EdDSA), // strongest
        ES512((int) COSEAlgorithmIdentifier.ES512.getValue(), PublicKeyCredentialParameters.ES512),
        ES384((int) COSEAlgorithmIdentifier.ES384.getValue(), PublicKeyCredentialParameters.ES384),
        ES256((int) COSEAlgorithmIdentifier.ES256.getValue(), PublicKeyCredentialParameters.ES256),
        RS512((int) COSEAlgorithmIdentifier.RS512.getValue(), PublicKeyCredentialParameters.RS512),
        RS384((int) COSEAlgorithmIdentifier.RS384.getValue(), PublicKeyCredentialParameters.RS384),
        RS256((int) COSEAlgorithmIdentifier.RS256.getValue(), PublicKeyCredentialParameters.RS256); // weakest

        private final int value;

        private final PublicKeyCredentialParameters credentialParameters;

        COSEAlgorithm(int value, PublicKeyCredentialParameters credentialParameters) {
            this.value = value;
            this.credentialParameters = credentialParameters;
        }

        public PublicKeyCredentialParameters getCredentialParameters() {
            return credentialParameters;
        }

        public static COSEAlgorithm fromValue(int value) {
            for (COSEAlgorithm algorithm : values()) {
                if (algorithm.value == value) {
                    return algorithm;
                }
            }
            throw new IllegalArgumentException("Unsupported COSE Algorithm Identifier: " + value);
        }
    }

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

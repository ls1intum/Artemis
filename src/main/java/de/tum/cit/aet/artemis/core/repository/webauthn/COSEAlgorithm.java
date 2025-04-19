package de.tum.cit.aet.artemis.core.repository.webauthn;

import org.springframework.security.web.webauthn.api.COSEAlgorithmIdentifier;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;

/**
 * Enum representing supported COSE (CBOR Object Signing and Encryption) algorithm identifiers
 * for WebAuthn credential creation.
 *
 * <p>
 * This enum maps integer values of {@link COSEAlgorithmIdentifier} to their corresponding
 * {@link PublicKeyCredentialParameters} representations. It is used to configure the public key
 * cryptographic algorithms supported during WebAuthn registration and authentication.
 * </p>
 *
 * <p>
 * Algorithms are ordered from strongest to weakest (based on cryptographic strength).
 * </p>
 */
public enum COSEAlgorithm {

    /** Edwards-curve Digital Signature Algorithm (EdDSA), e.g., Ed25519 — strongest */
    EDDSA((int) COSEAlgorithmIdentifier.EdDSA.getValue(), PublicKeyCredentialParameters.EdDSA),

    /** ECDSA with SHA-512 (NIST P-521) */
    ES512((int) COSEAlgorithmIdentifier.ES512.getValue(), PublicKeyCredentialParameters.ES512),

    /** ECDSA with SHA-384 (NIST P-384) */
    ES384((int) COSEAlgorithmIdentifier.ES384.getValue(), PublicKeyCredentialParameters.ES384),

    /** ECDSA with SHA-256 (NIST P-256) — commonly used default */
    ES256((int) COSEAlgorithmIdentifier.ES256.getValue(), PublicKeyCredentialParameters.ES256),

    /** RSASSA-PKCS1-v1_5 with SHA-512 */
    RS512((int) COSEAlgorithmIdentifier.RS512.getValue(), PublicKeyCredentialParameters.RS512),

    /** RSASSA-PKCS1-v1_5 with SHA-384 */
    RS384((int) COSEAlgorithmIdentifier.RS384.getValue(), PublicKeyCredentialParameters.RS384),

    /** RSASSA-PKCS1-v1_5 with SHA-256 — weakest supported */
    RS256((int) COSEAlgorithmIdentifier.RS256.getValue(), PublicKeyCredentialParameters.RS256);

    private final int value;

    private final PublicKeyCredentialParameters credentialParameters;

    /**
     * Constructs a new {@code COSEAlgorithm} entry with the corresponding integer identifier and
     * credential parameters.
     *
     * @param value                the COSE algorithm identifier value
     * @param credentialParameters the associated {@link PublicKeyCredentialParameters}
     */
    COSEAlgorithm(int value, PublicKeyCredentialParameters credentialParameters) {
        this.value = value;
        this.credentialParameters = credentialParameters;
    }

    /**
     * Returns the associated {@link PublicKeyCredentialParameters} for use in WebAuthn registration.
     *
     * @return the credential parameters representing this algorithm
     */
    public PublicKeyCredentialParameters getCredentialParameters() {
        return credentialParameters;
    }

    /**
     * Resolves the {@code COSEAlgorithm} enum from the given integer value.
     *
     * @param value the COSE algorithm identifier (e.g., -8 for EdDSA, -7 for ES256)
     * @return the matching {@code COSEAlgorithm}
     * @throws IllegalArgumentException if no matching algorithm is found
     */
    public static COSEAlgorithm fromValue(int value) {
        for (COSEAlgorithm algorithm : values()) {
            if (algorithm.value == value) {
                return algorithm;
            }
        }
        throw new IllegalArgumentException("Unsupported COSE Algorithm Identifier: " + value);
    }
}

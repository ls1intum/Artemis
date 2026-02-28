package de.tum.cit.aet.artemis.core.authentication;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Set;

import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;

/**
 * Factory for creating {@link CredentialRecord} objects for testing purposes.
 */
public class CredentialRecordFactory {

    private static final int DEFAULT_CREDENTIAL_PUBLIC_KEY_BYTE_COUNT = 77;

    private static final int DEFAULT_CREDENTIAL_ID_BYTE_COUNT = 32;

    private static final int DEFAULT_ATTESTATION_OBJECT_BYTE_COUNT = 32;

    private CredentialRecordFactory() {
        // Prevent instantiation of this utility class
    }

    /**
     * Creates a CredentialRecord for testing purposes with a random credential ID.
     *
     * @param user  the user to associate with the credential
     * @param label the label for the credential
     * @return a CredentialRecord instance
     */
    public static CredentialRecord createCredentialRecord(User user, String label) {
        return createCredentialRecord(user, label, generateRandomCredentialId());
    }

    /**
     * Creates a CredentialRecord with a specific credential ID for testing purposes.
     *
     * @param user         the user to associate with the credential
     * @param label        the label for the credential
     * @param credentialId the credential ID to use
     * @return a CredentialRecord instance
     */
    public static CredentialRecord createCredentialRecord(User user, String label, Bytes credentialId) {
        byte[] publicKeyBytes = new byte[DEFAULT_CREDENTIAL_PUBLIC_KEY_BYTE_COUNT];
        new SecureRandom().nextBytes(publicKeyBytes);

        byte[] attestationObjectBytes = new byte[DEFAULT_ATTESTATION_OBJECT_BYTE_COUNT];
        new SecureRandom().nextBytes(attestationObjectBytes);

        return ImmutableCredentialRecord.builder().credentialId(credentialId).userEntityUserId(BytesConverter.longToBytes(user.getId()))
                .publicKey(new ImmutablePublicKeyCose(publicKeyBytes)).signatureCount(0).uvInitialized(true)
                .transports(Set.of(AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID)).backupEligible(true).backupState(true)
                .attestationObject(new Bytes(attestationObjectBytes)).created(Instant.now()).lastUsed(Instant.now()).label(label).credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .build();
    }

    /**
     * Generates a random credential ID for testing.
     *
     * @return a Bytes object containing a random credential ID
     */
    public static Bytes generateRandomCredentialId() {
        byte[] credentialIdBytes = new byte[DEFAULT_CREDENTIAL_ID_BYTE_COUNT];
        new SecureRandom().nextBytes(credentialIdBytes);
        return new Bytes(credentialIdBytes);
    }
}
